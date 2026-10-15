"""Non-executable Spanish-number parsing security contract."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis/core"

class SpokenMathSecurityContract(unittest.TestCase):
    def test_number_converter_has_no_android_or_network_authority(self):
        source = (CORE / "SpanishNumberWords.kt").read_text()
        forbidden = ("Runtime.getRuntime", "ProcessBuilder", "Intent(", "HttpURLConnection",
                     "java.net.", "SharedPreferences", "startActivity(", "sendBroadcast(",
                     "performGlobalAction(", "Class.forName")
        for snippet in forbidden:
            self.assertNotIn(snippet, source)
        self.assertIn("Unrecognized tokens are left untouched", source)

    def test_voice_remains_bounded_and_pure(self):
        gate = (CORE / "VoiceMathGate.kt").read_text()
        calculator = (CORE / "OfflineArithmetic.kt").read_text()
        self.assertIn("OfflineArithmetic.evaluate(transcript)", gate)
        self.assertIn("transcript.length > 160", gate)
        self.assertIn("SpanishNumberWords.rewrite(expr)", calculator)
        self.assertIn("!allowed.matches(expr)", calculator)
        self.assertIn("Parser(expr).evaluate()", calculator)

if __name__ == "__main__":
    unittest.main()
