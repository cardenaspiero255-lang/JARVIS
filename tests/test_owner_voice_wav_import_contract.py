"""Android document picker import must be consented and fail-closed."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"


class OwnerWavImportContract(unittest.TestCase):
    def test_selects_exactly_three_wavs_with_explicit_android_saf_picker(self):
        activity = (SRC/"OwnerVoiceEnrollmentActivity.kt").read_text()
        for required in ("ACTION_OPEN_DOCUMENT", "CATEGORY_OPENABLE",
                         "EXTRA_ALLOW_MULTIPLE", "clipData",
                         "contentResolver.openInputStream", "OwnerVoiceWavDecoder.decode",
                         "IMPORTAR 3 WAV"):
            self.assertIn(required, activity)
        self.assertIn("selected.size != 3", activity)
        self.assertNotIn("takePersistableUriPermission(", activity)

    def test_reuses_existing_encryption_enrollment_and_revoke_gates(self):
        activity = (SRC/"OwnerVoiceEnrollmentActivity.kt").read_text()
        engine = (SRC/"SherpaSpeakerEnrollmentEngine.kt").read_text()
        for required in ("runLocalEnrollment(epoch)", "clearRecordings()",
                         "enrollmentAllowed.get()", "isActive",
                         "cancelCapture(", "FLAG_SECURE", "BORRAR PERFIL Y REVOCAR"):
            self.assertIn(required, activity)
        self.assertIn("AndroidSpeakerTemplateVault(context).save(", engine)
        self.assertIn("calibrated = false", engine)
        self.assertNotIn("SpeakerEvidence.MATCHED", activity)

    def test_real_pairwise_cosine_is_diagnostic_not_owner_permission(self):
        activity = (SRC/"OwnerVoiceEnrollmentActivity.kt").read_text()
        engine = (SRC/"SherpaSpeakerEnrollmentEngine.kt").read_text()
        self.assertIn("minimumPairwiseCosine", engine)
        self.assertIn("onDiagnostics", engine)
        self.assertIn("OwnerVoiceCosine.compare", engine)
        self.assertIn("minimumPairwiseCosine", activity)
        self.assertIn("NO autentica", activity)
        self.assertIn("calibrated = false", engine)

    def test_revocation_epoch_invalidates_late_import_and_native_enrollment(self):
        activity = (SRC/"OwnerVoiceEnrollmentActivity.kt").read_text()
        self.assertIn("sessionEpoch.incrementAndGet()", activity)
        self.assertIn("sessionEpoch.get() == epoch", activity)
        self.assertIn("runLocalEnrollment(epoch)", activity)
        self.assertIn("runLocalVerification(finalCaptured, epoch)", activity)
        self.assertIn("AtomicLong", activity)

    def test_last_enrollment_commit_is_serialized_with_revocation(self):
        engine = (SRC/"SherpaSpeakerEnrollmentEngine.kt").read_text()
        self.assertIn("ENROLLMENT_COMMIT_LOCK", engine)
        self.assertGreaterEqual(engine.count("synchronized(ENROLLMENT_COMMIT_LOCK)"), 2)
        self.assertIn("if (!isActive()", engine)
        self.assertIn("AndroidSpeakerTemplateVault(context).revoke()", engine)

    def test_no_extra_storage_permission_or_internet_requested(self):
        manifest = (ROOT/"apps/android/app/src/main/AndroidManifest.xml").read_text()
        activity = (SRC/"OwnerVoiceEnrollmentActivity.kt").read_text()
        for forbidden in ("READ_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE",
                          "WRITE_EXTERNAL_STORAGE", "READ_MEDIA_AUDIO"):
            self.assertNotIn(forbidden, manifest)
            self.assertNotIn(forbidden, activity)


if __name__ == "__main__":
    unittest.main()
