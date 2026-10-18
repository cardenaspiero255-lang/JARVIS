"""Early Vosk partial text is a status-only signal, never a voice command."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class EarlyWakeContract(unittest.TestCase):
    def test_early_wake_only_status_without_action(self):
        runner = (ROOT / "NativeSpanishSpeechRunner.kt").read_text()
        policy = (ROOT / "core/PartialWakeSignalPolicy.kt").read_text()
        self.assertIn("PartialWakeSignalPolicy()", runner)
        self.assertIn("recognizer.partialResult", runner)
        self.assertIn("ASR_NATIVE_WAKE_CANDIDATE", runner)
        self.assertIn("lastPartialProbe >= 400L", runner)
        self.assertIn("earlyWake.onUtteranceCompleted()", runner)
        self.assertIn("normalized != \"jarvis\"", policy)
        self.assertIn("announced = true", policy)
        # Real commands continue through final / end-of-utterance checks.
        self.assertIn("turnGate.observe(", runner)
        self.assertIn("recognizer.acceptWaveForm(audio, received)", runner)

if __name__ == "__main__":
    unittest.main()
