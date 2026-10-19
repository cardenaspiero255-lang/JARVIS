"""Contracts for native sherpa-onnx integration: no mock wake authorization."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "apps/android"
APP = ANDROID / "app"
SRC = APP / "src/main/java/com/cardenaspiero255/jarvis"

class SherpaNativeWakeBridgeContract(unittest.TestCase):
    def test_exact_android_aar_is_version_pinned_and_sha_checked(self):
        settings = (ANDROID / "settings.gradle.kts").read_text()
        gradle = (APP / "build.gradle.kts").read_text()
        self.assertIn('sherpa-onnx:1.13.8@aar', gradle)
        self.assertIn('633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96', gradle)
        self.assertIn('includeGroup("com.k2fsa.sherpa.release")', settings)

    def test_real_native_kws_stream_consumes_shared_audio_and_cleans_up(self):
        bridge = (SRC / "SherpaNativeWakeBridge.kt").read_text()
        runner = (SRC / "NativeSpanishSpeechRunner.kt").read_text()
        self.assertIn("KeywordSpotter(", bridge)
        self.assertIn("acceptWaveform(", bridge)
        self.assertIn("getResult(", bridge)
        self.assertIn("KwsCandidateGate", bridge)
        self.assertIn("OfflineKwsModelGate", bridge)
        self.assertIn("stream.release()", bridge)
        self.assertIn("spotter.release()", bridge)
        self.assertIn("wakeBridge?.consume(audio, received", runner)
        self.assertIn("wakeBridge?.close()", runner)
        self.assertIn("org.vosk.Recognizer", runner)

    def test_optional_native_failure_retains_bounded_vosk_and_no_privileges(self):
        bridge = (SRC / "SherpaNativeWakeBridge.kt").read_text()
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        self.assertIn("KwsReadiness.READY_FOR_NATIVE_ADAPTER", bridge)
        self.assertIn("KwsDecision.CANDIDATE", bridge)
        self.assertIn("return null", bridge)
        self.assertNotIn("ACTION_BOOT_COMPLETED", service)
        self.assertNotIn("startActivity(", bridge)

if __name__ == "__main__":
    unittest.main()
