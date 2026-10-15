"""Fail-closed privacy and security boundaries for user-selected screenshots."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class VisionPrivacyContracts(unittest.TestCase):
    def test_image_selection_does_not_request_camera_media_or_internet(self):
        root = ET.parse(ROOT / "apps/android/app/src/main/AndroidManifest.xml").getroot()
        grants = {p.get(ANDROID+"name") for p in root.findall("uses-permission")}
        for forbidden in ("android.permission.CAMERA", "android.permission.INTERNET",
                          "android.permission.READ_MEDIA_IMAGES",
                          "android.permission.READ_EXTERNAL_STORAGE"):
            self.assertNotIn(forbidden, grants)

    def test_input_is_user_selected_and_ocr_text_is_not_executable(self):
        activity = (ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis/MainActivity.kt").read_text()
        vision = (ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis/VisionRecognizer.kt").read_text()
        self.assertIn("Intent.ACTION_OPEN_DOCUMENT", activity)
        self.assertIn('type = "image/*"', activity)
        self.assertIn("visionRecognizer.inspectSelectedImage", activity)
        self.assertIn("TextRecognition.getClient", vision)
        self.assertIn("InputImage.fromBitmap", vision)
        self.assertIn("VisionImagePolicy", vision)
        self.assertNotIn("ProviderBridge", vision)
        self.assertNotIn("OmegaHandsService", vision)
        self.assertNotIn("startActivity(", vision)

    def test_bundled_model_and_input_caps(self):
        build = (ROOT / "apps/android/app/build.gradle.kts").read_text()
        self.assertIn('com.google.mlkit:text-recognition:16.0.1', build)
        vision = (ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis/VisionRecognizer.kt").read_text()
        self.assertIn("MAX_INPUT_BYTES", vision)
        self.assertIn("policy.acceptDimensions", vision)
        self.assertIn("bitmap.recycle()", vision)
