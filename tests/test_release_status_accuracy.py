"""Prevent stale documentation from claiming no Android shell or certified SSS+."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis/MainActivity.kt"

class ReleaseStateAccuracyContracts(unittest.TestCase):
    def test_ui_labels_sss_plus_as_unverified_goal(self):
        src = ANDROID.read_text()
        self.assertIn("OBJETIVO SSS+ | PROTOTIPO ANDROID", src)
        self.assertIn("SIN CERTIFICACIÓN", src)
        self.assertNotIn('"SSS+  |  NÚCLEO ANDROID 0.1"', src)

    def test_readme_covers_implemented_android_and_remaining_device_tests(self):
        src = (ROOT / "README.md").read_text()
        self.assertIn("Android", src)
        self.assertIn("Omega Voice", src)
        self.assertIn("Omega Performance", src)
        self.assertIn("PR #16", src)
        self.assertIn("pruebas físicas", src)
        self.assertNotIn("No real Sentinel/GameHub IPC,\nAndroid runtime", src)

    def test_release_status_does_not_deny_existing_android_app(self):
        src = (ROOT / "docs/IMPLEMENTATION_STATUS.md").read_text()
        self.assertIn("Android implementado, no certificado", src)
        self.assertIn("GameHub Ultra", src)
        self.assertIn("Ultra Sentinel", src)
        self.assertIn("pruebas instrumentadas en dispositivo", src)
        self.assertNotIn("There is no JARVIS Android runtime", src)

if __name__ == "__main__":
    unittest.main()
