"""Guard against losing an existing integration when Omega Vision is merged."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"

class VisionGameHubCompatibility(unittest.TestCase):
    def test_user_can_choose_ocr_or_open_gamehub_from_same_activity(self):
        src = (ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis/MainActivity.kt").read_text()
        for expected in ("ANALIZAR IMAGEN SELECCIONADA", "ABRIR GAMEHUB ULTRA",
                         "PARADA DE EMERGENCIA HANDS",
                         "CONFIRMAR AJUSTES SOLICITADOS POR VOZ"):
            self.assertIn(expected, src)

    def test_package_visibility_and_mlkit_dependency_are_both_preserved(self):
        root = ET.parse(ROOT / "apps/android/app/src/main/AndroidManifest.xml").getroot()
        packages = [p.get(ANDROID+"name") for p in root.findall("./queries/package")]
        self.assertEqual(packages, ["com.cardenaspiero255.gamehubultra"])
        build = (ROOT / "apps/android/app/build.gradle.kts").read_text()
        self.assertIn("com.google.mlkit:text-recognition:16.0.1", build)
