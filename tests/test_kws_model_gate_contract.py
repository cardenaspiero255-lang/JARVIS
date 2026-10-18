"""External KWS assets stay opt-in, checksum pinned, and cannot authorize actions."""
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
class KwsContract(unittest.TestCase):
    def test_assets_are_offline_allowlisted_and_kws_is_not_implicitly_enabled(self):
        gate = (ROOT / "core/OfflineKwsModelGate.kt").read_text()
        runner = (ROOT / "NativeSpanishSpeechRunner.kt").read_text()
        self.assertIn('MessageDigest.getInstance("SHA-256")', gate)
        self.assertIn("Files.isSymbolicLink(file.toPath())", gate)
        self.assertIn("nativeDetected: Boolean", gate)
        self.assertIn("sessionGranted: Boolean", gate)
        self.assertIn("KwsDecision.CANDIDATE", gate)
        self.assertNotIn("HttpURLConnection", gate)
        self.assertNotIn("getSystemService", gate)
        # No JNI runtime or model has been configured; preserve Vosk, not fake KWS.
        self.assertIn("org.vosk.Recognizer", runner)
        self.assertNotIn("SherpaOnnx", runner)
if __name__ == "__main__": unittest.main()
