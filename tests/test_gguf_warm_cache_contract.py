"""Regression gate: native weights persist ONLY while same verified GGUF is selected.

This static architecture test complements the authentic Android-14 cache-hit
instrumentation, which asserts native load/hit counts from real inference.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"


class WarmModelContract(unittest.TestCase):
    def test_cache_bound_to_verified_sha_and_not_only_the_filename(self):
        cpp = (ROOT / "apps/android/app/src/main/cpp/jarvis_llm.cpp").read_text()
        kotlin = (JAVA / "LocalGgufInference.kt").read_text()
        store = (JAVA / "LocalGgufModelStore.kt").read_text()
        self.assertIn("cached_revision", cpp)
        self.assertIn("cached_path", cpp)
        self.assertIn("llama_model_free(cached_model)", cpp)
        self.assertIn("generateCached", kotlin)
        self.assertIn("withVerifiedModelRevision", store)
        self.assertIn("MAX_NEW_TOKENS", kotlin)
        self.assertIn("runState.finish()", kotlin)

    def test_import_and_delete_release_cached_weights_after_running_inference(self):
        kotlin = (JAVA / "LocalGgufInference.kt").read_text()
        main = (JAVA / "MainActivity.kt").read_text()
        self.assertIn("releaseCachedModel()", kotlin)
        self.assertIn("fun deleteModel(", kotlin)
        self.assertIn("fun importModel(", kotlin)
        self.assertIn("mutationPending", kotlin)
        self.assertIn("localGgufInference.deleteModel(", main)
        self.assertIn("localGgufInference.importModel(", main)
        self.assertNotIn("modelImportWorker.submit {", main)

    def test_real_instrumentation_must_prove_native_cache_hit(self):
        e2e = (ROOT / "apps/android/app/src/ggufE2e/java/com/cardenaspiero255/jarvis/"
               "LocalGgufRealModelInstrumentedTest.kt").read_text()
        self.assertIn("nativeCacheCounters()", e2e)
        self.assertIn('assertEquals("One verified native load",', e2e)
        self.assertIn('assertTrue("Repeated authentic queries hit the warm model",', e2e)


if __name__ == "__main__":
    unittest.main()
