"""Contracts complement emulator proof; do not fabricate hardware observations."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "apps/android/app/src"
MAIN = APP / "main"
SRC = MAIN / "java/com/cardenaspiero255/jarvis"
ANDROID_TEST = APP / "androidTest/java/com/cardenaspiero255/jarvis"


class LockedScreenBiometricsContract(unittest.TestCase):
    def test_service_reports_only_pcm_counters_after_real_screen_sleep(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        runner = (SRC / "NativeSpanishSpeechRunner.kt").read_text()
        evidence = (SRC / "core/LockScreenAudioEvidence.kt").read_text()
        instrumentation = (ANDROID_TEST /
            "LockScreenAudioContinuityInstrumentedTest.kt").read_text()
        self.assertIn("PowerManager)?.isInteractive", service)
        self.assertIn("lockscreen_off_samples", service)
        self.assertIn("LOCKSCREEN_PCM_OBSERVED", service)
        self.assertIn("onAudioProgress", service)
        self.assertIn("onAudioProgress", runner)
        self.assertIn("pcmSamples <= 0L", evidence)
        self.assertIn("input keyevent 223", instrumentation)
        self.assertIn("!power.isInteractive", instrumentation)
        self.assertIn("SCREEN_OFF_AUDIO_OBSERVED", instrumentation)
        self.assertIn("input keyevent 224", instrumentation)
        self.assertNotIn("samples16kPcm", evidence)

    def test_no_hidden_boot_relaunch_or_biometric_owner_elevation(self):
        manifest = (MAIN / "AndroidManifest.xml").read_text()
        identity_policy = (SRC / "core/SpeakerEnrollmentPolicy.kt").read_text()
        self.assertNotIn("BOOT_COMPLETED", manifest)
        self.assertIn('android:stopWithTask="false"', manifest)
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        self.assertIn("if (!extendedListening)", service)
        self.assertIn("ACTION_STOP", service)
        self.assertIn("return SpeakerEvidence.UNCERTAIN", identity_policy)
        self.assertIn("calibrated = false",
                      (SRC / "SherpaSpeakerEnrollmentEngine.kt").read_text())

    def test_holdout_requires_real_consent_and_replay_label(self):
        harness = (ROOT / "tools/evaluate_owner_biometric_holdout.py").read_text()
        self.assertIn('("genuine", "impostor", "replay")', harness)
        self.assertIn('("calibration", "holdout")', harness)
        self.assertIn("expected_holdout_sha256", harness)
        self.assertIn('"authentication": "NOT_CERTIFIED"', harness)
        self.assertIn('row["consent"] is not True', harness)
        self.assertIn("replay_source_sha256", harness)


if __name__ == "__main__":
    unittest.main()
