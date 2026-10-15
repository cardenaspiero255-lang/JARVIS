"""The private JARVIS window should hide from system screenshots by default."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class PrivateWindowSafetyContract(unittest.TestCase):
    def test_secure_window_from_creation_and_after_background(self):
        src=(BASE/"MainActivity.kt").read_text()
        self.assertIn("WindowManager.LayoutParams.FLAG_SECURE",src)
        self.assertIn("window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)",src)
        self.assertIn("private fun enforceCapturePrivacy()",src)
        self.assertIn("capturePrivacyGate.revoke()",src)
        self.assertIn("SessionLockChannel.SCREEN_CAPTURE to",src)
        self.assertIn("override fun onStop()",src)
        self.assertIn("override fun onDestroy()",src)

    def test_visible_opt_in_debug_button_and_automatic_reset(self):
        src=(BASE/"MainActivity.kt").read_text()
        self.assertIn('primaryButton("PERMITIR CAPTURAS 60 S — DEPURACIÓN")',src)
        self.assertIn('primaryButton("PROTEGER PANTALLA AHORA")',src)
        self.assertIn("capturePrivacyGate.allowForDebugByTap(SystemClock.elapsedRealtime())",src)
        self.assertIn("capturePrivacyHandler.postDelayed(",src)
        self.assertIn("capturePrivacyHandler.removeCallbacks",src)
        gate=(BASE/"core/ScreenCapturePrivacyGate.kt").read_text()
        self.assertIn("private const val MAX_WINDOW_MS = 60_000L",gate)
        self.assertNotIn("SharedPreferences",gate)
        self.assertNotIn("HttpURLConnection",gate)

    def test_security_not_relaxed_in_manifest(self):
        text=(ROOT/"apps/android/app/src/main/AndroidManifest.xml").read_text()
        self.assertIn('android:allowBackup="false"',text)
        self.assertNotIn("android.permission.INTERNET",text)

if __name__=="__main__":
    unittest.main()
