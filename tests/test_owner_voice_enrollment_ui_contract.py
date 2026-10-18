"""Enrollment UI never silently records and keeps owner-only authentication disabled."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "apps/android/app/src/main"
SRC = ROOT / "java/com/cardenaspiero255/jarvis"

class EnrollmentUX(unittest.TestCase):
    def test_explicit_three_sample_screen_is_exported_false(self):
        manifest = (ROOT / "AndroidManifest.xml").read_text()
        main = (SRC / "MainActivity.kt").read_text()
        assert 'android:name=".OwnerVoiceEnrollmentActivity"' in manifest
        assert 'android:exported="false"' in manifest
        assert 'REGISTRAR MI VOZ · 3 MUESTRAS' in main

    def test_each_sample_is_separate_opt_in_and_ephemeral(self):
        activity = (SRC / "OwnerVoiceEnrollmentActivity.kt").read_text()
        policy = (SRC / "core/OwnerVoiceCapturePolicy.kt").read_text()
        for token in ("requestPermissions(", "AudioRecord(", "isChecked = false",
                      "onStop()", "cancelCapture(", "clearRecordings()", "FLAG_SECURE",
                      "no se conserva audio", "REVOKE"):
            if token == "REVOKE":
                self.assertIn("revoke()", activity)
            else:
                self.assertIn(token, activity)
        self.assertIn("40_000", policy)
        self.assertNotIn("startForegroundService(", activity)
        self.assertNotIn("INTERNET", activity)

    def test_unverified_owner_never_unlocks_commands(self):
        engine = (SRC / "SherpaSpeakerEnrollmentEngine.kt").read_text()
        self.assertIn("calibrated = false", engine)
        self.assertIn("isActive()", engine)
        self.assertIn("AndroidSpeakerTemplateVault(context).save(", engine)

if __name__ == "__main__":
    unittest.main()
