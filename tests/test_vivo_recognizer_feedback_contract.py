"""Regression: ASR callbacks must drive UI feedback and cannot hang silently."""
import unittest
from pathlib import Path

SRC = Path(__file__).resolve().parents[1] / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
MAIN = SRC / "MainActivity.kt"
ASR = SRC / "ForegroundSpeechRecognizer.kt"


class VivoSpeechFeedbackContract(unittest.TestCase):
    def test_asr_level_callbacks_are_not_dropped(self):
        recognizer = ASR.read_text()
        activity = MAIN.read_text()
        self.assertIn("override fun onRmsChanged(rmsdB: Float)", recognizer)
        self.assertIn("onAudioLevel(rmsdB)", recognizer)
        self.assertIn("override fun onBeginningOfSpeech()", recognizer)
        self.assertIn("Voz detectada por Android", recognizer)
        self.assertIn("onAudioLevel = { rms ->", activity)
        self.assertIn("micMeter.progress = SpeechLevelPolicy.progress(rms)", activity)
        self.assertIn("Micrófono listo para escuchar", recognizer)
        self.assertNotIn("onBufferReceived(buffer: ByteArray?) { onAudioLevel", recognizer)

    def test_no_ready_callback_times_out_but_does_not_start_background_recording(self):
        recognizer = ASR.read_text()
        activity = MAIN.read_text()
        self.assertIn("ERROR_NO_READY_CALLBACK", recognizer)
        self.assertIn("armWatchdog(ticket, 10_000L", recognizer)
        self.assertIn("watchdogHandler.removeCallbacks", recognizer)
        self.assertIn("ForegroundSpeechRecognizer.ERROR_NO_READY_CALLBACK", activity)
        self.assertIn("foregroundGate.onStop()", activity)
        self.assertIn("stopContinuousListening()", activity)

    def test_explicit_visible_system_microphone_fallback_preserves_consent(self):
        activity = MAIN.read_text()
        self.assertIn('primaryButton("PROBAR MICRÓFONO DE ANDROID")', activity)
        self.assertIn("openAndroidSystemVoiceByTap()", activity)
        self.assertIn("recognitionRequest.begin(SystemClock.elapsedRealtime())", activity)
        self.assertIn("startActivityForResult(systemIntent, SPEECH_REQUEST)", activity)
        self.assertNotIn("MediaRecorder(", activity)
        self.assertNotIn("AudioRecord(", activity)

if __name__ == "__main__":
    unittest.main()
