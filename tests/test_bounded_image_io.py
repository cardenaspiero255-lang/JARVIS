"""OCR input should be uniformly bounded across read, skip and reset."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis/core"
VISION = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis/VisionRecognizer.kt"

class BoundedImageIOContracts(unittest.TestCase):
    def test_ocr_uses_hardened_byte_budget_for_both_decode_passes(self):
        source = VISION.read_text(encoding="utf-8")
        self.assertIn("BoundedImageInputStream(input)", source)
        self.assertEqual(source.count("BoundedImageInputStream(input)"), 2)
        self.assertNotIn("BoundedInputStream(", source)

    def test_hardened_reader_accounts_for_skip_and_never_refills_on_reset(self):
        source = (CORE / "BoundedImageInputStream.kt").read_text(encoding="utf-8")
        self.assertIn("override fun skip(", source)
        self.assertIn("override fun read()", source)
        self.assertIn("override fun read(", source)
        self.assertIn("MAX_INPUT_BYTES", source)
        self.assertNotIn("consumed = marked", source)

if __name__ == "__main__":
    unittest.main()
