"""Android document-picker callbacks require bounded per-tap consent."""
from pathlib import Path
import unittest

APP = Path(__file__).resolve().parents[1] / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
MAIN = APP / "MainActivity.kt"
GATE = APP / "core/VisionPickerRequestGate.kt"

class VisionPickerAuthorizationContracts(unittest.TestCase):
    def test_document_picker_requires_unique_request_code_and_consumption(self):
        source = MAIN.read_text(encoding="utf-8")
        launch = source.split("private fun pickVisionImage()", 1)[1].split(
            "private fun startSpeech()", 1)[0]
        self.assertIn("visionPickerRequest.begin(", launch)
        self.assertIn("startActivityForResult(intent, requestCode)", launch)
        self.assertIn("visionPickerRequest.cancel()", launch)
        result = source.split("override fun onActivityResult(", 1)[1].split(
            "if (requestCode != SPEECH_REQUEST)", 1)[0]
        self.assertIn("visionPickerRequest.owns(requestCode)", result)
        self.assertIn("visionPickerRequest.consume(requestCode,", result)
        self.assertLess(result.index("visionPickerRequest.consume(requestCode,"),
                        result.index("visionRecognizer.inspectSelectedImage"))

    def test_privacy_lock_and_destroy_revoke_in_flight_picker(self):
        source = MAIN.read_text(encoding="utf-8")
        lock = source.split("private fun lockSessionByTap()", 1)[1].split(
            "private fun clearVisibleReadouts()", 1)[0]
        self.assertIn("visionPickerRequest.cancel()", lock)
        destroy = source.split("override fun onDestroy()", 1)[1].split(
            "override fun onResume()", 1)[0]
        self.assertIn("visionPickerRequest.cancel()", destroy)
        self.assertIn("foregroundGate.canAcceptRecognitionResult()", source)

    def test_rotation_only_restores_issued_counter_not_pending_grant(self):
        source = MAIN.read_text(encoding="utf-8")
        self.assertIn("VisionPickerRequestGate(savedInstanceState?.getInt(", source)
        self.assertIn("outState.putInt(VISION_PICKER_COUNTER_KEY, visionPickerRequest.issuedCount())", source)
        self.assertIn("private const val VISION_PICKER_COUNTER_KEY", source)
        self.assertNotIn("pendingCode", source)
        self.assertIn("visionPickerRequest.cancel()", source)

    def test_picker_gate_does_not_grant_screen_capture_or_read_permissions(self):
        gate = GATE.read_text(encoding="utf-8")
        self.assertIn("MAX_AGE_MILLIS", gate)
        self.assertIn("REQUEST_COUNT", gate)
        self.assertNotIn("startActivity(", gate)
        self.assertNotIn("MediaProjection", gate)
        self.assertNotIn("android.permission", gate)


if __name__ == "__main__":
    unittest.main()
