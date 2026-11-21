"""tools/prepare_weather_scene.py : recadrage, detourage d'horizon, controles du masque, pack et manifeste.

Les tests qui calculent sur des images demandent numpy et Pillow (tools/.venv) et sont sautes sans eux.
"""
import hashlib
import json
import re
import tempfile
import unittest
import zipfile
from pathlib import Path

try:
    import numpy as np
    from PIL import Image
except ImportError:  # pragma: no cover
    np = None
    Image = None

from tools import prepare_weather_scene as pws

ROOT = Path(__file__).resolve().parents[1]
NEEDS_IMAGES = unittest.skipUnless(np is not None, "numpy et Pillow requis (tools/.venv)")


def synthetic_scene(width=480, height=270, water=0.56):
    """Ciel en degrade, un triangle de montagne sombre au centre, une rive sombre, de l'eau claire dessous."""
    rows = np.arange(height)[:, None]
    sky = np.zeros((height, width, 3), dtype=np.float32)
    sky[..., 0] = 120 + 100 * rows / height
    sky[..., 1] = 170 + 60 * rows / height
    sky[..., 2] = 230 - 20 * rows / height
    water_row = round(water * height)
    img = sky.copy()
    xs = np.arange(width)[None, :]
    peak = water_row - (0.30 * height) * np.clip(1 - np.abs(xs - width / 2) / (width * 0.28), 0, 1)
    ground = (rows >= peak) & (rows < water_row + 6)
    img[np.broadcast_to(ground, img.shape[:2])] = (45, 40, 38)
    img[water_row + 6:] = (140, 170, 200)
    return img.astype(np.uint8), water_row


class GeometryTest(unittest.TestCase):
    def java(self, name):
        text = (ROOT / "launcher/app/src/main/java/com/gathin/tvhome/AlpineMetrics.java").read_text()
        return float(re.search(rf"{name} = ([0-9.]+)f", text).group(1))

    def test_clock_geometry_matches_the_launcher(self):
        self.assertAlmostEqual(self.java("CLOCK_WIDTH"), pws.CLOCK_WIDTH)
        self.assertAlmostEqual(self.java("BASELINE"), pws.CLOCK_BASELINE)
        self.assertAlmostEqual(self.java("OCCLUSION"), pws.OCCLUSION)
        self.assertAlmostEqual(self.java("SOFT"), pws.SOFT)
        self.assertAlmostEqual(self.java("ADVANCE_EM"), pws.ADVANCE_EM)
        self.assertAlmostEqual(self.java("DIGIT_RATIO"), pws.DIGIT_EM, delta=0.01)

    def test_waterline_matches_the_drawn_scene(self):
        java = (ROOT / "launcher/app/src/main/java/com/gathin/tvhome/DrawnAlpineBackdrop.java").read_text()
        match = re.search(r"HORIZON = ([0-9.]+)f", java)
        self.assertAlmostEqual(float(match.group(1)), pws.WATERLINE)

    def test_clock_sits_above_the_waterline_and_is_raised(self):
        text_size, digit_h, baseline, left, right = pws.clock_geometry()
        self.assertLess(baseline, pws.WATERLINE * pws.HEIGHT, "la mise en page B remonte l'horloge au-dessus de la ligne d'eau")
        self.assertAlmostEqual(baseline, 565.9, delta=0.5)
        self.assertAlmostEqual(right - left, pws.CLOCK_WIDTH * pws.WIDTH)
        self.assertLess(digit_h, text_size)


@NEEDS_IMAGES
class MaskTest(unittest.TestCase):
    def setUp(self):
        self.rgb, self.water = synthetic_scene()

    def alpha(self, **kw):
        sky = pws.sky_mask(self.rgb, self.water)
        return pws.foreground_alpha(sky, self.water, rgb=self.rgb, **kw)

    def test_the_sky_is_found_and_the_mountain_is_not(self):
        sky = pws.sky_mask(self.rgb, self.water)
        self.assertTrue(sky[5, 5])
        self.assertTrue(sky[30, 240])
        self.assertFalse(sky[self.water - 5, 240])

    def test_foreground_is_opaque_below_the_crest_and_clear_above(self):
        alpha = self.alpha()
        h, w = alpha.shape
        self.assertEqual(alpha[5, w // 2], 0)
        self.assertEqual(alpha[self.water - 3, w // 2], 255)
        self.assertEqual(alpha[h - 2, w // 2], 0)       # l'eau n'est jamais dans le premier plan

    def test_a_clean_mask_passes_every_check(self):
        alpha = self.alpha()
        self.assertTrue(pws.check_mask(alpha, self.water, "synthetique"))
        self.assertLess(pws.check_edge_fringe(self.rgb.astype(np.float32), alpha), 0.25)

    def test_a_mask_with_the_reflection_is_refused(self):
        alpha = self.alpha()
        alpha[self.water + pws.SHORE_MARGIN + 10: self.water + pws.SHORE_MARGIN + 40, 100:300] = 255
        with self.assertRaisesRegex(pws.SceneError, "reflet"):
            pws.check_mask(alpha, self.water, "x")

    def test_an_empty_mask_is_refused(self):
        with self.assertRaises(pws.SceneError):
            pws.check_mask(np.zeros((270, 480), dtype=np.uint8), self.water, "x")

    def test_a_dirty_crest_is_refused(self):
        alpha = self.alpha()
        # des trous de ciel sous la crete, a plusieurs endroits
        for x in range(0, 480, 6):
            alpha[self.water - 6:self.water, x:x + 3] = 0
        with self.assertRaisesRegex(pws.SceneError, "sale"):
            pws.check_mask(alpha, self.water, "x")

    def test_a_jagged_crest_is_refused(self):
        alpha = np.zeros((270, 480), dtype=np.uint8)
        for x in range(480):
            top = 60 if x % 2 == 0 else 140
            alpha[top:self.water + 10, x] = 255
        with self.assertRaisesRegex(pws.SceneError, "dents de scie"):
            pws.check_mask(alpha, self.water, "x")

    def test_a_mask_shifted_into_the_sky_shows_a_fringe(self):
        alpha = self.alpha()
        shifted = np.zeros_like(alpha)
        shifted[:-14] = alpha[14:]                       # crete 14 px trop haute : du ciel colle a la crete
        with self.assertRaisesRegex(pws.SceneError, "franges"):
            pws.check_edge_fringe(self.rgb.astype(np.float32), shifted)

    def test_manual_override_replaces_the_crest(self):
        base = self.alpha()
        edited = self.alpha(overrides=[(100, 140, self.water - 8)])
        self.assertEqual(edited[self.water - 4, 120], 255)
        self.assertEqual(edited[self.water - 12, 120], 0)
        self.assertNotEqual(int(base[self.water - 12, 120]), 255 + 1)  # le fond reste calculable

    def test_median_filter_removes_a_single_column_spike(self):
        values = np.full(40, 100, dtype=np.int64)
        values[20] = 10
        self.assertEqual(int(pws.median_filter_1d(values, 9)[20]), 100)
        step = np.concatenate([np.full(20, 50), np.full(20, 150)])
        filtered = pws.median_filter_1d(step, 9)
        self.assertEqual(int(filtered[5]), 50)
        self.assertEqual(int(filtered[35]), 150)

    def test_the_clock_area_keeps_the_top_of_the_digits_visible(self):
        rgb, water = synthetic_scene(1920, 1080)
        sky = pws.sky_mask(rgb, water)
        alpha = pws.foreground_alpha(sky, water, rgb=rgb)
        _, digit_h, baseline, left, right = pws.clock_geometry(1920, 1080)
        cap = baseline - pws.OCCLUSION * digit_h
        x = int((left + right) / 2) - 200
        above_top = int(baseline - digit_h) + 2
        self.assertEqual(int(alpha[above_top, x]), 0, "le haut des chiffres reste devant le terrain")
        self.assertEqual(int(alpha[int(baseline) - 2, x]), 255, "le bas des chiffres est derriere la rive")
        self.assertLessEqual(cap, baseline)


@NEEDS_IMAGES
class PackTest(unittest.TestCase):
    def fake_out(self, folder):
        folder = Path(folder)
        folder.mkdir(parents=True, exist_ok=True)
        (folder / "photo.jpg").write_bytes(b"photo")
        (folder / "photo_1080.jpg").write_bytes(b"photo-hd")
        (folder / "foreground.png").write_bytes(b"fg")
        (folder / "foreground_1080.png").write_bytes(b"fg-hd")

    def test_manifest_lists_the_four_files_with_their_hash(self):
        with tempfile.TemporaryDirectory() as t:
            self.fake_out(t)
            m = pws.build_manifest(t, crest=[0.4] * 241, grid=[0] * (32 * 18 * 3), sizes=False)
            self.assertEqual(m["version"], pws.MANIFEST_VERSION)
            self.assertEqual(m["version"], 3)
            self.assertEqual(sorted(m["files"]), sorted(pws.PACK_FILES))
            self.assertEqual(m["files"]["photo.jpg"]["sha256"], hashlib.sha256(b"photo").hexdigest())
            self.assertEqual(m["files"]["foreground.png"]["size"], len(b"fg"))
            self.assertEqual(len(m["crest"]), pws.CREST_POINTS)
            self.assertEqual(len(m["grid"]), pws.GRID[0] * pws.GRID[1] * 3)
            self.assertEqual(m["credits"]["author"], "Deralpinbergsteiger")
            self.assertEqual(m["credits"]["license"], "CC BY-SA 4.0")

    def test_pack_zip_holds_the_manifest_and_the_four_images(self):
        with tempfile.TemporaryDirectory() as t:
            self.fake_out(t)
            pack = Path(t) / "out" / "weather_pack.zip"
            pws.build_pack(t, pack, sizes=False)
            with zipfile.ZipFile(pack) as z:
                names = set(z.namelist())
                manifest = json.loads(z.read("manifest.json"))
            self.assertEqual(names, set(pws.PACK_FILES) | {"manifest.json"})
            for rel in manifest["files"]:
                self.assertIn(rel, names)

    def test_manifest_keys_match_what_the_launcher_accepts(self):
        java = (ROOT / "launcher/app/src/main/java/com/gathin/tvhome/WeatherPackManifest.java").read_text()
        for name in pws.PACK_FILES:
            self.assertIn(name, java)
        self.assertRegex(java, r"VERSION = 3")

    def test_the_shipped_pack_is_the_single_photo_pack(self):
        with zipfile.ZipFile(ROOT / "themes/weather/weather_pack.zip") as z:
            names = set(z.namelist())
            manifest = json.loads(z.read("manifest.json"))
            self.assertEqual(names, set(pws.PACK_FILES) | {"manifest.json"})
            for name, rec in manifest["files"].items():
                data = z.read(name)
                self.assertEqual(len(data), rec["size"], name)
                self.assertEqual(hashlib.sha256(data).hexdigest(), rec["sha256"], name)
        self.assertEqual(manifest["version"], 3)
        self.assertEqual(manifest["files"]["photo.jpg"]["pixels"], [3840, 2160])
        self.assertEqual(manifest["files"]["photo_1080.jpg"]["pixels"], [1920, 1080])
        self.assertEqual(len(manifest["crest"]), pws.CREST_POINTS)
        self.assertEqual(len(manifest["grid"]), pws.GRID[0] * pws.GRID[1] * 3)

    def test_verify_refuses_a_wrong_size_and_a_reflection(self):
        with tempfile.TemporaryDirectory() as t:
            small = Path(t) / "small.png"
            Image.new("RGBA", (100, 100), (0, 0, 0, 0)).save(small)
            with self.assertRaisesRegex(pws.SceneError, "au lieu de"):
                pws.verify_foreground(small)
            full = np.zeros((pws.HEIGHT, pws.WIDTH, 4), dtype=np.uint8)
            full[400:560, :, 3] = 255
            full[800:900, 300:900, 3] = 255                 # du reflet, bien sous la ligne d'eau
            bad = Path(t) / "bad.png"
            Image.fromarray(full, "RGBA").save(bad)
            with self.assertRaisesRegex(pws.SceneError, "reflet"):
                pws.verify_foreground(bad)

    def test_repack_refuses_a_missing_photo(self):
        with tempfile.TemporaryDirectory() as t:
            with self.assertRaises(pws.SceneError):
                pws.repack(t, Path(t) / "x.zip")


class CreditsTest(unittest.TestCase):
    def test_credits_file_names_the_author_the_licence_and_the_source(self):
        text = (ROOT / "themes/weather/CREDITS.md").read_text(encoding="utf-8")
        self.assertIn("Deralpinbergsteiger", text)
        self.assertIn("CC BY-SA 4.0", text)
        self.assertIn("https://creativecommons.org/licenses/by-sa/4.0/", text)
        self.assertIn(pws.CREDITS["source"], text)
        for name in pws.PACK_FILES:
            self.assertIn(f"`{name}`", text)

    def test_the_app_credits_mention_the_same_licence(self):
        for values in ("values", "values-fr", "values-iw"):
            xml = (ROOT / f"launcher/app/src/main/res/{values}/strings.xml").read_text(encoding="utf-8")
            self.assertIn("alpine_credits_text", xml)
            self.assertIn("CC BY-SA 4.0", xml)
            self.assertIn("Deralpinbergsteiger", xml)
            self.assertNotIn("sunset and a red sky", xml, "une seule photo")




SOURCES = ROOT / "themes/weather/sources.json"


@NEEDS_IMAGES
class NoUpscaleTest(unittest.TestCase):
    """Qualite d'image : jamais d'agrandissement, masters natifs, JPEG 4:4:4, premier plan sans perte."""

    def test_fit_never_enlarges(self):
        small = Image.new("RGB", (1600, 900), (10, 20, 30))
        out, capped = pws.fit(small, pws.UHD)
        self.assertTrue(capped)
        self.assertEqual(out.size, (1600, 900))
        out, capped = pws.fit(small, pws.HD)           # 1600 px < 1920 px : meme le 1080p serait un agrandissement
        self.assertTrue(capped)
        self.assertEqual(out.size, (1600, 900))
        out, capped = pws.fit(small, (1280, 720))
        self.assertFalse(capped)
        self.assertEqual(out.size, (1280, 720))

    def test_fit_reduces_and_keeps_native_size(self):
        big = Image.new("RGB", (4000, 2250))
        self.assertEqual(pws.fit(big, pws.UHD)[0].size, pws.UHD)
        exact = Image.new("RGB", pws.UHD)
        out, capped = pws.fit(exact, pws.UHD)
        self.assertFalse(capped)
        self.assertIs(out, exact)   # a la taille exacte : aucun reechantillonnage

    def test_the_photo_box_is_16x9_wide_enough_and_inside_its_master(self):
        sources = json.loads(SOURCES.read_text(encoding="utf-8"))
        self.assertEqual(set(sources), set(pws.PHOTOS), "les photos livrees, ni plus ni moins")
        for pid, entry in pws.PHOTOS.items():
            box = entry["photo"]["box"]
            left, top, right, bottom = box
            w, h = right - left, bottom - top
            self.assertAlmostEqual(w / h, 16 / 9, delta=0.003, msg=pid)
            self.assertGreaterEqual(w, pws.UHD[0], f"{pid} : le recadrage natif doit etre au moins aussi large que l'UHD")
            self.assertGreaterEqual(left, 0)
            self.assertGreaterEqual(top, 0)
            self.assertLessEqual(right, sources[pid]["width"])
            self.assertLessEqual(bottom, sources[pid]["height"])
            self.assertFalse(sources[pid]["capped"])

    def test_crop_native_is_a_pixel_crop_with_no_resampling(self):
        with tempfile.TemporaryDirectory() as t:
            master = Path(t) / "riffelsee-lever.jpg"
            im = Image.new("RGB", (6000, 4000))
            im.putpixel((100, 900), (255, 0, 0))
            im.save(master, quality=100, subsampling=0)
            out = pws.crop_native(master)
            self.assertEqual(out.size, (3900, 2194))
            self.assertGreater(out.getpixel((100, 900 - 831))[0], 200)   # le pixel rouge est la, a sa place, intact

    def test_crop_native_refuses_a_box_outside_or_not_16x9(self):
        with tempfile.TemporaryDirectory() as t:
            master = Path(t) / "small.jpg"
            Image.new("RGB", (3000, 2000)).save(master)
            with self.assertRaises(pws.SceneError):
                pws.crop_native(master)
            big = Path(t) / "big.jpg"
            Image.new("RGB", (6000, 4000)).save(big)
            with self.assertRaisesRegex(pws.SceneError, "16:9"):
                pws.crop_native(big, (0, 0, 3900, 3000))

    def test_jpeg_delivery_is_quality_95_and_444(self):
        from PIL import JpegImagePlugin
        with tempfile.TemporaryDirectory() as t:
            path = Path(t) / "a.jpg"
            noise = np.random.default_rng(1).integers(0, 255, (270, 480, 3), dtype=np.uint8)
            pws.save_jpeg(Image.fromarray(noise), path)
            with Image.open(path) as im:
                self.assertEqual(JpegImagePlugin.get_sampling(im), 0, "chrominance 4:4:4 attendue")
            self.assertEqual(pws.JPEG_QUALITY_UHD, 95)
            self.assertGreaterEqual(pws.JPEG_QUALITY_UHD, 92)

    def test_foreground_png_is_lossless_where_visible_and_clean_elsewhere(self):
        rgb, water = synthetic_scene(480, 270)
        sky = pws.sky_mask(rgb, water)
        alpha = pws.foreground_alpha(sky, water, rgb=rgb, cap=False)
        with tempfile.TemporaryDirectory() as t:
            pws.write_files(Path(t), (rgb, alpha), (rgb, alpha))
            with Image.open(Path(t) / "foreground.png") as im:
                back = np.asarray(im)
        self.assertEqual(back.shape, (270, 480, 4))
        visible = alpha > 0
        self.assertTrue((back[..., :3][visible] == rgb[visible]).all(), "couleurs visibles identiques a la photo, sans perte")
        self.assertTrue((back[..., 3] == alpha).all())
        self.assertEqual(int(back[..., :3][~visible].max()), 0, "pixels transparents mis a zero : PNG plus leger")

    def test_mask_is_computed_at_full_resolution_for_uhd(self):
        rgb, water = synthetic_scene(1920, 1080)
        sky = pws.sky_mask(rgb, water)
        big = np.asarray(Image.fromarray(rgb).resize((3840, 2160), Image.LANCZOS))
        alpha = pws.foreground_alpha(sky, water, rgb=big, scale=2, cap=False)
        self.assertEqual(alpha.shape, (2160, 3840))
        self.assertTrue(pws.check_mask(alpha, water * 2, "uhd", scale=2))
        self.assertTrue(pws.check_edge_fringe(big.astype(np.float32), alpha, scale=2) < 0.25)
        below = alpha[(water + pws.SHORE_MARGIN + 2) * 2:, :]
        self.assertEqual(int(below.max()), 0, "aucun terrain sous la ligne d'eau en UHD non plus")

    def test_open_crest_removes_a_narrow_spike_and_keeps_wide_features(self):
        crest = np.full(200, 300, dtype=np.int64)
        crest[50:56] = 100                       # pointe de 6 colonnes (nuage contre la montagne)
        crest[120:170] = 150                     # vrai pic large
        out = pws.open_crest(crest, 6)
        self.assertEqual(int(out[52]), 300)
        self.assertEqual(int(out[140]), 150)
        self.assertEqual(int(out[10]), 300)
        edge = np.full(100, 300, dtype=np.int64)
        edge[:6] = 100                           # pointe collee au bord gauche (plus etroite que la demi-fenetre)
        self.assertEqual(int(pws.open_crest(edge, 8)[3]), 300)
        self.assertIs(pws.open_crest(crest, 0), crest)

    def test_sources_json_records_native_provenance(self):
        sources = json.loads(SOURCES.read_text(encoding="utf-8"))
        self.assertEqual(sorted(sources), ["braies-matin", "riffelsee-lever"])
        for pid, licence, size, author in (("riffelsee-lever", "CC BY-SA 4.0", (6000, 4000), "Deralpinbergsteiger"),
                                           ("braies-matin", "CC BY-SA 2.0", (8213, 5475), "Eric Kilby")):
            rec = sources[pid]
            self.assertEqual(rec["license"], licence)
            self.assertTrue(rec["original_url"].startswith("https://upload.wikimedia.org/wikipedia/commons/"))
            self.assertNotIn("/thumb/", rec["original_url"], "jamais une miniature")
            self.assertEqual((rec["width"], rec["height"]), size)
            self.assertRegex(rec["sha256"], r"^[0-9a-f]{64}$")
            self.assertEqual(rec["author"], author)
            self.assertTrue(rec["commons_url"].startswith("https://commons.wikimedia.org/wiki/File:"))

    def test_credits_name_the_photo_and_its_licence(self):
        text = (ROOT / "themes/weather/CREDITS.md").read_text(encoding="utf-8")
        for pid, rec in json.loads(SOURCES.read_text(encoding="utf-8")).items():
            self.assertIn(rec["author"], text, pid)
            self.assertIn(rec["commons_url"].replace("_", " ").split("/wiki/File:")[1].split(".jpg")[0].replace(" ", "_"), text.replace(" ", "_"), pid)
        self.assertIn("Matterhorn reflection in the Riffelsee at sunrise", text)
        for name in pws.PACK_FILES:
            self.assertIn(name, text)


if __name__ == "__main__":
    unittest.main()
