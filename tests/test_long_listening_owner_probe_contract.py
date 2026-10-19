"""Regression checks: user-initiated extended on-device listening and owner probe.

Contracts prohibit an unconsented boot listener and an uncalibrated biometric
MATCHED decision. Existing 60-second diagnostics remain separate.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "apps/android/app/src/main"
SRC = BASE / "java/com/cardenaspiero255/jarvis"


class LongListeningOwnerProbeContract(unittest.TestCase):
    def test_long_session_is_separate_explicit_action(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        ui = (SRC / "MainActivity.kt").read_text()
        manifest = (BASE / "AndroidManifest.xml").read_text()
        self.assertIn("ACTION_START_LONG_NATIVE", service)
        self.assertIn("ACTION_START_LONG_NATIVE", ui)
        self.assertIn("30 * 60_000L", service)
        self.assertIn("EXTENDED_LISTENING", service)
        self.assertIn("DETENER", service)
        self.assertIn("FOREGROUND_SERVICE_TYPE_MICROPHONE", service)
        self.assertNotIn("BOOT_COMPLETED", manifest)
        # Swiping away the task must revoke the microphone (not a lockscreen event).
        self.assertIn('android:stopWithTask="true"', manifest)

    def test_repeated_voice_turns_do_not_end_extended_session(self):
        runner = (SRC / "NativeSpanishSpeechRunner.kt").read_text()
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        self.assertIn("continuous: Boolean", runner)
        self.assertIn("sessionDurationMillis: Long", runner)
        self.assertIn("setResponsePlayback", runner)
        self.assertIn("nativeRunner?.setResponsePlayback", service)
        self.assertIn("continuous = extendedListening", service)

    def test_voice_echo_and_pending_tts_timeout_are_bounded(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        enrollment = (SRC / "OwnerVoiceEnrollmentActivity.kt").read_text()
        self.assertIn('TTS_INIT_TIMEOUT', service)
        self.assertIn('350L', service)
        self.assertIn('pendingSpeech = null', service)
        self.assertIn('verifyingOnly = false // Permission flow', enrollment)
        self.assertIn('PROBAR MI VOZ otra vez', enrollment)

    def test_real_owner_probe_never_grants_owner_authority(self):
        engine = (SRC / "SherpaOwnerVoiceProbeEngine.kt").read_text()
        enrollment = (SRC / "OwnerVoiceEnrollmentActivity.kt").read_text()
        self.assertIn("SpeakerEmbeddingExtractor(", engine)
        self.assertIn("AndroidSpeakerTemplateVault", engine)
        self.assertIn("cosine", engine.lower())
        self.assertIn("embedding.fill(0f)", engine)
        self.assertIn("PROBAR MI VOZ", enrollment)
        self.assertIn("probe(", enrollment)
        self.assertNotIn("SpeakerEvidence.MATCHED", engine)
        self.assertNotIn("ACTION_BOOT_COMPLETED", engine)


if __name__ == "__main__":
    unittest.main()
