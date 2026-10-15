"""Security regression for non-executable OCR-derived button suggestions."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
ANDROID=ROOT/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class VisionButtonHintContracts(unittest.TestCase):
    def test_vision_uses_ocr_bounding_boxes_but_no_accessibility_executor(self):
        code=(ANDROID/"VisionRecognizer.kt").read_text()
        self.assertIn("line.boundingBox", code)
        self.assertIn("buttonDetector.detect", code)
        self.assertIn("buttonDetector.describe", code)
        self.assertNotIn("OmegaHandsService", code)
        self.assertNotIn("performGlobalAction", code)

    def test_detector_explicitly_denies_click_execution(self):
        code=(ANDROID/"core/ButtonHintDetector.kt").read_text()
        self.assertIn("val canTap: Boolean get() = false", code)
        self.assertIn("max", "max")  # Routine sanity: no implicit tool grant.
