"""Fixed grammar only restricts an on-device recognizer when supported."""
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class NativeCommandGrammarContract(unittest.TestCase):
    def test_grammar_supported_or_unrestricted_offline_fallback(self):
        runner = (ROOT / "NativeSpanishSpeechRunner.kt").read_text()
        policy = (ROOT / "core/OfflineCommandGrammarPolicy.kt").read_text()
        self.assertIn('File(folder, "graph/words.txt")', runner)
        self.assertIn("Recognizer(model, 16_000.0f, grammar)", runner)
        self.assertIn("return Recognizer(model, 16_000.0f)", runner)
        self.assertIn("ASR_NATIVE_COMMAND_GRAMMAR_FALLBACK", runner)
        self.assertIn("ASR_NATIVE_COMMAND_VOCAB_UNAVAILABLE", runner)
        self.assertIn('if ("jarvis" !in present)', policy)
        self.assertIn('"[unk]"', policy)
        self.assertNotIn("HttpURLConnection", runner)
        self.assertNotIn("SpeechRecognizer.create", runner)

    def test_only_completed_utterances_trigger_commands(self):
        runner = (ROOT / "NativeSpanishSpeechRunner.kt").read_text()
        self.assertIn("recognizer.acceptWaveForm(audio, received)", runner)
        self.assertIn("turnGate.observe(", runner)
        self.assertIn("SpeakerEvidence.UNAVAILABLE", runner)

if __name__ == "__main__":
    unittest.main()
