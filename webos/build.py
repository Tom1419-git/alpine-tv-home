#!/usr/bin/env python3
"""Assemble webos/app/ (l'appli LG webOS) a partir du moteur de l'apercu et du pack photo.

A lancer apres `tools/.venv/bin/python tools/build_alpine_preview.py` (qui produit build/accueil-alpin-preview/).
Les images sont integrees en data: URI (images.js) : sur webOS l'appli est servie en file://, et un canvas dont l'image vient
d'un fichier local peut etre interdit de lecture de pixels ; une data: URI ne l'est jamais.
Le dossier webos/app/ produit est versionne : qui installe n'a rien a construire.
"""
import base64
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = Path(__file__).resolve().parent / "app"
VIEWER = ROOT / "tools/alpine_preview"
PREVIEW = ROOT / "build/accueil-alpin-preview"
# Les photos de l'appli : identifiant et dossier d'apercu (tools/build_alpine_preview.py --photo <id> --out <dossier>).
PHOTOS = [("riffelsee-lever", PREVIEW), ("braies-matin", ROOT / "build/accueil-alpin-preview-braies")]


def data_uri(path, mime):
    return "data:%s;base64,%s" % (mime, base64.b64encode(path.read_bytes()).decode())


def main():
    for pid, folder in PHOTOS:
        if not (folder / "pack/photo_1080.jpg").is_file():
            sys.exit("Lance d'abord : tools/.venv/bin/python tools/build_alpine_preview.py --photo %s --out %s" % (pid, folder))
    APP.mkdir(parents=True, exist_ok=True)
    for name in ("layout.js", "grade.js", "scene.js"):
        shutil.copyfile(VIEWER / name, APP / name)
    # data.js : seulement la partie pack de chaque photo (grille de couleur, courbe de crete), sans les cartes de la TV.
    packs = {}
    for pid, folder in PHOTOS:
        src = (folder / "data.js").read_text(encoding="utf-8")
        packs[pid] = json.loads(src[src.index("=") + 1:].rstrip(";\n "))["pack"]
    (APP / "data.js").write_text("window.ALPINE_DATA = " + json.dumps({"packs": packs}, separators=(",", ":")) + ";\n",
                                 encoding="utf-8")
    images = {pid: {"photo": data_uri(folder / "pack/photo_1080.jpg", "image/jpeg"),
                    "fg": data_uri(folder / "pack/foreground_1080.png", "image/png")} for pid, folder in PHOTOS}
    (APP / "images.js").write_text("window.ALPINE_IMAGES = " + json.dumps(images) + ";\n", encoding="utf-8")
    (APP / "fonts").mkdir(exist_ok=True)
    shutil.copyfile(PREVIEW / "fonts/outfit.ttf", APP / "fonts/outfit.ttf")
    from PIL import Image
    photo = Image.open(PREVIEW / "pack/photo_1080.jpg").convert("RGB")
    box = (560, 140, 1280, 860)   # le Cervin et son reflet, carre
    for size, name in ((80, "icon.png"), (130, "largeIcon.png")):
        photo.crop(box).resize((size, size), Image.LANCZOS).save(APP / name)
    print("webos/app prete :", sum(p.stat().st_size for p in APP.rglob("*") if p.is_file()) // 1024, "Ko")


if __name__ == "__main__":
    main()
