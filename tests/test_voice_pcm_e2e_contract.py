"""A real PCM/Vosk/native GGUF/Android offline TTS experiment is distinct from static contracts."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class VoicePcmE2eContract(unittest.TestCase):
    def test_pcm_source_and_real_android_tts_are_exercised(self):
        src = ROOT / "apps/android/app/src/ggufQwenE2e/java/com/cardenaspiero255/jarvis"
        kt = (src / "OfflineVoicePcmToTtsInstrumentedTest.kt").read_text()
        workflow = (ROOT / ".github/workflows/android-gguf-qwen-benchmark.yml").read_text()
        self.assertIn('assets.open(fixture)', kt)
        self.assertIn("Recognizer(model, 16_000.0f)", kt)
        self.assertIn("recognizer.acceptWaveForm", kt)
        self.assertIn("OfflineVoiceQuestionPolicy.extract", kt)
        self.assertIn("LocalGgufInference", kt)
        self.assertIn("TextToSpeech(", kt)
        self.assertIn("synthesizeToFile", kt)
        self.assertIn("isNetworkConnectionRequired", kt)
        self.assertIn("espeak-ng", workflow)
        self.assertIn('"¿Cuál es la capital de Chile?"', workflow)
        self.assertIn("OfflineVoiceQuestionPolicy.extract(recognized, true)", kt)
        self.assertIn("ASR_FIXTURE_MISRECOGNIZED", kt)
        self.assertIn("wake_pre_authorized=simulated", kt)
        self.assertIn("test-voice-16k-0.pcm", workflow)
        self.assertIn('test-voice-16k-$i.pcm', workflow)
        self.assertIn("syntheticSpanishPcmIsAccuratelyRecognizedOffline", kt)
        self.assertIn("assertEquals(\"cual es la capital de chile\"", kt)
        self.assertIn("ASR_FIXTURE_MISRECOGNIZED", kt)
        self.assertIn("VOICE_ASR_FIXTURE_MATCH", kt)
        self.assertIn("test-voice-16k-0.pcm", kt)
        self.assertIn("syntheticSpanishPcmIsAccuratelyRecognizedOffline", (
            ROOT / "apps/android/run_android14_qwen_benchmark.sh"
        ).read_text())
        self.assertNotIn('assertTrue("ASR missed target question content"', kt)
        self.assertIn("TTS_OFFLINE_PREFLIGHT_PASS", kt)
        self.assertIn("synthesizesRealOfflineSpanishWavBeforeModelLoad", kt)
        self.assertIn("synth.defaultEngine", kt)
        self.assertIn("TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED", kt)
        self.assertIn("RIFF", kt)
        self.assertIn("WAVE", kt)
        self.assertIn("com.reecedunn.espeak", kt)
        self.assertIn("com.reecedunn.espeak_22.apk", workflow)
        self.assertIn("0a7822fec54d7f7ae759ffedc9afeaa77f809587b171fb2101998787821ec790", workflow)
        runner = (ROOT / "apps/android/run_android14_qwen_benchmark.sh").read_text()
        self.assertIn("adb install", runner)
        self.assertIn("INSTALL_TTS_DATA", runner)
        self.assertIn("synthesizesRealOfflineSpanishWavBeforeModelLoad", runner)
        self.assertIn("adb shell svc wifi disable", runner)
        self.assertLess(runner.index("adb shell svc wifi disable"),
                        runner.index("synthesizesRealOfflineSpanishWavBeforeModelLoad"))


if __name__ == "__main__":
    unittest.main()
