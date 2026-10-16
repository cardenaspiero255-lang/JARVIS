"""Static guard regression: consent requests and STOP must not restart ASR."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis/MainActivity.kt"
POLICY = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis/core/ForegroundVoiceTurnPolicy.kt"

class VoiceTurnSafetyContractTest(unittest.TestCase):
    def test_privileged_turn_suspends_before_processing(self):
        src = MAIN.read_text(encoding="utf-8")
        self.assertIn("ForegroundVoiceTurnPolicy.mustSuspend(", src)
        self.assertIn("if (suspendListening) stopContinuousListening()", src)
        self.assertIn("if (!suspendListening) scheduleListeningRestart(", src)

    def test_pending_confirmation_not_revoked_by_next_turn(self):
        src = MAIN.read_text(encoding="utf-8")
        self.assertIn("private fun hasPendingVoiceConfirmation(): Boolean", src)
        self.assertGreaterEqual(src.count("if (hasPendingVoiceConfirmation())"), 2)
        self.assertIn("voiceConsent.hasPending(VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH, now)", src)
        self.assertIn("voiceConsent.hasPending(VoiceRequestedAction.ACCESSIBILITY_SETTINGS, now)", src)

    def test_stop_and_automation_are_fail_closed(self):
        src = POLICY.read_text(encoding="utf-8")
        for name in ("OPEN_ACCESSIBILITY_SETTINGS", "OPEN_GAMEHUB_ULTRA", "STOP_SESSION"):
            self.assertIn(name, src)
        self.assertIn("automationPreview || when (action)", src)

if __name__ == "__main__":
    unittest.main()
