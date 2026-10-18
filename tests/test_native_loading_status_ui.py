"""Regression: the Vivo preview status must not freeze at ASR_NATIVE_LOADING."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1] / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class NativeLoadingUiContract(unittest.TestCase):
    def test_loading_status_is_refreshed_while_activity_is_foreground(self):
        activity = (ROOT / "MainActivity.kt").read_text(encoding="utf-8")
        self.assertIn("private val handsFreeUiRefresh = object : Runnable", activity)
        self.assertIn("refreshHandsFreePreviewUi()", activity)
        self.assertIn("handsFreeUiHandler.postDelayed(this, 800L)", activity)
        self.assertIn("handsFreeUiHandler.post(handsFreeUiRefresh)", activity)
        # Prevent polling from leaking into background or destroying the Activity.
        self.assertGreaterEqual(activity.count(
            "handsFreeUiHandler.removeCallbacks(handsFreeUiRefresh)"), 2)

    def test_native_loading_phases_expose_progress_without_saving_audio(self):
        store = (ROOT / "NativeSpanishModelStore.kt").read_text(encoding="utf-8")
        runner = (ROOT / "NativeSpanishSpeechRunner.kt").read_text(encoding="utf-8")
        self.assertIn("fun ensureInstalled(onStage: (String) -> Unit = {})", store)
        for code in (
            "ASR_NATIVE_MODEL_CACHED",
            "ASR_NATIVE_ASSET_VERIFY",
            "ASR_NATIVE_EXTRACTING",
            "ASR_NATIVE_MODEL_READY",
        ):
            self.assertIn('onStage("' + code + '")', store)
        self.assertIn("ensureInstalled { stage -> onStatus(stage) }", runner)
        self.assertIn('onStatus("ASR_NATIVE_DECODER_LOADING")', runner)
        self.assertIn('onStatus("ASR_NATIVE_AUDIO_OPEN")', runner)
        self.assertNotIn("HttpURLConnection", store)
        self.assertNotIn("SpeechRecognizer", runner)

if __name__ == "__main__":
    unittest.main()
