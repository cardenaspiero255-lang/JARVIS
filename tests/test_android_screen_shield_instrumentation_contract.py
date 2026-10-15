"""Ensure device-only security tests cannot be mistaken for CI execution."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
DEVICE=ROOT/"apps/android/app/src/androidTest/java/com/cardenaspiero255/jarvis/ScreenShieldInstrumentedTest.kt"

class InstrumentedScreenShieldContract(unittest.TestCase):
    def test_real_android_tests_cover_startup_consent_and_privacy_lock(self):
        src=DEVICE.read_text()
        self.assertIn("@RunWith(AndroidJUnit4::class)",src)
        self.assertIn("InstrumentationRegistry.getInstrumentation()",src)
        self.assertIn("startupKeepsAndroidScreenshotShieldEnabled",src)
        self.assertIn("userCanTemporarilyAllowScreenshotsAndReprotectImmediately",src)
        self.assertIn("privacyLockRevokesActiveDebugCapturePermission",src)
        self.assertIn("WindowManager.LayoutParams.FLAG_SECURE",src)
        self.assertIn("performClick()",src)

    def test_device_tests_do_not_assert_pixels_or_secret_logs(self):
        src=DEVICE.read_text()
        for forbidden in ("takeScreenshot()", "Log.d(", "Log.i(", "getText().toString()",
                          "HttpURLConnection", "SharedPreferences", "startRecording("):
            self.assertNotIn(forbidden,src)
        docs=(ROOT/"docs/OMEGA_SCREEN_SHIELD_DEVICE_QA.md").read_text()
        self.assertIn("connectedDebugAndroidTest",docs)
        self.assertIn("no ejecutadas",docs)
        self.assertIn("pruebas físicas",docs)

if __name__=="__main__":
    unittest.main()
