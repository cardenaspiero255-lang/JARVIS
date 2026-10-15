"""Privacy-lock invariants: visible, local, no silent deletion or re-arm."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis/MainActivity.kt"
POLICY=ROOT/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis/core/SessionLockCoordinator.kt"

class PrivacyLockContract(unittest.TestCase):
    def test_visible_button_alone_invokes_privacy_lock(self):
        src=APP.read_text()
        self.assertIn('primaryButton("BLOQUEAR SESIÓN — PRIVACIDAD")',src)
        self.assertIn("private fun lockSessionByTap()",src)
        self.assertIn("lockSessionByTap()",src)

    def test_lock_covers_every_local_module_and_does_not_erase_disk(self):
        src=APP.read_text().split("private fun lockSessionByTap()",1)[1].split(
            "private fun handsStatus()",1)[0]
        for snippet in ("voiceConsent.cancel()", "spokenReplyEngine.stop()",
                        "visionRecognizer.cancel()", "OmegaHandsService.setEmergencyStop(this, true)",
                        "privateMemory.stop()", "localDialogue.clear()", "lastChatReply = null",
                        "SessionLockCoordinator().lock(actions)"):
            self.assertIn(snippet,src)
        for forbidden in ("privateMemory.eraseByTap()", "enableByTap()", "setEmergencyStop(this, false)",
                          "GameHubLauncher", "performGlobalAction(", "startSpeech()"):
            self.assertNotIn(forbidden,src)

    def test_failed_steps_are_visible_and_cannot_short_circuit(self):
        src=POLICY.read_text()
        self.assertIn("for (channel in SessionLockChannel.values())",src)
        self.assertIn("catch (_: Exception)",src)
        self.assertIn("SessionLockReport(failures)",src)
        self.assertIn("failures.add(channel)",src)
        self.assertNotIn("return SessionLockReport(emptySet())",src)

if __name__=="__main__":
    unittest.main()
