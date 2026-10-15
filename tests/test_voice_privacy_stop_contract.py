"""Voice emergency stop must use the same foreground, one-shot privacy lock."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1] / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"


class VoicePrivacyStopContract(unittest.TestCase):
    def test_only_consumed_foreground_speech_dispatches_emergency_stop(self):
        main = (ROOT / "MainActivity.kt").read_text(encoding="utf-8")
        callback = main.split("override fun onActivityResult(", 1)[1]
        before_dispatch = callback.split("when (action) {", 1)[0]
        self.assertIn("recognitionRequest.consume(", before_dispatch)
        self.assertIn("foregroundGate.canAcceptRecognitionResult()", before_dispatch)
        branch = callback.split("VoiceAction.STOP_SESSION ->", 1)[1].split(
            "VoiceAction.UNSUPPORTED ->", 1)[0]
        self.assertIn("lockSessionByTap()", branch)
        self.assertNotIn("startSpeech(", branch)
        self.assertNotIn("startActivity(", branch)

    def test_emergency_stop_is_same_multichannel_revocation_as_visible_button(self):
        main = (ROOT / "MainActivity.kt").read_text(encoding="utf-8")
        button = main.split('primaryButton("BLOQUEAR SESIÓN — PRIVACIDAD")', 1)[1]
        self.assertIn("lockSessionByTap()", button.split("})", 1)[0])
        lock = main.split("private fun lockSessionByTap()", 1)[1].split(
            "private fun clearVisibleReadouts()", 1)[0]
        for name in ("VOICE_REQUEST", "SPOKEN_REPLY", "IMAGE_OCR", "HANDS",
                     "PRIVATE_MEMORY", "LOCAL_DIAGNOSTICS", "SCREEN_CAPTURE",
                     "GAMEHUB_PREFLIGHT", "LOCAL_CHAT"):
            self.assertIn("SessionLockChannel." + name, lock)
        self.assertIn("SessionLockCoordinator().lock(actions)", lock)

    def test_parser_does_not_grant_remote_or_device_execution(self):
        parser = (ROOT / "core/VoiceCommandParser.kt").read_text(encoding="utf-8")
        self.assertIn("STOP_SESSION", parser)
        self.assertIn("else -> VoiceAction.UNSUPPORTED", parser)


if __name__ == "__main__":
    unittest.main()
