"""PR27 must preserve current Android capabilities and avoid request collisions."""
from pathlib import Path
import unittest
BASE=Path(__file__).resolve().parents[1]/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
class ModernProjectionCompatibility(unittest.TestCase):
    def test_picker_and_screen_capture_request_codes_disjoint(self):
        screen=(BASE/"core/ScreenConsentRequestGate.kt").read_text()
        picker=(BASE/"core/VisionPickerRequestGate.kt").read_text()
        self.assertIn("FIRST_REQUEST_CODE = 8100",screen)
        self.assertIn("LAST_REQUEST_CODE = 8999",screen)
        self.assertIn("FIRST_REQUEST_CODE = 7002",picker)
        self.assertIn("LAST_REQUEST_CODE = 7999",picker)
        activity=(BASE/"MainActivity.kt").read_text()
        self.assertIn("screenConsentGate.consume(requestCode,",activity)
        self.assertIn("visionPickerRequest.consume(requestCode,",activity)
        self.assertIn("recognitionRequest.consume(",activity)
        projection = activity.split("if (screenConsentGate.owns(requestCode))", 1)[1].split(
            "if (visionPickerRequest.owns(requestCode))", 1)[0]
        # A callback for a previous consent must not invalidate a newer pending request.
        self.assertLess(projection.index("if (!authorized)"),
                        projection.index("screenConsentRequested = false"))

    def test_lock_revokes_projection_without_losing_other_channels(self):
        activity=(BASE/"MainActivity.kt").read_text()
        code=activity.split("private fun lockSessionByTap()",1)[1].split("private fun clearVisibleReadouts()",1)[0]
        for snippet in ("SCREEN_PROJECTION","SCREEN_CAPTURE","GAMEHUB_PREFLIGHT",
                        "LOCAL_DIAGNOSTICS","visionPickerRequest.cancel()",
                        "screenConsentGate.cancel()","ScreenObservationService.stopNow()",
                        "recognitionRequest.cancel()","voiceMathSession.clear()"):
            self.assertIn(snippet,code)
        self.assertIn("WindowManager.LayoutParams.FLAG_SECURE",activity)
        self.assertIn("savedInstanceState?.getInt(SCREEN_CONSENT_COUNTER_KEY, 0)",activity)
        self.assertIn("outState.putInt(SCREEN_CONSENT_COUNTER_KEY, screenConsentGate.issuedCount())",activity)
    def test_projection_no_remote_api_or_click_executor(self):
        service=(BASE/"ScreenObservationService.kt").read_text()
        self.assertIn("pendingStart.consume()",service)
        for forbidden in ("HttpURLConnection","INTERNET","performGlobalAction(",
                          "SharedPreferences","FileOutputStream"):
            self.assertNotIn(forbidden,service)

if __name__=="__main__":
    unittest.main()
