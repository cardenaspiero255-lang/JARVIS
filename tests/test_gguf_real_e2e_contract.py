"""Guard real Android GGUF E2E against fake/missing-model passing evidence."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "apps/android/app"


class RealGgufEvidenceContract(unittest.TestCase):
    def test_ci_requires_real_model_binary_and_computes_pinned_hash(self):
        script = (ROOT / "tools/prepare_gguf_e2e_fixture.py").read_text()
        workflow = (ROOT / ".github/workflows/android-gguf-e2e.yml").read_text()
        self.assertIn("e3131339bf4e8065265593d4fd8f7bb7ff2d3abff1edb5618aa1197b89cad9f5", script)
        self.assertIn("ggml-org/SmolLM2-135M-GGUF", script)
        self.assertIn("Apache-2.0", script)
        self.assertIn("checksum.hexdigest() != EXPECTED_SHA256", script)
        self.assertIn("prepare_gguf_e2e_fixture.py", workflow)
        self.assertIn("run_android14_gguf_e2e.sh", workflow)
        self.assertNotIn("upload-artifact", workflow.split("Acquire and hash-check official")[0])

    def test_genuine_instrumentation_is_not_skipped_or_only_missing_path(self):
        test = (APP / "src/ggufE2e/java/com/cardenaspiero255/jarvis/"
                "LocalGgufRealModelInstrumentedTest.kt").read_text()
        script = (ROOT / "apps/android/run_android14_gguf_e2e.sh").read_text()
        self.assertIn('assets.open("e2e-smollm.gguf")', test)
        self.assertIn("assertEquals(\"Byte-for-byte authentic GGUF\"", test)
        self.assertIn("inference.ask(", test)
        self.assertIn("completed.await(", test)
        self.assertIn("GGUF_E2E_REAL_GENERATION_OK", test)
        self.assertNotIn("Assume.assume", test)
        self.assertIn("GGUF_E2E_NOT_RUN", script)
        self.assertIn("GGUF_E2E_INVALID_RESULT", script)
        self.assertIn("-PggufE2e=true", script)

    def test_native_model_lease_cannot_block_ui_presence_probe(self):
        store = (APP / "src/main/java/com/cardenaspiero255/jarvis/"
                 "LocalGgufModelStore.kt").read_text()
        infer = (APP / "src/main/java/com/cardenaspiero255/jarvis/"
                 "LocalGgufInference.kt").read_text()
        self.assertIn("store.withVerifiedModelRevision { model, verifiedSha ->", infer)
        self.assertIn("@Synchronized fun <T> withVerifiedModelRevision", store)
        self.assertIn("fun hasModel(): Boolean", store)
        self.assertNotIn("@Synchronized fun hasModel()", store)
        self.assertNotIn("active?.cancel(true)", infer)
        self.assertIn("runState.finish()", infer)

    def test_product_apk_never_bundles_ci_model(self):
        gradle = (APP / "build.gradle.kts").read_text()
        manifest = (APP / "src/main/AndroidManifest.xml").read_text()
        self.assertIn('providers.gradleProperty("ggufE2e")', gradle)
        self.assertIn('android.sourceSets.getByName("androidTest").assets.srcDir', gradle)
        self.assertNotIn("e2e-smollm.gguf", (APP / "src/main/java/com/cardenaspiero255/jarvis/"
                                               "LocalGgufInference.kt").read_text())
        self.assertNotIn('android:name="android.permission.INTERNET"', manifest)


if __name__ == "__main__":
    unittest.main()
