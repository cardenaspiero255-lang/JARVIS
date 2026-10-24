"""Keep the string-result bridge in sync; no model/native code is executed."""
from pathlib import Path
import re
import unittest


MAIN = Path(__file__).resolve().parents[1] / "apps/android/app/src/main"
SRC = MAIN / "java/com/cardenaspiero255/jarvis"


class GgufFailureSentinelContract(unittest.TestCase):
    def test_every_native_and_adapter_error_is_covered_by_the_shared_filter(self):
        cpp = (MAIN / "cpp/jarvis_llm.cpp").read_text()
        adapter = (SRC / "LocalGgufInference.kt").read_text()
        policy = (SRC / "core/OfflineVoiceQuestionGate.kt").read_text()
        prefixes_section = policy.split("private val failurePrefixes = listOf(", 1)[1]
        prefixes = re.findall(r'"([^"\n]+)"', prefixes_section.split("\n    )", 1)[0])
        failures = re.findall(r'(?:return|output =|make_java_string\(env,)\s*"([^"\n]+)"', cpp)
        failures += re.findall(r'(?:var result =|result =|ifBlank \{)\s*"([^"\n]+)"', adapter)
        self.assertGreaterEqual(len(set(failures)), 19)
        for failure in failures:
            with self.subTest(failure=failure):
                self.assertTrue(any(failure.casefold().startswith(p.casefold()) for p in prefixes),
                                f"Unclassified failure sentinel: {failure}")

    def test_filter_remains_shared_by_speech_and_diagnostic_receipts(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        probe = (SRC / "core/VivoGgufProbePolicy.kt").read_text()
        self.assertIn("OfflineVoiceAnswerPolicy.forTts(response)", service)
        self.assertIn("OfflineVoiceAnswerPolicy.forTts(response)", probe)
        self.assertIn("if (safeReply == null)", service)
        self.assertLess(service.index("if (safeReply == null)"),
                        service.index('putInt("voice_gguf_success_count"'))


if __name__ == "__main__":
    unittest.main()
