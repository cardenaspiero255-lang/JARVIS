"""Gate platform speech results by one recent explicitly initiated request."""
from pathlib import Path
import unittest

JAVA=Path(__file__).resolve().parents[1]/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class VoiceRequestOneUseContract(unittest.TestCase):
    def test_visible_tap_arms_request_but_callback_consumes_once(self):
        activity=(JAVA/"MainActivity.kt").read_text()
        begin=activity.split("private fun startSpeech()",1)[1].split("@Deprecated",1)[0]
        callback=activity.split("if (requestCode != SPEECH_REQUEST) return",1)[1].split("if (resultCode != RESULT_OK)",1)[0]
        self.assertIn("recognitionRequest.begin(SystemClock.elapsedRealtime())",begin)
        self.assertIn("startActivityForResult(intent, SPEECH_REQUEST)",begin)
        self.assertIn("recognitionRequest.consume(SystemClock.elapsedRealtime())",callback)
        self.assertIn("if (!requested || !foregroundGate.canAcceptRecognitionResult())",callback)

    def test_privacy_lock_and_exit_revoke_or_expire_pending_results(self):
        activity=(JAVA/"MainActivity.kt").read_text()
        self.assertIn("recognitionRequest.cancel()",activity)
        self.assertIn("if (!recognitionRequest.isPending(SystemClock.elapsedRealtime()))",activity)
        gate=(JAVA/"core/VoiceRecognitionRequestGate.kt").read_text()
        self.assertIn("MAX_RESULT_AGE_MILLIS = 180_000L",gate)
        self.assertIn("val accepted = isPending(nowMillis)",gate)
        self.assertIn("cancel() // Reject duplicates",gate)
        for forbidden in ("SharedPreferences", "FileOutputStream", "HttpURLConnection",
                          "startActivity(", "sendBroadcast(", "performGlobalAction("):
            self.assertNotIn(forbidden,gate)

if __name__=="__main__":
    unittest.main()
