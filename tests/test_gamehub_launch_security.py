"""Check the GameHub Ultra integration never requests broad package access."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"

class GameHubPackageVisibilityTests(unittest.TestCase):
    def test_exact_package_visibility_not_all_packages(self):
        root = ET.parse(ROOT / "apps/android/app/src/main/AndroidManifest.xml").getroot()
        packages = [p.get(ANDROID + "name") for p in root.findall("./queries/package")]
        self.assertEqual(packages, ["com.cardenaspiero255.gamehubultra"])
        permissions = [p.get(ANDROID + "name") for p in root.findall("uses-permission")]
        self.assertNotIn("android.permission.QUERY_ALL_PACKAGES", permissions)

    def test_only_explicit_launch_from_visible_user_button(self):
        root = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
        activity = (root / "MainActivity.kt").read_text()
        launcher = (root / "GameHubLauncher.kt").read_text()
        self.assertIn('primaryButton("ABRIR GAMEHUB ULTRA")', activity)
        self.assertIn("GameHubLauncher(this).launchOnTap()", activity)
        self.assertIn("getLaunchIntentForPackage", launcher)
        self.assertIn("intent.setPackage(policy.packageName)", launcher)
        self.assertNotIn("sendBroadcast(", launcher)
        self.assertNotIn("bindService(", launcher)
