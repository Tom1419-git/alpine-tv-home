"""Interface de comparaison de l'accueil alpin (photo unique, mise en page B) : geometrie, port du moteur, contraste, page.

La geometrie et le moteur d'etalonnage de l'interface sont des PORTS de AlpineMetrics.java, AlpineDock.java et
SceneGrade.java : ces tests les comparent au code Java, pour que les apercus restent calcules avec ce que fait l'application.
Les tests qui executent du JavaScript demandent node (sautes sans lui).
"""
import json
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

try:
    import numpy as np  # noqa: F401
    from PIL import Image
except ImportError:  # pragma: no cover
    Image = None

ROOT = Path(__file__).resolve().parents[1]
VIEWER = ROOT / "tools/alpine_preview"
JAVA = ROOT / "launcher/app/src/main/java/com/gathin/tvhome"
NODE = shutil.which("node")
PACK_ZIP = ROOT / "themes/weather/weather_pack.zip"


def node(expr, manifest=None):
    code = (f"const L=require({json.dumps(str(VIEWER / 'layout.js'))});const G=require({json.dumps(str(VIEWER / 'grade.js'))});"
            f"const M={json.dumps(manifest)};console.log(JSON.stringify({expr}));")
    return json.loads(subprocess.run([NODE, "-e", code], capture_output=True, text=True, check=True).stdout)


def java_float(name, text):
    return float(re.search(rf"{name} = (-?[0-9.]+)f", text).group(1))


def pack_manifest():
    with zipfile.ZipFile(PACK_ZIP) as z:
        return json.loads(z.read("manifest.json"))


@unittest.skipUnless(NODE, "node requis pour la geometrie de l'interface")
class LayoutGeometryTest(unittest.TestCase):
    def test_layout_a_is_the_tv_measured_on_17_nov_2026(self):
        m = node("L.clockMetrics(L.LAYOUTS.A)")
        # Capture de la TV (1080p) avant la mise en page B : chiffres de 385 a 618, date a 331, salutation a 282.
        self.assertAlmostEqual(m["top"], 385, delta=3)
        self.assertAlmostEqual(m["baseline"], 618, delta=1)
        self.assertAlmostEqual(m["dateY"], 331, delta=3)
        self.assertAlmostEqual(m["greetY"], 282, delta=3)

    def test_layout_b_raises_the_clock_and_the_pills(self):
        a, b = node("L.clockMetrics(L.LAYOUTS.A)"), node("L.clockMetrics(L.LAYOUTS.B)")
        self.assertAlmostEqual(a["baseline"] - b["baseline"], 52, delta=1.5, msg="horloge remontee de 52 px")
        self.assertAlmostEqual(b["baseline"], 565.9, delta=0.5)
        self.assertAlmostEqual(b["dateY"], 232.5, delta=0.5)
        self.assertAlmostEqual(b["greetY"], 183.3, delta=0.5)
        self.assertEqual(node("L.LAYOUTS.A.pillsTop") - node("L.LAYOUTS.B.pillsTop"), 54, "pilules remontees de 54 px")
        self.assertEqual(node("L.PILLS")[0]["y"], 64)

    def test_constants_match_the_android_code(self):
        metrics = (JAVA / "AlpineMetrics.java").read_text()
        dock = (JAVA / "AlpineDock.java").read_text()
        b = "L.LAYOUTS.B."
        self.assertEqual(node(b + "clockWidth"), java_float("CLOCK_WIDTH", metrics))
        self.assertEqual(node(b + "baseline"), java_float("BASELINE", metrics))
        self.assertEqual(node(b + "occlusion"), java_float("OCCLUSION", metrics))
        self.assertEqual(node(b + "soft"), java_float("SOFT", metrics))
        self.assertEqual(node(b + "dateGap"), java_float("DATE_GAP", metrics))
        self.assertEqual(node("L.DOCK_TOP"), java_float("TOP_FRAC", dock))
        self.assertEqual(node("L.DOCK_BOTTOM"), java_float("BOTTOM_FRAC", dock))
        self.assertEqual(node("L.ROW_GAP_DP"), java_float("ROW_GAP_DP", dock))
        self.assertEqual(node("L.BELOW_ALPHA"), java_float("BELOW_ALPHA", dock))
        self.assertEqual(node("L.PILLS.map(p=>[p.x,p.w])"), [[872, 242], [1134, 360], [1514, 293]])
        self.assertIn("{{872f, 242f}, {1134f, 360f}, {1514f, 293f}}", metrics)
        self.assertEqual(node("L.PILLS[0].h"), java_float("PILLS_HEIGHT", metrics))
        self.assertEqual(node("L.PILLS[0].y"), java_float("PILLS_TOP", metrics))

    def test_layout_b_respects_the_margins_and_overlaps_nothing(self):
        self.assertEqual(node("L.checks(L.LAYOUTS.B)"), [])
        cards = node("L.dockCards(L.LAYOUTS.B)")
        self.assertEqual(len(cards), 8)
        first, second = [c for c in cards if c["row"] == 0], [c for c in cards if c["row"] == 1]
        self.assertEqual({c["y"] for c in first}, {612})
        self.assertGreater(min(c["y"] for c in second), max(c["y"] + c["h"] for c in first), "les rangees ne se chevauchent pas")
        self.assertLessEqual(max(c["y"] + c["h"] for c in cards), 1080 * 0.95 + 1)
        self.assertGreaterEqual(min(c["x"] for c in cards), 96)
        self.assertLessEqual(max(c["x"] + c["w"] for c in cards), 1920 - 96)
        self.assertGreaterEqual(min(c["w"] for c in cards), 300, "grandes cartes")

    def test_clock_never_touches_the_status_pills_or_the_dock(self):
        m = node("L.clockMetrics(L.LAYOUTS.B)")
        pills_bottom = max(p["y"] + p["h"] for p in node("L.PILLS"))
        self.assertGreater(m["greetY"] - m["greetSize"], pills_bottom - 1)
        self.assertLess(m["baseline"], node("L.cardTop(1080)") - 20)

    def test_layout_a_is_flagged_as_the_cut_off_layout(self):
        self.assertTrue(any("coup" in i for i in node("L.checks(L.LAYOUTS.A)")))


@unittest.skipUnless(NODE, "node requis")
class EnginePortTest(unittest.TestCase):
    def test_keyframes_are_the_java_table(self):
        java = (JAVA / "SceneGrade.java").read_text()
        block = java[java.index("KEYFRAMES-BEGIN"):java.index("KEYFRAMES-END")]
        rows = [[float(v) for v in re.findall(r"-?\d+\.?\d*(?=f)", line)] for line in block.splitlines() if line.strip().startswith("{")]
        self.assertEqual(len(rows), 10)
        self.assertEqual(node("G.KEYS"), rows)

    def test_overlay_constants_are_the_java_ones(self):
        java = (JAVA / "SceneGrade.java").read_text()
        for name in ("CLOUD_ALPHA", "FOG_ALPHA", "SUN_GLOW_ALPHA", "STORM_ALPHA", "PILL_GLASS_ALPHA"):
            self.assertEqual(node(f"G.consts.{name}"), java_float(name, java), name)
        contrast = (JAVA / "ContrastPlan.java").read_text()
        self.assertEqual(node("G.MAX_VEIL"), java_float("MAX_VEIL", contrast))
        self.assertEqual(node("G.DARK_L"), java_float("DARK_L", contrast))
        self.assertIn(f"PREFER_WHITE = {node('0.5')}0f", contrast)

    def test_the_grade_is_continuous_across_the_whole_sun_path(self):
        out = node("(()=>{let worst=0,prev=null;for(let e=-20;e<=70;e+=0.25){const g=G.compute(e,0,{cloud:0,fog:0,rain:0,snow:0,storm:0});"
                   "const v=[g.exposure,g.contrast,g.saturation,g.gainR,g.gainG,g.gainB,g.veilAlpha];"
                   "if(prev)worst=Math.max(worst,...v.map((x,i)=>Math.abs(x-prev[i])));prev=v;}return worst})()")
        self.assertLess(out, 0.06, "aucun saut d'etalonnage entre deux quarts de degre")

    def test_night_is_darker_than_noon_and_dawn_warmer_than_noon(self):
        n, d, m = node("G.compute(-20,1,{cloud:0,fog:0,rain:0,snow:0,storm:0})"), node("G.compute(0.5,0,{cloud:0,fog:0,rain:0,snow:0,storm:0})"), node("G.compute(40,0,{cloud:0,fog:0,rain:0,snow:0,storm:0})")
        self.assertLess(n["exposure"], 0.4 * m["exposure"])
        self.assertGreater(d["gainR"] / d["gainB"], 1.2 * m["gainR"] / m["gainB"])

    @unittest.skipUnless(PACK_ZIP.is_file(), "pack absent")
    def test_every_phase_and_weather_passes_the_contrast_model(self):
        """8 phases x 9 meteos x 7 zones : le modele garantit 4,5:1 (3:1 pour l'heure) avec un voile borne."""
        m = pack_manifest()
        rows = node("(()=>{const out=[];for(const p of Object.keys(G.PHASES))for(const w of Object.keys(G.CONDITIONS)){const st=G.state(p,w);"
                    "for(const z of G.ZONES){const pl=G.plan(z,1920,1080,M.grid,M.crest,st.grade,st.mix);out.push([p,w,z,pl.ratio,pl.threshold,pl.veilAlpha,pl.ok])}}return out})()",
                    manifest={"grid": m["grid"], "crest": m["crest"]})
        self.assertEqual(len(rows), 8 * 9 * 7)
        bad = [r for r in rows if not r[6] or r[3] + 1e-3 < r[4]]
        self.assertEqual(bad, [], "combinaisons sous le seuil")
        self.assertLessEqual(max(r[5] for r in rows), 0.6, "voile borne")


@unittest.skipUnless(NODE, "node requis")
class TransitionEngineTest(unittest.TestCase):
    def test_observations_drive_the_targets_like_the_java_code(self):
        t = node("G.deriveTargets('pluie',{cloudPct:90,precipMm:2,windKmh:30,visibilityM:4000})")
        self.assertAlmostEqual(t["cloud"], 0.9, places=3)
        self.assertAlmostEqual(t["rain"], 0.5482, delta=0.002)
        self.assertAlmostEqual(t["fog"], 0.1236, delta=0.002)
        self.assertAlmostEqual(t["wind"], 0.6667, delta=0.001)
        self.assertLessEqual(node("G.deriveTargets('bruine',{cloudPct:95,precipMm:8,windKmh:5,visibilityM:9000}).rain"), 0.35 + 1e-9)
        self.assertGreater(node("G.deriveTargets('brouillard',{cloudPct:70,precipMm:0,windKmh:3,visibilityM:300}).fog"), 0.8)
        self.assertEqual(node("G.deriveTargets('clair',{cloudPct:5,precipMm:0,windKmh:3,visibilityM:30000}).fog"), 0)

    def test_sun_follows_the_noaa_equations_for_lausanne_on_the_real_date(self):
        for minute, expected in ((60, -60.9), (458, -0.7), (750, 24.6), (1020, -0.9), (1380, -58.0)):
            self.assertAlmostEqual(node(f"G.sunPosition(2026,11,17,{minute},46.516,6.63282,60)[1]"), expected, delta=0.3)
        rise, sunset = node("G.riseSet(2026,11,17,46.516,6.63282,60)")
        self.assertAlmostEqual(rise, 458, delta=3)
        self.assertAlmostEqual(sunset, 1020, delta=3)

    def test_a_weather_update_glides_without_a_jump_and_ends_on_target(self):
        out = node("(()=>{const m=G.newMix();m.target=G.deriveTargets('clair',null);for(const k of Object.keys(m.target))m[k]=m.target[k];"
                   "m.target=G.deriveTargets('pluie',{cloudPct:100,precipMm:4,windKmh:40,visibilityM:5000});let prev=m.rain,worst=0,mono=true;"
                   "for(let i=0;i<450;i++){G.stepMix(m,1/30);worst=Math.max(worst,m.rain-prev);if(m.rain<prev-1e-9)mono=false;prev=m.rain}"
                   "return {worst,mono,rain:m.rain,target:m.target.rain}})()")
        self.assertTrue(out["mono"])
        self.assertLess(out["worst"], 0.02)
        self.assertAlmostEqual(out["rain"], out["target"], delta=0.05)

    def test_haze_follows_fog_rain_and_snow(self):
        self.assertAlmostEqual(node("G.haze({fog:0.8,rain:0,snow:0,cloud:0})"), 0.8)
        self.assertAlmostEqual(node("G.haze({fog:0,rain:0.75,snow:0,cloud:0.9})"), 0.30)
        self.assertAlmostEqual(node("G.haze({fog:0,rain:0,snow:0.85,cloud:0})"), 0.2975)

    def test_the_transitions_tab_is_wired(self):
        html = (VIEWER / "index.html").read_text(encoding="utf-8")
        js = (VIEWER / "anim.js").read_text(encoding="utf-8")
        ids = set(re.findall(r'id="([A-Za-z0-9_-]+)"', html))
        used = set(re.findall(r"\$\('#([A-Za-z0-9_-]+)'\)", js))
        self.assertEqual(sorted(used - ids), [])
        self.assertIn('<script src="anim.js"></script>', html)
        for needle in ("an-play", "an-pos", "an-strip", "an-mode", "an-speed", "an-res", "an-back", "an-fwd"):
            self.assertIn(f'id="{needle}"', html)
        for needle in ("deriveTargets", "stepMix", "skyAt", "requestAnimationFrame", "visibilityM", "precipMm"):
            self.assertIn(needle, js)


class PageConsistencyTest(unittest.TestCase):
    def test_every_script_and_asset_the_page_loads_exists(self):
        html = (VIEWER / "index.html").read_text(encoding="utf-8")
        for name in re.findall(r'<script src="([^"]+)"', html) + re.findall(r'<link rel="stylesheet" href="([^"]+)"', html):
            if name != "data.js":   # genere par build_alpine_preview.py
                self.assertTrue((VIEWER / name).is_file(), name)

    def test_every_element_the_script_looks_up_is_in_the_page(self):
        html = (VIEWER / "index.html").read_text(encoding="utf-8")
        js = (VIEWER / "app.js").read_text(encoding="utf-8")
        ids = set(re.findall(r'id="([A-Za-z0-9_-]+)"', html))
        used = set(re.findall(r"\$\('#([A-Za-z0-9_-]+)'\)", js))
        self.assertEqual(sorted(used - ids - {"bolt"}), [])   # la case "bolt" est creee par l'onglet Legende

    def test_page_has_the_features_the_brief_asks_for(self):
        html = (VIEWER / "index.html").read_text(encoding="utf-8")
        js = (VIEWER / "app.js").read_text(encoding="utf-8")
        for needle in ("cmp-zoom", "cmp-mode", "cmp-res", "cmp-op", "cmp-pixel", "cmp-detail", "tv-res", "tv-detail", "summary-btn",
                       "fav-count", "presets", "ct-model", "ct-pixels"):
            self.assertIn(f'id="{needle}"', html)
        for needle in ("toggleFav", "localStorage", "devicePixelRatio", "Aper", "runModel", "runPixels", "showDetail"):
            self.assertIn(needle, js)
        for group in ("phase", "weather", "state"):
            self.assertIn(f'data-group="{group}"', html)
        self.assertNotIn('data-group="photo"', html, "une seule photo")
        for phase in ("avant-lever", "lever", "matin", "midi", "apres-midi", "coucher", "crepuscule", "nuit"):
            self.assertIn(f"['{phase}'", js)
        for weather in ("clair", "partiel", "couvert", "pluie", "bruine", "averses", "neige", "brouillard", "orage"):
            self.assertIn(f"['{weather}'", js)
        for state in ("repos", "focus", "focus2", "musique"):
            self.assertIn(f"['{state}'", js)
        for preset in ("Nuit claire", "Nuit neigeuse", "Matin brumeux", "Midi d", "Coucher d", "Coucher pluvieux", "Nuit pluvieuse", "Orage"):
            self.assertIn(preset, js)

    def test_page_needs_no_network(self):
        for f in VIEWER.iterdir():
            if f.suffix in (".html", ".js", ".css"):
                text = f.read_text(encoding="utf-8")
                self.assertNotRegex(text, r"""(src|href)=["']https?://""", f.name)
                self.assertNotIn("@import url(http", text, f.name)

    def test_no_reference_to_the_abandoned_photos(self):
        for f in list(VIEWER.iterdir()) + [ROOT / "tools/build_alpine_preview.py", ROOT / "tools/fetch_weather_masters.py",
                                           ROOT / "themes/weather/sources.json", ROOT / "themes/weather/CREDITS.md"]:
            if f.suffix in (".html", ".js", ".css", ".py", ".json", ".md"):
                text = f.read_text(encoding="utf-8").lower()
                for gone in ("stellisee", "misurina", "oeschinensee", "riffelsee-coucher", "tre cime"):
                    self.assertNotIn(gone, text, f"{f.name} : {gone}")
        masters = {p.name for p in (ROOT / "themes/weather/masters-native").glob("*.jpg")}
        self.assertLessEqual(masters, {"riffelsee-lever.jpg", "braies-matin.jpg"})   # les deux photos livrees, rien d'autre


@unittest.skipUnless(Image is not None, "numpy et Pillow requis (tools/.venv)")
class GeneratorTest(unittest.TestCase):
    def fake_pack(self, folder, version=3, drop=None):
        names = ["photo.jpg", "photo_1080.jpg", "foreground.png", "foreground_1080.png"]
        files = {}
        for n in names:
            (folder / n).write_bytes(b"x" + n.encode())
            files[n] = {"pixels": [3840, 2160], "sha256": "0" * 64, "size": len(n) + 1}
        if drop:
            (folder / drop).unlink()
        (folder / "manifest.json").write_text(json.dumps({"version": version, "files": files, "grid": [0] * (32 * 18 * 3),
                                                          "crest": [0.4] * 241, "waterline": 0.56}))

    def build(self, pack, out, *extra):
        sys.path.insert(0, str(ROOT / "tools"))
        import build_alpine_preview as b
        return b.main(["--pack-dir", str(pack), "--out", str(out), *extra])

    def test_it_builds_the_viewer_from_a_pack(self):
        with tempfile.TemporaryDirectory() as t:
            pack, out = Path(t) / "pack", Path(t) / "out"
            pack.mkdir()
            self.fake_pack(pack)
            self.assertEqual(self.build(pack, out), 0)
            self.assertTrue((out / "index.html").is_file())
            self.assertTrue((out / "pack/photo.jpg").is_file())
            data = (out / "data.js").read_text()
            self.assertTrue(data.startswith("window.ALPINE_DATA = "))
            parsed = json.loads(data[len("window.ALPINE_DATA = "):].rstrip(";\n"))
            self.assertEqual(parsed["photo"]["id"], "riffelsee-lever")
            self.assertEqual(len(parsed["pack"]["grid"]), 32 * 18 * 3)

    def test_an_incomplete_or_old_pack_is_refused(self):
        with tempfile.TemporaryDirectory() as t:
            pack, out = Path(t) / "pack", Path(t) / "out"
            pack.mkdir()
            self.fake_pack(pack, drop="foreground.png")
            with self.assertRaises(SystemExit):
                self.build(pack, out)
            self.fake_pack(pack, version=2)
            with self.assertRaises(SystemExit):
                self.build(pack, out)
            with self.assertRaises(SystemExit):
                self.build(Path(t) / "absent", out)

    def test_fetch_tool_lists_one_photo_and_never_a_thumbnail(self):
        sys.path.insert(0, str(ROOT / "tools"))
        import fetch_weather_masters as f
        self.assertEqual(list(f.PHOTOS), ["riffelsee-lever", "braies-matin"])
        self.assertEqual(f.PHOTOS["riffelsee-lever"], ("Matterhorn reflection in the Riffelsee at sunrise.jpg", "CC BY-SA 4.0"))
        self.assertEqual(f.PHOTOS["braies-matin"], ("Lago di Braies - Morning.jpg", "CC BY-SA 2.0"))
        src = (ROOT / "tools/fetch_weather_masters.py").read_text(encoding="utf-8")
        self.assertIn('"/thumb/" in url', src)
        self.assertEqual(f.plain('<a href="x">Deralpinbergsteiger &amp; co</a>'), "Deralpinbergsteiger & co")


if __name__ == "__main__":
    unittest.main()
