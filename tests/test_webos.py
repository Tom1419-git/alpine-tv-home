"""L'appli LG webOS : le dossier est complet, le moteur se charge, l'installeur fait ce qu'il dit sans toucher la TV."""
import json
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "webos/app"
NODE = shutil.which("node")


class WebosAppTest(unittest.TestCase):
    def test_appinfo_et_fichiers(self):
        info = json.loads((APP / "appinfo.json").read_text(encoding="utf-8"))
        self.assertEqual(info["type"], "web")
        self.assertEqual(info["id"], "com.tom1419.alpine")
        for name in (info["main"], info["icon"], info["largeIcon"], "main.js", "config.js", "layout.js", "grade.js", "scene.js",
                     "data.js", "images.js", "fonts/outfit.ttf"):
            self.assertTrue((APP / name).is_file(), name)
        html = (APP / "index.html").read_text(encoding="utf-8")
        for src in re.findall(r'<script src="([^"]+)"', html):
            self.assertTrue((APP / src).is_file(), src)

    def test_pluie_et_neige_animees_a_part(self):
        # la scene est rendue une fois par minute sans meteo ; les gouttes et flocons sont dessines a part, sur leur canevas.
        main = (APP / "main.js").read_text(encoding="utf-8")
        self.assertIn("noWeather: true", main)
        self.assertIn("S.overlay(", main)
        self.assertIn("requestAnimationFrame", main)
        self.assertIn('id="fx"', (APP / "index.html").read_text(encoding="utf-8"))
        self.assertIn("overlay: overlay", (APP / "scene.js").read_text(encoding="utf-8"))

    def test_es5_pour_webos_4(self):
        # webOS 4 embarque Chromium 53 : ni fleches, ni let/const, ni async dans les fichiers ecrits pour l'appli.
        src = (APP / "main.js").read_text(encoding="utf-8")
        for bad in ("=>", "async ", "await ", "let ", "const ", "`"):
            self.assertNotIn(bad, src, bad)

    def test_images_en_data_uri(self):
        # Une image servie en file:// interdit la lecture des pixels du canvas : elles doivent etre integrees.
        head = (APP / "images.js").read_bytes()[:200]
        self.assertIn(b"data:image/jpeg;base64,", head)
        for photo in (b"riffelsee-lever", b"braies-matin"):
            self.assertIn(photo, (APP / "images.js").read_bytes())
            self.assertIn(photo, (APP / "data.js").read_bytes())

    def test_les_moteurs_sont_ceux_de_l_apercu(self):
        for name in ("layout.js", "grade.js", "scene.js"):
            self.assertEqual((APP / name).read_bytes(), (ROOT / "tools/alpine_preview" / name).read_bytes(), name)


@unittest.skipUnless(NODE, "node n'est pas installe")
class InstallScriptTest(unittest.TestCase):
    def run_install(self, *extra):
        return subprocess.run([NODE, str(ROOT / "webos/install.js"), "--dry-run", "--yes", *extra], capture_output=True, text=True,
                              timeout=60)

    def test_syntaxe(self):
        r = subprocess.run([NODE, "--check", str(ROOT / "webos/install.js")], capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stderr)

    def test_installation_a_blanc(self):
        before = (APP / "config.js").read_text(encoding="utf-8")
        r = self.run_install("--action", "install", "--ip", "192.168.1.50", "--lat", "46.516", "--lon", "6.63", "--city", "Lausanne")
        self.assertEqual(r.returncode, 0, r.stderr)
        for expected in ("ares-setup-device --add lgtv", "host=192.168.1.50", "ares-novacom --device lgtv --getkey", "ares-package",
                         "ares-install --device lgtv", "ares-launch --device lgtv com.tom1419.alpine"):
            self.assertIn(expected, r.stdout)
        self.assertEqual(before, (APP / "config.js").read_text(encoding="utf-8"), "un essai a blanc ne doit rien ecrire")

    def test_ip_invalide_refusee(self):
        r = self.run_install("--action", "install", "--ip", "999.1.1.1", "--lat", "46", "--lon", "6")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("--ip", r.stderr)

    def test_option_inconnue(self):
        self.assertEqual(self.run_install("--nope").returncode, 2)


if __name__ == "__main__":
    unittest.main()
