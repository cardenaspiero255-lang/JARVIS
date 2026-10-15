"""Voice fallback is local, non-executing and never persisted."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"


class VoiceMathContractTest(unittest.TestCase):
    def test_unsupported_voice_only_routes_through_bounded_local_math_session(self):
        activity = (JAVA / "MainActivity.kt").read_text()
        gate = (JAVA / "core/VoiceMathSession.kt").read_text()
        self.assertIn("VoiceAction.UNSUPPORTED ->", activity)
        self.assertIn("voiceMathSession.answer(transcript,", activity)
        self.assertIn("OfflineArithmetic.evaluate(transcript, freshValue)", gate)
        self.assertNotIn("OmegaHandsService.goHome", gate)
        self.assertNotIn("Intent(", gate)
        self.assertNotIn("SharedPreferences", gate)
        self.assertNotIn("http", gate)
        self.assertNotIn("privateMemory.save", gate)

    def test_voice_result_does_not_save_raw_transcription(self):
        activity = (JAVA / "MainActivity.kt").read_text()
        block = activity.split("VoiceAction.UNSUPPORTED ->", 1)[1].split("\n        }", 1)[0]
        self.assertIn("voiceMathSession.answer(transcript,", block)
        self.assertNotIn("privateMemory.save", block)
        self.assertNotIn("localDialogue.reply(transcript)", block)


if __name__ == "__main__":
    unittest.main()
