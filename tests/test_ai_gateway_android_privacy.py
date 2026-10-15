"""Regression: legacy gateway removed, privacy and local TTS retained."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]/"apps/android/app/src/main"
SRC=ROOT/"java/com/cardenaspiero255/jarvis"

class RetiredGatewayAndroidPrivacy(unittest.TestCase):
    def test_no_app_network_permission_or_cloud_client(self):
        ns="{http://schemas.android.com/apk/res/android}"
        manifest=ET.parse(ROOT/"AndroidManifest.xml").getroot()
        grants={x.get(ns+"name") for x in manifest.findall("uses-permission")}
        self.assertNotIn("android.permission.INTERNET",grants)
        self.assertNotIn("android.permission.FOREGROUND_SERVICE_MICROPHONE",grants)
        self.assertEqual("false",manifest.find("application").get(ns+"usesCleartextTraffic"))
        for path in ("AiGatewayClient.kt","AiGatewaySettings.kt","core/AiGatewayPolicy.kt"):
            self.assertFalse((SRC/path).exists(),path)
        for path in ("VisionRecognizer.kt","OmegaHandsService.kt","ForegroundSpeechRecognizer.kt"):
            self.assertNotIn("HttpURLConnection",(SRC/path).read_text())

    def test_private_memory_independent_of_retired_pairing(self):
        store=(SRC/"PrivateConversationStore.kt").read_text()
        clean=(SRC/"RetiredCloudCredentialCleaner.kt").read_text()
        self.assertIn("AndroidKeyStore",store)
        self.assertIn('getSharedPreferences("omega_ai_gateway_v1"',clean)
        self.assertIn("deleteEntry",clean)
        self.assertNotIn('getSharedPreferences("omega_private_conversation_v1"',clean)
        self.assertIn("privateMemory.save(localDialogue.snapshotForStorage())",
                      (SRC/"MainActivity.kt").read_text())

    def test_cloud_commands_fail_closed_without_prompt_dispatch(self):
        main=(SRC/"MainActivity.kt").read_text()
        self.assertIn('message.equals("conectar ia", ignoreCase = true)',main)
        self.assertIn('message.equals("desconectar ia", ignoreCase = true)',main)
        self.assertIn("Ninguna consulta se envió a OpenAI.",main)
        self.assertNotIn("requestAiReply(",main)
        self.assertNotIn("AiGatewayClient(",main)

    def test_audio_stays_local_and_requires_explicit_action(self):
        main=(SRC/"MainActivity.kt").read_text()
        engine=(SRC/"SpokenReplyEngine.kt").read_text()
        self.assertIn("spokenReplyEngine.speakRecognizedAnswerAfterTap(",main)
        self.assertIn("spokenReplyEngine.speakChatByTap(",main)
        self.assertIn("localDialogue.replyVoice(transcript)",main)
        self.assertNotIn("speakAiReply",engine)
        self.assertNotIn("privateMemory.save(transcript)",main)

if __name__=="__main__":
    unittest.main()
