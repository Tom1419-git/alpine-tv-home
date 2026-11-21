#!/usr/bin/env python3
"""Telecharge l'ORIGINAL Wikimedia Commons de la photo unique de l'accueil alpin (Riffelsee, Cervin au lever du soleil) et note leur provenance exacte.

Usage (depuis la racine du depot, avec Pillow) :
    tools/.venv/bin/python tools/fetch_weather_masters.py            # telecharge ce qui manque
    tools/.venv/bin/python tools/fetch_weather_masters.py --check    # relit sources.json et verifie les fichiers

Le master est ecrit, a sa resolution native (6000x4000), dans themes/weather/masters-native/ (ignore par git : 20 Mo). themes/weather/sources.json, lui, est versionne : titre, auteur, licence, liens, resolution, poids et SHA-256.
Jamais de miniature : l'URL est celle du fichier original (`url` d'imageinfo), pas d'une vignette redimensionnee.
Une image plus petite que 1920 px de large est marquee `capped` (qualite plafonnee) et jamais agrandie.
"""
import argparse
import hashlib
import html
import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEST = ROOT / "themes/weather/masters-native"
SOURCES = ROOT / "themes/weather/sources.json"
API = "https://commons.wikimedia.org/w/api.php"
UA = "alpine-home-builder/1.0 (local build script)"
MIN_WIDTH = 1920

# id -> (titre exact sur Commons, licence attendue). Une licence differente de celle attendue arrete tout.
PHOTOS = {
    "riffelsee-lever": ("Matterhorn reflection in the Riffelsee at sunrise.jpg", "CC BY-SA 4.0"),
    "braies-matin": ("Lago di Braies - Morning.jpg", "CC BY-SA 2.0"),
}


def get(url, binary=False):
    """GET poli : attend et reessaie quand Wikimedia repond 429 ou 503 (limite de debit)."""
    delay = 20
    for attempt in range(6):
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=180) as r:
                data = r.read()
            time.sleep(3)   # une pause entre deux requetes, pour rester sous la limite
            return data if binary else data.decode("utf-8")
        except urllib.error.HTTPError as e:
            if e.code not in (429, 503) or attempt == 5:
                raise
            wait = int(e.headers.get("Retry-After", delay) or delay)
            print(f"limite de debit ({e.code}), attente de {wait} s...", flush=True)
            time.sleep(wait)
            delay *= 2


def download(url, path):
    """Telecharge l'original avec curl (urllib est limite en debit par Wikimedia plus vite que curl), avec reprise."""
    tmp = path.with_suffix(".part")
    for attempt in range(5):
        r = subprocess.run(["curl", "-sfL", "--retry", "3", "-A", UA, "-o", str(tmp), url])
        if r.returncode == 0:
            tmp.replace(path)
            time.sleep(3)
            return
        print(f"echec curl ({r.returncode}), nouvelle tentative dans {20 * (attempt + 1)} s...", flush=True)
        time.sleep(20 * (attempt + 1))
    raise SystemExit(f"telechargement impossible : {url}")


def info(title):
    q = urllib.parse.urlencode({"action": "query", "titles": "File:" + title, "prop": "imageinfo", "format": "json",
                                "iiprop": "url|size|sha1|extmetadata"})
    page = next(iter(json.loads(get(f"{API}?{q}"))["query"]["pages"].values()))
    if "imageinfo" not in page:
        raise SystemExit(f"introuvable sur Commons : {title}")
    return page["imageinfo"][0]


def plain(value):
    return html.unescape(re.sub(r"<[^>]+>", "", value or "")).strip()


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch(only=None):
    from PIL import Image
    Image.MAX_IMAGE_PIXELS = None
    DEST.mkdir(parents=True, exist_ok=True)
    records = {}
    for pid, (title, licence) in PHOTOS.items():
        if only and pid not in only:
            continue
        meta = info(title)
        ext = meta["extmetadata"]
        got = plain(ext.get("LicenseShortName", {}).get("value"))
        if got != licence:
            raise SystemExit(f"{pid} : licence {got!r} au lieu de {licence!r}, image ecartee")
        # L'URL d'imageinfo est celle de l'original ; on refuse tout chemin de miniature (/thumb/).
        url = meta["url"].split("?", 1)[0]   # sans les parametres utm de suivi : meme fichier original
        if "/thumb/" in url:
            raise SystemExit(f"{pid} : l'URL est une miniature, refus : {url}")
        path = DEST / f"{pid}.jpg"
        if not path.is_file() or path.stat().st_size != meta["size"]:
            print(f"telechargement de {pid} ({meta['size'] / 1e6:.1f} Mo)...", flush=True)
            download(url, path)
        with Image.open(path) as im:
            real = im.size
        if real != (meta["width"], meta["height"]):
            raise SystemExit(f"{pid} : {real} lu dans le fichier, {meta['width']}x{meta['height']} annonce par Commons")
        digest = sha256(path)
        if digest != meta["sha1"] and hashlib.sha1(path.read_bytes()).hexdigest() != meta["sha1"]:
            raise SystemExit(f"{pid} : empreinte SHA-1 differente de celle de Commons, fichier corrompu")
        records[pid] = {
            "title": "File:" + title,
            "author": plain(ext.get("Artist", {}).get("value")),
            "license": got,
            "license_url": plain(ext.get("LicenseUrl", {}).get("value")),
            "commons_url": meta["descriptionurl"],
            "original_url": url,
            "width": real[0],
            "height": real[1],
            "bytes": path.stat().st_size,
            "sha256": digest,
            "capped": real[0] < MIN_WIDTH,
            "native_16x9": abs(real[0] / real[1] - 16 / 9) < 0.01,
        }
        print(f"{pid}: {real[0]}x{real[1]}, {path.stat().st_size / 1e6:.1f} Mo, {got}"
              + (" (QUALITE PLAFONNEE)" if records[pid]["capped"] else ""))
    previous = json.loads(SOURCES.read_text()) if SOURCES.is_file() else {}
    previous.update(records)
    SOURCES.write_text(json.dumps(previous, indent=2, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")


def check():
    from PIL import Image
    Image.MAX_IMAGE_PIXELS = None
    data = json.loads(SOURCES.read_text())
    bad = 0
    for pid, rec in data.items():
        path = DEST / f"{pid}.jpg"
        if not path.is_file():
            print(f"{pid}: master absent")
            bad += 1
            continue
        with Image.open(path) as im:
            ok = im.size == (rec["width"], rec["height"])
        ok = ok and sha256(path) == rec["sha256"]
        print(f"{pid}: {'ok' if ok else 'DIFFERENT'}")
        bad += not ok
    return 1 if bad else 0


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true")
    ap.add_argument("only", nargs="*", help="identifiants a telecharger (tous par defaut)")
    a = ap.parse_args()
    sys.exit(check() if a.check else (fetch(a.only), 0)[1])
