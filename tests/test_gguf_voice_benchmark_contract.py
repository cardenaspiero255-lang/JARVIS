"""LLM voice security and quality-test evidence; does not fabricate ASR/TTS."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "apps/android/app"
SRC = APP / "src/main/java/com/cardenaspiero255/jarvis"


class GgufBenchmarkIntegrationContract(unittest.TestCase):
    def test_real_instrumentation_samples_five_requests_and_native_memory(self):
        test = (APP / "src/ggufE2e/java/com/cardenaspiero255/jarvis/"
                "LocalGgufRealModelInstrumentedTest.kt").read_text()
        self.assertIn('assets.open("e2e-smollm.gguf")', test)
        self.assertIn("Debug.getPss()", test)
        self.assertIn("SystemClock.elapsedRealtime()", test)
        self.assertIn("sampler.start()", test)
        self.assertIn("for (item in questions)", test)
        self.assertIn("GGUF_QA_RAW_MEASUREMENTS_COLLECTED", test)
        self.assertIn("gguf-qa-raw.json", test)
        for ident in ("chile_capital", "arithmetic", "red_planet", "computer_processor"):
            self.assertIn(ident, test)
        self.assertIn('gguf_voice_pipeline", "NOT_IMPLEMENTED"', test)

    def test_ci_uploads_only_aggregate_measurements_and_rejects_missing_data(self):
        workflow = (ROOT / ".github/workflows/android-gguf-e2e.yml").read_text()
        script = (ROOT / "apps/android/run_android14_gguf_e2e.sh").read_text()
        self.assertIn("tools/gguf_qa_report.py", workflow)
        self.assertIn("gguf-qa-results.json", script)
        self.assertIn("gguf-qa-results.md", script)
        self.assertIn("run-as com.cardenaspiero255.jarvis.handsfreepreview", script)
        self.assertIn("GGUF_E2E_NOT_RUN", script)
        self.assertIn("GGUF_E2E_INVALID_RESULT", script)
        self.assertIn("GGUF_QA_REPORT_VALIDATED", (ROOT / "tools/gguf_qa_report.py").read_text())
        self.assertNotIn("e2e-smollm.gguf", workflow.split("Upload only test reports")[1])

    def test_voice_asr_to_llm_and_tts_not_falsely_claimed(self):
        main = (SRC / "MainActivity.kt").read_text()
        voice = main.split("private fun processRecognizedSpeech(", 1)[1].split(
            "private fun openTtsSettingsByTap(", 1)[0]
        self.assertNotIn("localGgufInference.ask(", voice)
        self.assertIn("localGgufInference.ask(question)", main)
        self.assertIn("REVISAR PLAN", voice)
        self.assertIn("VoiceAction.STOP_SESSION", voice)
        policy = (SRC / "core/ChatSpeechPolicy.kt").read_text()
        self.assertIn("textForSpeech", policy)
        self.assertIn("it in fixed || spokenNumbers.matches(it)", policy)


if __name__ == "__main__":
    unittest.main()
