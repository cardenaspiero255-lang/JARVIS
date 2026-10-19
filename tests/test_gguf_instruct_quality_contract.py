"""Guard real licensed Instruct model evaluation from skipped/fabricated metrics."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "apps/android/app"
SHA = "ed5fa30c487b282ec156c29062f1222e5c20875a944ac98289dbd242e947f747"


class InstructQualityContracts(unittest.TestCase):
    def test_sources_and_runtime_use_same_pinned_revision(self):
        fixture = (ROOT / "tools/prepare_gguf_instruct_e2e_fixture.py").read_text()
        policy = (APP / "src/main/java/com/cardenaspiero255/jarvis/core/"
                  "LocalGgufPromptFormatter.kt").read_text()
        test = (APP / "src/ggufInstructE2e/java/com/cardenaspiero255/jarvis/"
                "LocalGgufInstructInstrumentedTest.kt").read_text()
        for text in (fixture, policy, test):
            self.assertIn(SHA, text)
        self.assertIn("SHA256", fixture)
        self.assertIn("apache-2.0", fixture)
        self.assertIn("LocalGgufPromptFormatter.format(question, verifiedSha)",
                      (APP / "src/main/java/com/cardenaspiero255/jarvis/"
                       "LocalGgufInference.kt").read_text())

    def test_e2e_uses_real_model_without_passing_blank_or_error_text(self):
        test = (APP / "src/ggufInstructE2e/java/com/cardenaspiero255/jarvis/"
                "LocalGgufInstructInstrumentedTest.kt").read_text()
        script = (ROOT / "apps/android/run_android14_instruct_qa.sh").read_text()
        self.assertIn('assets.open("instruct-smollm.gguf")', test)
        self.assertIn("inference.ask(case.second)", test)
        self.assertIn("completed.await(", test)
        self.assertIn("INSTRUCT_QUALITY_CASE", test)
        self.assertIn("INSTRUCT_E2E_NOT_RUN", script)
        self.assertIn("INSTRUCT_QUALITY_INCOMPLETE", script)
        self.assertIn("NOT_CERTIFIED", script)
        self.assertNotIn("Assume.assume", test)

    def test_weights_not_shipped_with_production_app(self):
        gradle = (APP / "build.gradle.kts").read_text()
        flow = (ROOT / ".github/workflows/android-gguf-instruct-qa.yml").read_text()
        manifest = (APP / "src/main/AndroidManifest.xml").read_text()
        self.assertIn('providers.gradleProperty("ggufInstructE2e")', gradle)
        self.assertIn('android.sourceSets.getByName("androidTest").assets.srcDir', gradle)
        self.assertIn("run_android14_instruct_qa.sh", flow)
        self.assertNotIn("instruct-smollm.gguf", flow.split(
            "Preserve only structured quality proxy and test reports")[1])
        self.assertNotIn('android:name="android.permission.INTERNET"', manifest)


if __name__ == "__main__":
    unittest.main()
