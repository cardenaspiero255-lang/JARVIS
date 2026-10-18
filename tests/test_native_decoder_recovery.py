"""Android Vosk recovery and energy regression contract; no physical device claims."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class NativeAudioReliabilityContracts(unittest.TestCase):
    def test_native_decoder_recovery_only_once_offline(self):
        runner = (ROOT / "NativeSpanishSpeechRunner.kt").read_text()
        safety = (ROOT / "core/NativeDecoderRetryPolicy.kt").read_text()
        self.assertIn("catch (initial: IOException)", runner)
        self.assertIn("NativeDecoderRetryPolicy()", runner)
        self.assertIn('forceRefresh = true, isActive = modelReady', runner)
        self.assertIn('ASR_NATIVE_DECODER_REBUILD', runner)
        self.assertIn('ASR_NATIVE_DECODER_RETRY', runner)
        self.assertIn("cause !is IOException", safety)
        self.assertIn("if (used || !isActive", safety)
        self.assertNotIn("HttpURLConnection", runner)
        self.assertNotIn("SpeechRecognizer.create", runner)

    def test_cancel_loading_before_writing_or_activating_stage(self):
        store = (ROOT / "NativeSpanishModelStore.kt").read_text()
        self.assertIn("fun requireActive()", store)
        self.assertIn("Thread.currentThread().isInterrupted", store)
        self.assertGreaterEqual(store.count("requireActive()"), 7)
        self.assertIn("forceRefresh: Boolean = false", store)

    def test_blocking_audio_read_avoids_25ms_poll_loop(self):
        runner = (ROOT / "NativeSpanishSpeechRunner.kt").read_text()
        self.assertIn("AudioRecord.READ_BLOCKING", runner)
        self.assertNotIn("AudioRecord.READ_NON_BLOCKING", runner)
        self.assertNotIn("Thread.sleep(25L)", runner)

if __name__ == "__main__":
    unittest.main()
