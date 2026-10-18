"""Native speaker extractor must feed only a consenting encrypted enrollment."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
S = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class SpeakerExtractorContracts(unittest.TestCase):
    def test_official_model_fingerprint_and_apk_packaging(self):
        script = (ROOT / "tools/prepare_speaker_model.py").read_text()
        gradle = (ROOT / "apps/android/app/build.gradle.kts").read_text()
        self.assertIn("1a331345f04805badbb495c775a6ddffcdd1a732567d5ec8b3d5749e3c7a5e4b", script)
        self.assertIn("bundleVerifiedSpeaker", gradle)
        self.assertIn("verified_sherpa_speaker", script)

    def test_real_sherpa_embedding_enrolls_in_existing_encrypted_vault(self):
        engine = (S / "SherpaSpeakerEnrollmentEngine.kt").read_text()
        self.assertIn("SpeakerEmbeddingExtractor(", engine)
        self.assertIn("SpeakerEmbeddingExtractorConfig(", engine)
        self.assertIn("SpeakerEnrollmentPolicy().create(", engine)
        self.assertIn("AndroidSpeakerTemplateVault(context).save(", engine)
        self.assertIn("consentGranted", engine)
        self.assertIn("extractor.release()", engine)
        self.assertIn("stream.release()", engine)
        self.assertIn("SpeakerEvidence.UNAVAILABLE", engine)

    def test_model_never_loaded_from_external_media_or_network_in_android(self):
        model = (S / "VerifiedSpeakerModelStore.kt").read_text()
        self.assertIn("context.noBackupFilesDir", model)
        self.assertIn("context.assets.open(", model)
        self.assertIn("MessageDigest.getInstance(\"SHA-256\")", model)
        self.assertNotIn("HttpURLConnection", model)

if __name__ == "__main__": unittest.main()
