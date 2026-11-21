#!/usr/bin/env python3
"""Genere l'interface web locale de comparaison de l'accueil alpin (photo unique : Riffelsee, Cervin au lever du soleil).

Usage (depuis la racine du depot) :
    tools/.venv/bin/python tools/build_alpine_preview.py            # fabrique build/accueil-alpin-preview/
    tools/.venv/bin/python tools/build_alpine_preview.py --serve    # puis sert http://127.0.0.1:8765/

Le pack (build/weather/, produit par tools/prepare_weather_scene.py) est copie tel quel : l'interface lit les memes
fichiers que l'application (photo.jpg, photo_1080.jpg, foreground.png, foreground_1080.png) et applique en direct
l'etalonnage continu (tools/alpine_preview/grade.js, port de SceneGrade.java). L'interface doit etre servie en http :
un navigateur interdit de relire les pixels d'un fichier ouvert directement.
Avec --docs, ecrit docs/accueil-alpin/fichiers-livres.md a partir des fichiers reellement produits.
"""
import argparse
import json
import shutil
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
from prepare_weather_scene import PHOTOS  # noqa: E402  (recadrage et titre de chaque photo)
VIEWER = Path(__file__).resolve().parent / "alpine_preview"
FONT = ROOT / "launcher/app/src/main/res/font/outfit.ttf"
SOURCES = ROOT / "themes/weather/sources.json"
PACK = ROOT / "build/weather"   # --pack-dir pour un autre dossier
SHOT = ROOT / "shots/alpin-home.png"
PHOTO_ID = "riffelsee-lever"
CROP_NATIVE = (3900, 2194)

# Cartes de l'application relevees sur une capture de la TV (dock en 1080p) : x, y, largeur, hauteur.
CARD_BOXES = [(142, 712, 393, 220), (576, 723, 354, 199), (990, 723, 354, 199), (1404, 723, 354, 199)]


def crop_cards(out):
    """Les quatre cartes d'application de la capture de la TV, si elle existe ; sinon l'interface dessine des cartes."""
    dest = out / "assets/cards"
    dest.mkdir(parents=True, exist_ok=True)
    if not SHOT.is_file():
        return 0
    from PIL import Image
    with Image.open(SHOT) as im:
        im = im.convert("RGB")
        for i, (x, y, w, h) in enumerate(CARD_BOXES):
            im.crop((x, y, x + w, y + h)).resize((354, 199), Image.LANCZOS).save(dest / f"card{i}.png")
    return len(CARD_BOXES)


def mb(b):
    return f"{b / 1e6:.1f}"   # Mo decimaux, comme Wikimedia Commons


def write_docs(source, pack, apk):
    """docs/accueil-alpin/fichiers-livres.md : resolution, poids et provenance de chaque fichier, tires des mesures."""
    bpp = source["bytes"] / (source["width"] * source["height"])
    lines = ["# Fichiers livres : resolution, poids, provenance", "",
             "Genere par `tools/.venv/bin/python tools/build_alpine_preview.py --docs` a partir des fichiers reellement produits ; "
             "rien n'est ecrit a la main. Licence et modifications : `themes/weather/CREDITS.md`.", "",
             "## Photo unique : master natif (themes/weather/masters-native/, ignore par git, jamais modifie)", "",
             "| Photo | Resolution native | Poids | Octets par pixel | Licence | Original (SHA-256) |", "|---|---|---|---|---|---|",
             f"| {source['title'][5:]} | {source['width']}x{source['height']} | {mb(source['bytes'])} Mo | {bpp:.2f} "
             f"| {source['license']} | `{source['sha256'][:16]}...` |", "",
             "## Pack livre a l'application (themes/weather/weather_pack.zip, hors APK)", "",
             f"Manifeste version {pack['version']} ; total des fichiers {mb(pack['total'])} Mo, zip non compresse (JPEG et PNG le sont deja). "
             f"Recadrage natif {CROP_NATIVE[0]}x{CROP_NATIVE[1]}, jamais agrandi. Photos : JPEG qualite 95, chrominance 4:4:4. Premiers "
             "plans : PNG sans perte. Les fichiers `_1080` sont le jeu de secours lu par une surface 1080p ; l'UHD est lu par une surface 4K.", "",
             "| Fichier | Pixels | Poids (Mo) | SHA-256 |", "|---|---|---|---|"]
    for k in sorted(pack["files"]):
        f = pack["files"][k]
        lines.append(f"| `{k}` | {f['pixels'][0]}x{f['pixels'][1]} | {mb(f['size'])} | `{f['sha256'][:16]}...` |")
    zip_path = ROOT / "themes/weather/weather_pack.zip"
    lines += ["", f"Archive `themes/weather/weather_pack.zip` : {zip_path.stat().st_size} octets ({mb(zip_path.stat().st_size)} Mo)."]
    if apk:
        lines.append(f"APK de release : {apk} octets (limite du CI : 737 280).")
    dest = ROOT / "docs/accueil-alpin/fichiers-livres.md"
    dest.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return dest


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default=str(ROOT / "build/accueil-alpin-preview"))
    ap.add_argument("--pack-dir", default=str(PACK), help="dossier du pack (manifest.json et les quatre fichiers)")
    ap.add_argument("--photo", default=PHOTO_ID, help="identifiant de la photo du pack (riffelsee-lever, braies-matin)")
    ap.add_argument("--docs", action="store_true", help="ecrit docs/accueil-alpin/fichiers-livres.md")
    ap.add_argument("--apk", type=int, help="poids de l'APK (octets), pour --docs")
    ap.add_argument("--serve", action="store_true", help="sert le dossier sur http://127.0.0.1:8765/")
    args = ap.parse_args(argv)
    out = Path(args.out)
    photo_id = args.photo
    pack_dir = Path(args.pack_dir)
    manifest = pack_dir / "manifest.json"
    if not manifest.is_file():
        raise SystemExit("pack absent : lancez tools/prepare_weather_scene.py (ou dezippez weather_pack.zip dans build/weather/)")
    m = json.loads(manifest.read_text(encoding="utf-8"))
    if m.get("version") != 3 or sorted(m["files"]) != sorted(["foreground.png", "foreground_1080.png", "photo.jpg", "photo_1080.jpg"]):
        raise SystemExit("manifeste inattendu : il faut un pack version 3, une seule photo")
    started = time.time()
    shutil.rmtree(out, ignore_errors=True)
    (out / "pack").mkdir(parents=True)
    for name in m["files"]:
        if not (pack_dir / name).is_file():
            raise SystemExit(f"{pack_dir / name} manque : pack incomplet")
        shutil.copy(pack_dir / name, out / "pack" / name)
    source = json.loads(SOURCES.read_text(encoding="utf-8"))[photo_id]
    box = PHOTOS[photo_id]["photo"]["box"]
    title = PHOTOS[photo_id]["photo"]["title"]
    pack = {"files": m["files"], "total": sum(f["size"] for f in m["files"].values()), "version": m["version"],
            "grid": m["grid"], "crest": m["crest"], "waterline": m["waterline"]}
    data = {"generated": time.strftime("%Y-%m-%d %H:%M"), "pack": pack,
            "photo": {"id": photo_id, "title": title, "source": source, "crop_native": [box[2] - box[0], box[3] - box[1]]}}
    for f in VIEWER.iterdir():
        if f.is_file():
            shutil.copy(f, out / f.name)
    (out / "fonts").mkdir()
    shutil.copy(FONT, out / "fonts/outfit.ttf")
    (out / "data.js").write_text("window.ALPINE_DATA = " + json.dumps(data, ensure_ascii=False) + ";\n", encoding="utf-8")
    cards = crop_cards(out)
    if args.docs:
        print("docs :", write_docs(source, pack, args.apk))
    size = sum(p.stat().st_size for p in out.rglob("*") if p.is_file())
    print(f"interface prete : {out / 'index.html'}  ({size / 1e6:.0f} Mo, {cards} cartes de la TV, {time.time() - started:.0f} s)")
    if args.serve:
        import functools
        import http.server
        handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=str(out))
        print("http://127.0.0.1:8765/  (Ctrl-C pour arreter)")
        http.server.ThreadingHTTPServer(("127.0.0.1", 8765), handler).serve_forever()
    return 0


if __name__ == "__main__":
    sys.exit(main())
