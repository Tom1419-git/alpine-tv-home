#!/usr/bin/env python3
"""Prepare the "fenetre alpine" photo pack : UNE seule photo, le Riffelsee au lever du soleil.

Usage (from the repo root, with Pillow and numpy; see tools/.venv):
    tools/.venv/bin/python tools/fetch_weather_masters.py        # le master natif 6000x4000, une fois
    tools/.venv/bin/python tools/prepare_weather_scene.py \
        --master themes/weather/masters-native/riffelsee-lever.jpg \
        --out build/weather --pack themes/weather/weather_pack.zip

Photo : Deralpinbergsteiger, CC BY-SA 4.0 (themes/weather/CREDITS.md). Une seule image sert toutes les phases du jour :
la lumiere (aube, lever, matin, midi, apres-midi, coucher, crepuscule, nuit), la meteo, le soleil et les nuages sont
produits a l'execution par l'application, par un etalonnage continu (SceneGrade), pas par des images differentes.

Le script :
  1. recadre le master natif en 16:9 autour du Cervin (jamais depuis un fichier deja reduit), pose la ligne d'eau a
     WATERLINE de la hauteur, reduit une seule fois vers 3840x2160 puis 1920x1080 et n'agrandit jamais,
  2. detoure la rive et les montagnes (tout ce qui est au-dessus de la ligne d'eau) par propagation depuis le ciel,
     recale la crete sur l'arete reelle et fond le bas des chiffres de l'horloge (profondeur de la mise en page B),
  3. verifie le masque et ECHOUE BRUYAMMENT si la crete est sale ou si le masque contient le reflet,
  4. ecrit photo.jpg et photo_1080.jpg (JPEG 95, chrominance 4:4:4), foreground.png et foreground_1080.png (PNG sans
     perte), manifest.json (version 3 : empreintes, courbe de crete, grille de couleur de la photo) et weather_pack.zip.

Le reflet du Cervin reste dans la photo de fond : c'est de l'eau, jamais un objet de premier plan.
Un humain peut remplacer un foreground.png a la main : le pack ne depend que de sa transparence (--repack).
"""
import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path

WIDTH, HEIGHT = 1920, 1080
UHD = (3840, 2160)
HD = (1920, 1080)
WATERLINE = 0.56            # ligne d'eau, en fraction de la hauteur : limite basse du premier plan (+ marge de rive)
SHORE_MARGIN = 28           # le premier plan descend sous la ligne d'eau
# Geometrie de l'horloge de la mise en page B (la meme que AlpineHomeView.java ; un test verifie l'accord) : largeur en
# fraction de l'ecran et ligne de base, remontee de 52 px par rapport a la mise en page precedente (0,572). Police
# Outfit poids 200 : "00:00" fait 2,902 em, les chiffres 0,692 em de haut.
CLOCK_WIDTH = 0.58
CLOCK_BASELINE = 0.524
ADVANCE_EM = 2.902
DIGIT_EM = 0.692
# Lisibilite : le terrain ne cache que le bas des chiffres, au plus OCCLUSION de leur hauteur, avec un fondu doux.
OCCLUSION = 0.22
SOFT = 0.10
JPEG_QUALITY_UHD = 95
MANIFEST_VERSION = 3
PACK_FILES = ("photo.jpg", "photo_1080.jpg", "foreground.png", "foreground_1080.png")
CREST_POINTS = 241          # echantillons de la courbe de crete (un tous les 16 px en UHD) : l'application s'en sert
                            # pour que le soleil, les nuages et les etoiles passent derriere les montagnes
GRID = (32, 18)             # grille de couleur de la photo (modele de contraste de l'application)
MASTERS = Path(__file__).resolve().parent.parent / "themes/weather/masters-native"

PHOTO = {
    "id": "riffelsee-lever", "title": "Riffelsee, Cervin au lever du soleil",
    # Boite de recadrage 16:9 dans le master 6000x4000 : centre le Cervin (a 33 % de la largeur) et pose la ligne
    # d'eau a 56 % de la hauteur. La largeur (3900 px) est au moins celle de l'UHD : aucun agrandissement.
    "box": (0, 831, 3900, 3025),
    # Neige du glacier qui se confond avec le ciel pale : crete tracee a la main sur un agrandissement x3
    # (coordonnees 1920x1080 du cadrage).
    "overrides": [[(1181, 463), (1207, 478), (1233, 483), (1253, 487), (1263, 488)]],
}

CREDITS = {
    "author": "Deralpinbergsteiger",
    "license": "CC BY-SA 4.0",
    "license_url": "https://creativecommons.org/licenses/by-sa/4.0/",
    "source": "https://commons.wikimedia.org/wiki/File:Matterhorn_reflection_in_the_Riffelsee_at_sunrise.jpg",
}


class SceneError(Exception):
    """Une image ou un masque est refuse : rien n'est ecrit."""


def _np():
    try:
        import numpy
        from PIL import Image
    except ImportError as error:  # pragma: no cover - message pour l'utilisateur
        raise SystemExit("Pillow et numpy sont necessaires : python3 -m venv tools/.venv && "
                         "tools/.venv/bin/pip install Pillow numpy") from error
    return numpy, Image


def sky_mask(rgb, water_row, eps=3.2, blur=2, seed_tolerance=70):
    """Booleen (h, w) : vrai pour le ciel.

    Le ciel est la region connectee au bord haut par des pixels voisins presque identiques (un degrade est lisse,
    une crete ne l'est pas). Calcul sur l'image reduite de moitie, puis remis a l'echelle.
    """
    np, Image = _np()
    h, w = rgb.shape[:2]
    small = np.asarray(Image.fromarray(rgb).resize((w // 2, h // 2), Image.BILINEAR), dtype=np.float32)
    if blur:
        small = _box_blur(small, blur)
    sh, sw = small.shape[:2]
    limit = max(2, water_row // 2 + SHORE_MARGIN // 2 + 2)
    link_h = np.abs(np.diff(small, axis=1)).max(axis=2) < eps          # (sh, sw-1) : pixel et voisin de droite
    link_v = np.abs(np.diff(small, axis=0)).max(axis=2) < eps          # (sh-1, sw) : pixel et voisin du dessous
    reach = np.zeros((sh, sw), dtype=bool)
    # Graines : seulement les pixels du bord haut qui ont la couleur du ciel (mediane du milieu du bord) ;
    # un coin de roche au bord haut ne doit pas devenir une source de ciel.
    top = small[0]
    ref = np.median(top[sw // 4: 3 * sw // 4], axis=0)
    reach[0, :] = np.abs(top - ref).max(axis=1) < seed_tolerance
    while True:
        before = reach.sum()
        # propagation dans les quatre sens, bornee par les liens lisses
        reach[:, 1:] |= reach[:, :-1] & link_h
        reach[:, :-1] |= reach[:, 1:] & link_h
        reach[1:, :] |= reach[:-1, :] & link_v
        reach[:-1, :] |= reach[1:, :] & link_v
        reach[limit:, :] = False
        if reach.sum() == before:
            break
    full = np.asarray(Image.fromarray((reach * 255).astype(np.uint8)).resize((w, h), Image.BILINEAR)) > 127
    return full


def _box_blur(arr, radius):
    np, _ = _np()
    k = 2 * radius + 1
    pad = np.pad(arr, ((radius, radius), (radius, radius), (0, 0)), mode="edge")
    csum = pad.cumsum(axis=0).cumsum(axis=1)
    csum = np.pad(csum, ((1, 0), (1, 0), (0, 0)))
    s = csum[k:, k:] - csum[:-k, k:] - csum[k:, :-k] + csum[:-k, :-k]
    return s / (k * k)


def refine_crest(rgb, crest, search=18, threshold=70, fallback=9):
    """Recale la crete sur l'arete reelle : la propagation s'arrete dans le halo d'accentuation, au-dessus.

    Pour chaque colonne, on descend jusqu'au premier pixel nettement different du ciel juste au-dessus ; sans
    contraste net (neige sur ciel pale), on garde un decalage fixe.
    """
    np, _ = _np()
    h, w = rgb.shape[:2]
    out = crest.copy()
    img = rgb.astype(np.int32)
    for x in range(w):
        c0 = int(crest[x])
        if c0 < 8 or c0 + search >= h - 1:
            continue
        ref = img[c0 - 7:c0 - 2, x].mean(axis=0)
        dist = np.abs(img[c0:c0 + search, x] - ref).sum(axis=1)
        hits = np.nonzero(dist > threshold)[0]
        out[x] = c0 + (int(hits[0]) if len(hits) else fallback)
    return out


def median_filter_1d(values, size):
    """Mediane glissante sur `size` colonnes (bords repetes) : garde les falaises, enleve les pointes isolees."""
    np, _ = _np()
    half = size // 2
    padded = np.pad(values, half, mode="edge")
    stack = np.stack([padded[i:i + len(values)] for i in range(size)])
    return np.median(stack, axis=0).astype(values.dtype)


def raw_crest(sky, water_row):
    """Crete brute (une valeur par colonne) et ligne basse du premier plan, a la resolution de `sky`."""
    np, _ = _np()
    h, w = sky.shape
    limit = min(h, water_row + SHORE_MARGIN)
    # crete : premiere ligne a partir de laquelle la colonne est du terrain de facon durable (22 lignes sur 24) ;
    # une tache isolee dans le ciel (oiseau, grain) ne fait donc pas une colonne de rocher.
    window = 24
    ground = (~sky[:limit]).astype(np.int32)
    csum = np.vstack([np.zeros((1, w), dtype=np.int32), ground.cumsum(axis=0)])
    ahead = csum[window:] - csum[:-window]                       # (limit-window+1, w)
    persistent = ahead >= window - 2
    crest = np.where(persistent.any(axis=0), persistent.argmax(axis=0), limit)
    return np.minimum(crest, limit), limit


def open_crest(crest, half):
    """Ouverture morphologique de la crete : supprime les pointes de ciel (un nuage contre la montagne) plus etroites
    que 2 * half + 1 colonnes, sans toucher aux falaises ni aux pics plus larges."""
    np, _ = _np()
    if half <= 0:
        return crest
    n = len(crest)
    # Hors de l'image, on suppose du terrain (valeur tres petite pour l'erosion, tres grande pour la dilatation) :
    # une pointe collee au bord peut ainsi disparaitre comme les autres.
    padded = np.pad(crest, half, mode="constant", constant_values=-10 ** 9)
    eroded = np.stack([padded[i:i + n] for i in range(2 * half + 1)]).max(axis=0)
    padded = np.pad(eroded, half, mode="constant", constant_values=10 ** 9)
    return np.stack([padded[i:i + n] for i in range(2 * half + 1)]).min(axis=0).astype(crest.dtype)


def foreground_alpha(sky, water_row, overrides=(), rgb=None, scale=1, cap=True, occlusion=None, spike=0):
    """Alpha uint8 : tout ce qui est sous la crete et au-dessus de la ligne d'eau (+ marge de rive).

    `sky` et `water_row` sont a la resolution de calcul (1920x1080) ; `rgb` et le resultat sont a `scale` fois cette
    resolution (2 pour l'UHD) : la crete est calculee une fois puis recalee sur l'arete reelle de l'image pleine
    resolution, sans jamais rien agrandir de la photo elle-meme.
    `overrides` : retouches manuelles apres le seuillage, pour les quelques colonnes ou la neige se confond avec
    le ciel : soit `(x0, x1, y_crete)`, soit une polyligne `[(x, y), ...]` (coordonnees a la resolution de calcul).
    `spike` : demi-largeur (en colonnes a la resolution de calcul) de l'ouverture qui retire les pointes de ciel.
    `cap` : applique le fondu de lisibilite sous l'horloge, d'une profondeur `occlusion` (OCCLUSION par defaut) ; faux
    pour un masque de terrain complet dont la profondeur sera choisie a l'affichage.
    """
    np, Image = _np()
    s = scale
    crest0, limit0 = raw_crest(sky, water_row)
    w0 = sky.shape[1]
    w, h, limit = w0 * s, sky.shape[0] * s, limit0 * s
    if s == 1:
        crest = crest0.astype(np.int64)
    else:
        xs = (np.arange(w) + 0.5) / s - 0.5
        crest = np.round(np.interp(xs, np.arange(w0), crest0.astype(np.float64)) * s).astype(np.int64)
    if rgb is not None:
        crest = np.minimum(refine_crest(rgb, crest, search=18 * s, fallback=9 * s), limit)
    crest = median_filter_1d(crest, 8 * s + 1)   # supprime les pointes d'une ou deux colonnes sur les faces tres raides
    crest = open_crest(crest, spike * s)
    for item in overrides:
        if isinstance(item, list):
            xs = [pt[0] * s for pt in item]
            ys = [pt[1] * s for pt in item]
            cols = np.arange(xs[0], xs[-1] + 1)
            crest[cols] = np.round(np.interp(cols, xs, ys)).astype(crest.dtype)
        else:
            x0, x1, y = item
            crest[x0 * s:x1 * s] = y * s
    # Bord doux cote ciel seulement, decale vers le terrain (la crete a ete recalee sur l'arete) : jamais de pixel de
    # ciel colle a la crete.
    rows = np.arange(h, dtype=np.float32)[:, None]
    ramp = np.clip((rows - crest[None, :].astype(np.float32) - 1.0 * s) / (3.0 * s), 0.0, 1.0)
    ramp[limit:, :] = 0.0
    if cap:
        # Lisibilite de l'heure : sous l'horloge, le terrain n'est opaque que sur le bas des chiffres, avec un fondu.
        _, digit_h, baseline, left, right = clock_geometry(w, h)
        top = baseline - (OCCLUSION if occlusion is None else occlusion) * digit_h
        soft = SOFT * digit_h
        fade = np.clip((rows - (top - soft)) / soft, 0.0, 1.0)
        cols = slice(max(0, int(left) - 4 * s), min(w, int(right) + 5 * s))
        ramp[:, cols] *= fade
    return (ramp * 255).astype(np.uint8)


def check_mask(alpha, water_row, name="masque", scale=1):
    """Verifie le masque ; leve SceneError si la crete est sale ou si le reflet s'y trouve.

    `water_row` est a la resolution de `alpha` ; `scale` (1 en 1080p, 2 en UHD) met a l'echelle les marges en pixels.
    """
    np, _ = _np()
    h, w = alpha.shape
    margin = SHORE_MARGIN * scale
    opaque = alpha > 127
    # 1. le reflet reste dans la photo : rien d'opaque sous la ligne d'eau + marge de rive
    below = opaque[water_row + margin + 2 * scale:, :]
    if below.any():
        raise SceneError(f"{name} : le masque contient de l'eau ou le reflet "
                         f"({int(below.sum())} pixels sous la ligne d'eau)")
    if not opaque.any():
        raise SceneError(f"{name} : le masque est vide")
    # 2. crete propre : une seule composante, sans ilot de ciel (trous) ni ilots de terrain
    column_gaps = 0
    for x in range(w):
        idx = np.nonzero(opaque[:water_row + margin, x])[0]
        if len(idx):
            # entre la crete et le bas de la colonne, tout doit etre plein : un trou est du ciel pris pour du terrain
            column_gaps += int(idx[-1] - idx[0] + 1 - len(idx))
    if column_gaps > 0.0005 * w * h:
        raise SceneError(f"{name} : la crete est sale ({column_gaps} pixels de ciel sous la crete)")
    crest = np.where(opaque.any(axis=0), opaque.argmax(axis=0), water_row)
    jumps = np.abs(np.diff(crest.astype(int)))
    # un pic ou une falaise font de grands sauts, mais jamais une crete en dents de scie sur tout l'ecran
    noisy = int((jumps > 40 * scale).sum())
    if noisy > 12 * scale:
        raise SceneError(f"{name} : la crete est en dents de scie ({noisy} sauts de plus de {40 * scale} px)")
    return True


def check_edge_fringe(rgb, alpha, margin=0.12, scale=1, tolerance=0.25):
    """Pas de frange de ciel : le premier pixel opaque ressemble au terrain, pas au ciel juste au-dessus.

    Retourne la part de colonnes suspectes (0 a 1) ; leve SceneError au-dela de `tolerance`."""
    np, _ = _np()
    opaque = alpha > 200
    edge = opaque.copy()
    edge[1:, :] &= ~opaque[:-1, :]            # premiere ligne opaque de chaque colonne
    ys, xs = np.nonzero(edge)
    k10, k14 = 10 * scale, 14 * scale
    keep = (ys > k10 + 2) & (ys < rgb.shape[0] - k14)
    ys, xs = ys[keep], xs[keep]
    if len(ys) == 0:
        return 0.0
    rgb = rgb[..., :3]
    here = rgb[ys, xs]
    sky = rgb[ys - k10, xs]
    ground = rgb[ys + k10, xs]
    d_sky = np.abs(here - sky).sum(axis=1)
    d_ground = np.abs(here - ground).sum(axis=1)
    distinct = np.abs(sky - ground).sum(axis=1) > margin * 3 * 255
    fringe = int(((d_sky < d_ground) & distinct).sum())
    # Heuristique : une neige tres claire sur la crete ressemble au ciel (8 a 15 % des colonnes sur ces photos, relues
    # a l'oeil sur un agrandissement). Un masque decale ou non recale depasse 80 % : le seuil attrape seulement ca.
    if fringe > tolerance * len(ys):
        raise SceneError(f"franges de ciel sur la crete ({fringe} colonnes sur {len(ys)}, tolerance {tolerance:.0%})")
    return fringe / len(ys)


def fit(img, size):
    """Redimensionne VERS LE BAS seulement (Lanczos). Si l'image est plus petite que `size`, elle est rendue telle
    quelle : on ne fabrique pas de pixels. Retourne (image, plafonnee)."""
    _, Image = _np()
    pil = img if hasattr(img, "resize") else Image.fromarray(img)
    if pil.width < size[0]:
        return pil, True
    if pil.width == size[0]:
        return pil, False
    return pil.resize(size, Image.LANCZOS), False


def save_jpeg(pil, path, quality=JPEG_QUALITY_UHD):
    """JPEG de livraison : qualite 93 par defaut et sous-echantillonnage de chrominance 4:4:4."""
    from PIL import ImageFile
    # `optimize` garde toute la sortie en memoire : un tampon trop petit (image tres bruitee) casserait l'ecriture.
    ImageFile.MAXBLOCK = max(ImageFile.MAXBLOCK, 3 * pil.width * pil.height)
    pil.save(path, "JPEG", quality=quality, subsampling=0, optimize=True)


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def image_size(path):
    _, Image = _np()
    with Image.open(path) as im:
        return list(im.size)


def clock_geometry(width=WIDTH, height=HEIGHT):
    """Pixels de l'horloge : (taille du texte, hauteur des chiffres, ordonnee de la ligne de base, x gauche, x droit)."""
    text_size = CLOCK_WIDTH * width / ADVANCE_EM
    digit_h = text_size * DIGIT_EM
    return text_size, digit_h, CLOCK_BASELINE * height, width * (0.5 - CLOCK_WIDTH / 2), width * (0.5 + CLOCK_WIDTH / 2)


def crop_native(path, box=None):
    """Recadre le master natif sans le reduire ni l'agrandir : retourne l'image PIL a la resolution native."""
    _, Image = _np()
    Image.MAX_IMAGE_PIXELS = None
    box = box or PHOTO["box"]
    with Image.open(path) as im:
        im = im.convert("RGB")
        left, top, right, bottom = box
        if left < 0 or top < 0 or right > im.width or bottom > im.height:
            raise SceneError(f"le recadrage {box} sort du master {im.size}")
        w, h = right - left, bottom - top
        if abs(w / h - 16 / 9) > 0.002:
            raise SceneError(f"le recadrage {w}x{h} n'est pas du 16:9")
        return im.crop(box)


def crest_curve(alpha, points=CREST_POINTS):
    """Courbe de crete tiree du masque : pour `points` colonnes regulieres, la hauteur de la premiere ligne opaque, en
    fraction de la hauteur (1,0 si la colonne n'a pas de terrain). Elle sert a decouper le ciel de l'application."""
    np, _ = _np()
    h, w = alpha.shape
    opaque = alpha > 127
    first = np.where(opaque.any(axis=0), opaque.argmax(axis=0), h)
    xs = np.round(np.linspace(0, w - 1, points)).astype(int)
    return [round(float(first[x]) / h, 4) for x in xs]


def color_grid(rgb, grid=GRID):
    """Grille de couleur (largeur x hauteur x RVB, entiers 0-255) de la photo : moyenne de chaque cellule. L'application
    en tire la luminosite du fond sous chaque texte, apres etalonnage, sans avoir a relire les pixels."""
    np, Image = _np()
    small = Image.fromarray(rgb).resize(grid, Image.BOX)
    return np.asarray(small, dtype=np.uint8).reshape(-1).tolist()


def write_files(folder, uhd, hd):
    """Ecrit les quatre fichiers. `uhd` et `hd` sont des couples (rgb, alpha) : la photo en JPEG 95 sans
    sous-echantillonnage de chrominance, le premier plan en PNG SANS PERTE (une compression avec perte laisserait des
    franges sur la crete)."""
    np, Image = _np()
    folder.mkdir(parents=True, exist_ok=True)
    for (rgb, alpha), photo, fg in ((uhd, "photo.jpg", "foreground.png"), (hd, "photo_1080.jpg", "foreground_1080.png")):
        save_jpeg(Image.fromarray(rgb), folder / photo)
        # Pixels entierement transparents mis a zero : le PNG, toujours sans perte, ne transporte pas le ciel invisible.
        visible = rgb * (alpha > 0)[..., None]
        Image.fromarray(np.dstack([visible, alpha]), "RGBA").save(folder / fg, "PNG", optimize=True)


def build_manifest(out_dir, crest=None, grid=None, sizes=True):
    files = {}
    for name in PACK_FILES:
        p = Path(out_dir) / name
        entry = {"sha256": sha256(p), "size": p.stat().st_size}
        if sizes:
            entry["pixels"] = image_size(p)
        files[name] = entry
    return {"version": MANIFEST_VERSION, "width": UHD[0], "height": UHD[1], "hd_width": HD[0], "hd_height": HD[1],
            "waterline": WATERLINE, "files": files, "crest": crest or [], "grid": grid or [], "credits": CREDITS}


def build_pack(out_dir, pack, crest=None, grid=None, sizes=True):
    manifest = build_manifest(out_dir, crest, grid, sizes)
    (Path(out_dir) / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
    pack = Path(pack)
    pack.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(pack, "w", zipfile.ZIP_STORED) as z:
        z.write(Path(out_dir) / "manifest.json", "manifest.json")
        for name in manifest["files"]:
            z.write(Path(out_dir) / name, name)
    return manifest


def run(master_path, out_dir, pack=None):
    """Le pack : la photo du lever du soleil seule, en UHD et en 1080p. `master_path` est le master NATIF."""
    np, Image = _np()
    out_dir = Path(out_dir)
    native = crop_native(master_path)
    uhd_img, capped = fit(native, UHD)
    if capped or uhd_img.width < UHD[0]:
        raise SceneError(f"le recadrage natif ({native.size[0]} px) est plus petit que l'UHD, livraison 4K impossible sans agrandir")
    uhd = np.asarray(uhd_img, dtype=np.uint8)
    hd = np.asarray(fit(uhd_img, HD)[0], dtype=np.uint8)
    water_row = round(WATERLINE * HD[1])
    sky = sky_mask(hd, water_row)
    alphas, fulls = {}, {}
    for name, rgb, sc in (("uhd", uhd, 2), ("hd", hd, 1)):
        # Le controle de frange se fait sur le masque SANS fondu sous l'horloge : c'est la crete reelle qu'il juge.
        full = foreground_alpha(sky, water_row, PHOTO["overrides"], rgb, scale=sc, cap=False)
        check_edge_fringe(rgb.astype(np.float32), full, scale=sc, tolerance=0.25)
        a = foreground_alpha(sky, water_row, PHOTO["overrides"], rgb, scale=sc, cap=True)
        check_mask(a, water_row * sc, PHOTO["id"], scale=sc)
        alphas[name], fulls[name] = a, full
    write_files(out_dir, (uhd, alphas["uhd"]), (hd, alphas["hd"]))
    crest, grid = crest_curve(fulls["uhd"]), color_grid(uhd)   # la crete reelle, pas le fondu
    return build_pack(out_dir, pack, crest, grid) if pack else build_manifest(out_dir, crest, grid)


def verify_foreground(path):
    """Controle d'un foreground.png remplace a la main : 3840x2160 ou 1920x1080, transparence propre, pas de reflet."""
    np, Image = _np()
    with Image.open(path) as im:
        im = im.convert("RGBA")
        if im.size not in (UHD, HD):
            raise SceneError(f"{path} : {im.size[0]}x{im.size[1]} au lieu de {UHD[0]}x{UHD[1]} ou {HD[0]}x{HD[1]}")
        alpha = np.asarray(im)[..., 3]
    scale = im.size[0] // HD[0]
    check_mask(alpha, round(WATERLINE * HD[1]) * scale, str(path), scale=scale)


def repack(folder, pack):
    """Reconstruit manifest.json et le zip depuis un dossier dont un foreground.png a ete remplace a la main : chaque
    fichier doit exister et chaque premier plan est controle comme a la generation ; un refus arrete tout."""
    folder = Path(folder)
    for name in PACK_FILES:
        if not (folder / name).is_file():
            raise SceneError(f"{folder / name} manque")
    verify_foreground(folder / "foreground.png")
    verify_foreground(folder / "foreground_1080.png")
    np, Image = _np()
    old = folder / "manifest.json"
    prev = json.loads(old.read_text()) if old.is_file() else {}
    return build_pack(folder, pack, prev.get("crest"), prev.get("grid"))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--master", help="master NATIF 6000x4000 (themes/weather/masters-native/riffelsee-lever.jpg)")
    ap.add_argument("--out", help="dossier de sortie (les quatre fichiers et manifest.json)")
    ap.add_argument("--pack", help="ecrit weather_pack.zip")
    ap.add_argument("--verify", help="verifie seulement un foreground.png")
    ap.add_argument("--repack", help="dossier dont un foreground.png a ete remplace a la main : refait le pack (avec --pack)")
    args = ap.parse_args(argv)
    try:
        if args.verify:
            verify_foreground(args.verify)
            print("foreground.png propre")
            return 0
        if args.repack:
            if not args.pack:
                ap.error("--repack demande --pack")
            manifest = repack(args.repack, args.pack)
        else:
            if not (args.master and args.out):
                ap.error("--master et --out sont requis")
            manifest = run(args.master, args.out, args.pack)
    except SceneError as error:
        print(f"ECHEC : {error}", file=sys.stderr)
        return 1
    total = sum(f["size"] for f in manifest["files"].values())
    print(f"pack pret : {len(manifest['files'])} fichiers, {total / 1e6:.1f} Mo")
    return 0


if __name__ == "__main__":
    sys.exit(main())
