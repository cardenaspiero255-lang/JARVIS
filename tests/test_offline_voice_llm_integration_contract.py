"""Regression guard for explicit offline ASR -> private GGUF -> local TTS."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
MANIFEST = ROOT / "apps/android/app/src/main/AndroidManifest.xml"


class OfflineVoiceLlmContract(unittest.TestCase):
    def test_native_vosk_question_bypass_is_only_opt_in(self):
        runner = (JAVA / "NativeSpanishSpeechRunner.kt").read_text()
        self.assertIn("allowLocalQuestions: Boolean = false", runner)
        self.assertIn("OfflineVoiceQuestionPolicy.extract(text, freshQuestionWake)", runner)
        self.assertIn("onQuestion(question)", runner)
        self.assertIn("if (allowLocalQuestions) return Recognizer(model, 16_000.0f)", runner)
        self.assertIn("responsePlayback.set(true)", runner)

    def test_service_uses_same_private_model_and_offline_tts(self):
        service = (JAVA / "HandsFreePreviewService.kt").read_text()
        self.assertIn("ACTION_START_LONG_NATIVE_QA", service)
        self.assertIn("onQuestion = { question ->", service)
        self.assertIn("LocalGgufInference", service)
        self.assertIn("speakThenStop(reply)", service)
        self.assertIn("private fun prepareTts()", service)
        self.assertIn("!it.isNetworkConnectionRequired", service)
        self.assertIn("localGgufInference?.close()", service)

    def test_screen_lock_continuation_is_bounded_and_user_reversible(self):
        service = (JAVA / "HandsFreePreviewService.kt").read_text()
        manifest = MANIFEST.read_text()
        self.assertIn('android:stopWithTask="false"', manifest)
        self.assertIn("override fun onTaskRemoved(rootIntent: Intent?)", service)
        self.assertIn("if (!extendedListening)", service)
        self.assertIn("ACTION_STOP", service)
        self.assertIn("START_NOT_STICKY", service)
        self.assertIn("30 * 60_000L", service)
        self.assertNotIn('android:name="android.permission.INTERNET"', manifest)


if __name__ == "__main__":
    unittest.main()
