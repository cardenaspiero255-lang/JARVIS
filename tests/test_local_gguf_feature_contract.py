"""Architecture contracts for optional local GGUF runtime (no fake inference)."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
CORE = SRC / "core"


class GgufRuntimeContract(unittest.TestCase):
    def test_optional_import_is_private_and_does_not_ask_internet(self):
        text = (SRC / "LocalGgufModelStore.kt").read_text()
        self.assertIn("ACTION_OPEN_DOCUMENT", (SRC / "MainActivity.kt").read_text())
        self.assertIn("filesDir", text)
        self.assertIn("MessageDigest.getInstance(\"SHA-256\")", text)
        self.assertIn("GGUF", text)
        self.assertIn("deleteModel", text)
        self.assertNotIn("getExternalFilesDir", text)
        manifest = (ROOT / "apps/android/app/src/main/AndroidManifest.xml").read_text()
        self.assertNotIn('android:name="android.permission.INTERNET"', manifest)

    def test_real_native_decode_path_must_exist_but_not_fake_passing(self):
        native = (ROOT / "apps/android/app/src/main/cpp/jarvis_llm.cpp").read_text()
        kotlin = (SRC / "LocalGgufInference.kt").read_text()
        self.assertIn("llama_model_load_from_file", native)
        self.assertIn("llama_decode(", native)
        self.assertIn("llama_sampler_sample", native)
        self.assertIn("llama_token_to_piece", native)
        self.assertIn('System.loadLibrary("jarvis_llm")', kotlin)
        self.assertIn("external fun generate", kotlin)
        self.assertIn("NOT_A_TOOL", kotlin)
        self.assertNotIn("startActivity(", kotlin)

    def test_loading_and_generation_never_run_on_ui_thread(self):
        kotlin = (SRC / "LocalGgufInference.kt").read_text()
        self.assertIn("Executors.newSingleThreadExecutor", kotlin)
        self.assertIn("check(Looper.myLooper() != Looper.getMainLooper())", kotlin)
        self.assertIn("finally", kotlin)
        self.assertIn("isCancelled", kotlin)

    def test_upstream_source_is_revision_pinned_and_not_silently_changed(self):
        script = (ROOT / "tools/prepare_llama_cpp.py").read_text()
        self.assertIn("31288c9c66e59377f7788d920678a565bb89dd37", script)
        self.assertIn("rev-parse", script)
        self.assertIn("https://github.com/ggml-org/llama.cpp.git", script)
        self.assertNotIn("git checkout master", script)

if __name__ == "__main__":
    unittest.main()
