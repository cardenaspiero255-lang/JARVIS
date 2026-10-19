"""Bounded, real-weight Qwen experiment: not a physical voice certificate."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class QwenBenchmarkContract(unittest.TestCase):
    def test_separate_pinned_weights_only_in_android_test(self):
        source = (ROOT / "tools/prepare_gguf_qwen_benchmark.py").read_text()
        script = (ROOT / "apps/android/run_android14_qwen_benchmark.sh").read_text()
        gradle = (ROOT / "apps/android/app/build.gradle.kts").read_text()
        self.assertIn("74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db", source)
        self.assertIn("Qwen/Qwen2.5-0.5B-Instruct-GGUF", source)
        self.assertIn("MAX_SIZE", source)
        self.assertIn("ggufQwenE2e", gradle)
        self.assertIn("instruct-qwen.gguf", script)
        self.assertIn("adb shell svc wifi disable", script)
        self.assertIn("QWEN_BENCH_CASE", script)
        self.assertIn("semantic_certified", script)
        self.assertIn("voice_e2e_verified", script)
        self.assertNotIn("android.permission.INTERNET", (
            ROOT / "apps/android/app/src/main/AndroidManifest.xml"
        ).read_text())

    def test_real_qwen_inference_yields_separate_cold_metrics(self):
        kotlin = (ROOT /
                  "apps/android/app/src/ggufQwenE2e/java/com/cardenaspiero255/jarvis/LocalQwenBenchmarkInstrumentedTest.kt"
                  ).read_text()
        self.assertIn("LocalGgufInference(store)", kotlin)
        self.assertIn("QWEN_BENCH_CASE", kotlin)
        self.assertIn("getPss", kotlin)
        self.assertIn("assertEquals(SHA", kotlin)
        self.assertIn("SystemClock.elapsedRealtime()", kotlin)
        self.assertIn("OfflineVoiceQuestionPolicy.extract", kotlin)


if __name__ == "__main__":
    unittest.main()
