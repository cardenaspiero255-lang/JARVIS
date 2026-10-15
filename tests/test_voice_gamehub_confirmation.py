"""Safety contract for a spoken GameHub launch request (no device E2E proof)."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"


class VoiceGameHubConfirmationContract(unittest.TestCase):
    def test_transcript_is_request_only_and_never_launches_directly(self):
        activity = (SRC / "MainActivity.kt").read_text()
        voice_branch = activity.split("VoiceAction.OPEN_GAMEHUB_ULTRA -> {", 1)[1].split(
            "VoiceAction.UNSUPPORTED -> {", 1
        )[0]
        self.assertIn("voiceConsent.request(", voice_branch)
        self.assertIn("VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH", voice_branch)
        self.assertNotIn("GameHubLauncher(", voice_branch)
        self.assertNotIn("launchOnTap(", voice_branch)
        self.assertNotIn("startActivity(", voice_branch)

    def test_separate_confirm_button_consumes_exact_short_lived_request(self):
        activity = (SRC / "MainActivity.kt").read_text()
        confirm = activity.split(
            'primaryButton("CONFIRMAR APERTURA DE GAMEHUB POR VOZ") {', 1
        )[1].split('primaryButton("PERFIL: ESPAÑOL LOCAL")', 1)[0]
        self.assertIn("voiceConsent.consume(", confirm)
        self.assertIn("VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH", confirm)
        self.assertIn("if (!confirmed)", confirm)
        self.assertIn("GameHubLauncher(this).launchOnTap()", confirm)
        self.assertLess(confirm.index("if (!confirmed)"), confirm.index("launchOnTap()"))

    def test_foreground_exit_revokes_the_request(self):
        activity = (SRC / "MainActivity.kt").read_text()
        self.assertIn("override fun onStop()", activity)
        self.assertIn("voiceConsent.cancel()", activity)
        gate = (SRC / "core/VoiceConsentGate.kt").read_text()
        self.assertIn("GAMEHUB_ULTRA_LAUNCH", gate)
        self.assertIn("30_000L", gate)
        launcher = (SRC / "GameHubLauncher.kt").read_text()
        self.assertIn("getLaunchIntentForPackage(policy.packageName)", launcher)
        self.assertIn("explicitUserTap = true", launcher)

    def test_only_known_exact_transcripts_are_eligible(self):
        parser = (SRC / "core/VoiceCommandParser.kt").read_text()
        self.assertIn('"jarvis abre gamehub ultra"', parser)
        self.assertIn("else -> VoiceAction.UNSUPPORTED", parser)


if __name__ == "__main__":
    unittest.main()
