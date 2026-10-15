"""Security contract: Android accessibility service must not expose screen data."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / "apps/android/app/src/main"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class AccessibilityManifestSafetyTests(unittest.TestCase):
    def test_service_requires_system_bind_and_explicit_accessibility_metadata(self):
        root = ET.parse(ROOT / "AndroidManifest.xml").getroot()
        app = root.find("application")
        services = [s for s in app.findall("service") if
                    s.get(ANDROID + "name") == ".OmegaHandsService"]
        self.assertEqual(len(services), 1)
        service = services[0]
        self.assertEqual(service.get(ANDROID + "permission"),
                         "android.permission.BIND_ACCESSIBILITY_SERVICE")
        self.assertEqual(service.get(ANDROID + "exported"), "true")
        actions = [x.get(ANDROID + "name") for x in service.findall("./intent-filter/action")]
        self.assertEqual(actions, ["android.accessibilityservice.AccessibilityService"])
        metadata = service.find("meta-data")
        self.assertEqual(metadata.get(ANDROID + "name"), "android.accessibilityservice")
        self.assertEqual(metadata.get(ANDROID + "resource"), "@xml/omega_hands_service")

    def test_service_does_not_request_screen_content_or_gestures(self):
        root = ET.parse(ROOT / "res/xml/omega_hands_service.xml").getroot()
        self.assertEqual(root.get(ANDROID + "canRetrieveWindowContent"), "false")
        self.assertNotEqual(root.get(ANDROID + "canPerformGestures"), "true")
        self.assertNotIn("flagRetrieveInteractiveWindows", root.get(ANDROID + "accessibilityFlags", ""))
