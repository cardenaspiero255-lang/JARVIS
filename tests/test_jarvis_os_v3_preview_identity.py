"""Prevent V2 branding from accidentally shipping in JARVIS OS V3 APKs."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "apps/android/app"
GRADLE = ROOT / "build.gradle.kts"
MANIFEST = ROOT / "src/main/AndroidManifest.xml"
HOME = ROOT / "src/main/java/com/cardenaspiero255/jarvis/JarvisHomeActivity.kt"


class JarvisV3IdentityTests(unittest.TestCase):
    def test_user_visible_debug_application_name_and_version_are_v3(self):
        data = GRADLE.read_text(encoding="utf-8")
        self.assertIn('resValue("string", "jarvis_application_label", "JARVIS OS V3 PREVIEW")', data)
        self.assertIn('versionNameSuffix = "-jarvis-os-v3-preview"', data)
        self.assertIn('applicationIdSuffix = ".osv2preview"', data,
                      "Keep the existing preview install identity to avoid duplicating its package")

    def test_home_candidate_and_launcher_header_show_v3(self):
        manifest = MANIFEST.read_text(encoding="utf-8")
        home = HOME.read_text(encoding="utf-8")
        self.assertIn('android:label="JARVIS OS V3"', manifest)
        self.assertIn('"HOME SHELL  /  V3  /  CUATRO MÓDULOS"', home)
        self.assertIn('"JARVIS OS V3  ·  OFFLINE  ·  FUNTOUCH INTACTO"', home)

    def test_original_funtouch_and_jarvis_omega_unchanged(self):
        data = GRADLE.read_text(encoding="utf-8")
        self.assertIn('resValue("string", "jarvis_application_label", "JARVIS OMEGA")', data)
        self.assertIn('applicationIdSuffix = ".osv2preview"', data)


if __name__ == "__main__":
    unittest.main()
