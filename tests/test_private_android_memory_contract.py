"""Static privacy regression for Android encrypted local chat."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1] / "apps/android/app/src/main"

class PrivateMemoryContracts(unittest.TestCase):
    def test_keystore_gcm_no_raw_history_and_no_backup(self):
        code=(ROOT / "java/com/cardenaspiero255/jarvis/PrivateConversationStore.kt").read_text()
        manifest=(ROOT / "AndroidManifest.xml").read_text()
        for item in ("AndroidKeyStore", "AES/GCM/NoPadding",
                     "KeyProperties.PURPOSE_ENCRYPT", "GCMParameterSpec(128, iv)",
                     "ConversationMemoryPolicy", "eraseByTap"):
            self.assertIn(item, code)
        self.assertIn('android:allowBackup="false"', manifest)

    def test_memory_cannot_be_enabled_by_voice_or_vision(self):
        core=(ROOT / "java/com/cardenaspiero255/jarvis/core/LocalDialogue.kt").read_text()
        self.assertNotIn("OmegaHandsService", core)
        self.assertNotIn("ProviderBridge", core)
        self.assertNotIn("GameHubLauncher", core)
