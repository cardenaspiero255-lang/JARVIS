"""Owner speaker templates must be encrypted, model-bound, revocable and fail closed."""
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
class VoiceMatchVaultContract(unittest.TestCase):
    def test_keystore_and_model_binding(self):
        vault = (ROOT / "AndroidSpeakerTemplateVault.kt").read_text()
        policy = (ROOT / "core/SpeakerEnrollmentPolicy.kt").read_text()
        self.assertIn("AndroidKeyStore", vault)
        self.assertIn("AES/GCM/NoPadding", vault)
        self.assertIn("context.noBackupFilesDir", vault)
        self.assertIn("cipher.updateAAD(modelSha256.toByteArray", vault)
        self.assertIn("fun revoke()", vault)
        self.assertIn("SpeakerEvidence.UNAVAILABLE", policy)
        self.assertIn("SpeakerEvidence.UNCERTAIN", policy)
        self.assertNotIn("SpeakerEvidence.MATCHED", policy)
        self.assertNotIn("RECORD_AUDIO", vault)
if __name__ == "__main__": unittest.main()
