"""User-requested microphone onboarding; no automatic background listening or hidden system indicators."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1] / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
MAIN = ROOT / "MainActivity.kt"
ASR = ROOT / "ForegroundSpeechRecognizer.kt"
TTS = ROOT / "SpokenReplyEngine.kt"

class VoiceOnboardingContract(unittest.TestCase):
    def test_permission_prompt_once_after_ui_is_ready_without_starting_microphone(self):
        s = MAIN.read_text()
        ui = s.split("setContentView(omegaScroll)", 1)[1].split("override fun onSaveInstanceState", 1)[0]
        self.assertIn("requestMicrophoneOnFirstLaunch()", ui)
        onboarding = s.split("private fun requestMicrophoneOnFirstLaunch()", 1)[1].split(
            "private fun startSpeech()", 1)[0]
        self.assertIn("Manifest.permission.RECORD_AUDIO", onboarding)
        self.assertIn("requestPermissions(", onboarding)
        self.assertIn("getSharedPreferences(", onboarding)
        self.assertNotIn("startListening(", onboarding)
        self.assertNotIn("startSpeech()", onboarding)

    def test_one_tap_starts_recognizer_only_with_permission_and_explicit_request(self):
        activity = MAIN.read_text()
        assert "recognitionRequest.begin(SystemClock.elapsedRealtime())" in activity
        s = activity.split("private fun startSpeech()", 1)[1].split("override fun onActivityResult", 1)[0]
        self.assertIn("Manifest.permission.RECORD_AUDIO", s)
        self.assertIn("checkSelfPermission(", s)
        self.assertIn("ForegroundSpeechRecognizer(", s)
        self.assertIn("recognitionRequest.begin(SystemClock.elapsedRealtime())", s)
        self.assertIn("foregroundGate.canAcceptRecognitionResult()", activity)
        recognizer = ASR.read_text()
        self.assertIn("SpeechRecognizer.createSpeechRecognizer(", recognizer)
        self.assertIn("setRecognitionListener(", recognizer)
        self.assertIn("SpeechRecognizer.RESULTS_RECOGNITION", recognizer)
        self.assertIn("fun stop()", recognizer)
        self.assertIn("destroy()", recognizer)
        self.assertNotIn("RECORD_AUDIO", TTS.read_text())

    def test_stop_voice_button_revokes_pending_microphone_listening(self):
        activity = MAIN.read_text()
        block = activity.split('primaryButton("DETENER RESPUESTA DE VOZ")', 1)[1].split("})", 1)[0]
        self.assertIn("stopForegroundSpeech()", block)
        self.assertIn("recognitionRequest.cancel()", block)
        self.assertIn("spokenReplyEngine.stop()", block)

    def test_late_tts_initialization_cannot_rearm_after_privacy_stop(self):
        engine = TTS.read_text()
        self.assertIn("private var autoStartBlocked = false", engine)
        self.assertIn("if (ready && !autoStartBlocked) session.resume()", engine)
        stop = engine.split("fun stop()", 1)[1].split("fun close()", 1)[0]
        self.assertIn("autoStartBlocked = true", stop)

    def test_no_extra_tts_activation_or_hidden_screen_access(self):
        activity = MAIN.read_text()
        engine = TTS.read_text()
        self.assertNotIn('primaryButton("ACTIVAR RESPUESTAS DE VOZ")', activity)
        self.assertIn('primaryButton("HABLAR CON JARVIS")', activity)
        self.assertIn('primaryButton("DETENER RESPUESTA DE VOZ")', activity)
        self.assertIn('primaryButton("PROBAR AUDIO JARVIS")', activity)
        self.assertIn("session.resume()", engine)
        self.assertNotIn("startSpeech()\n        requestScreenObservation()", activity)
        self.assertIn("manager.createScreenCaptureIntent()", activity)
        self.assertIn("Settings.ACTION_ACCESSIBILITY_SETTINGS", activity)

if __name__ == "__main__":
    unittest.main()
