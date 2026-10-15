"""Regression guard: untrusted recognition cannot directly launch settings."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1] / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"


class VoiceIntentSafetyContract(unittest.TestCase):
    def test_spoken_settings_requires_explicit_button_confirmation(self):
        source = (ROOT / "MainActivity.kt").read_text(encoding="utf-8")
        branch = source.split("VoiceAction.OPEN_ACCESSIBILITY_SETTINGS ->", 1)[1].split(
            "VoiceAction.UNSUPPORTED ->", 1)[0]
        self.assertIn("voiceConsent.request(", branch)
        self.assertNotIn("openAccessibilitySettings(", branch)
        self.assertIn('primaryButton("CONFIRMAR AJUSTES SOLICITADOS POR VOZ")', source)
        self.assertIn("voiceConsent.consume(", source)

    def test_voice_request_is_revoked_at_foreground_loss(self):
        source = (ROOT / "MainActivity.kt").read_text(encoding="utf-8")
        on_stop = source.split("override fun onStop()", 1)[1].split(
            "override fun onDestroy()", 1)[0]
        self.assertIn("voiceConsent.cancel()", on_stop)

    def test_speaking_is_stopped_before_external_recognition(self):
        source = (ROOT / "MainActivity.kt").read_text(encoding="utf-8")
        start = source.split("private fun startSpeech()", 1)[1].split(
            'Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)', 1)[0]
        self.assertIn("spokenReplyEngine.stop()", start)
