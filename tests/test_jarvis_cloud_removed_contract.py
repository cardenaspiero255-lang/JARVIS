"""Contract: JARVIS Android has no outbound paid OpenAI route."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
MANIFEST=ROOT/"apps/android/app/src/main/AndroidManifest.xml"

class NoPaidCloudInAndroid(unittest.TestCase):
    def test_manifest_grants_no_internet(self):
        ns="{http://schemas.android.com/apk/res/android}"
        manifest=ET.parse(MANIFEST).getroot()
        permissions={x.get(ns+"name") for x in manifest.findall("uses-permission")}
        self.assertNotIn("android.permission.INTERNET",permissions)
        self.assertIn("android.permission.RECORD_AUDIO",permissions)
        self.assertIn("android.permission.FOREGROUND_SERVICE_MICROPHONE",permissions)
        self.assertEqual("false",manifest.find("application").get(ns+"usesCleartextTraffic"))

    def test_no_old_paid_api_client_or_model_strings(self):
        for path in ("AiGatewayClient.kt","AiGatewaySettings.kt",
                     "core/AiGatewayPolicy.kt","core/OfflineCostPolicy.kt"):
            self.assertFalse((SRC/path).exists(),path)
        main=(SRC/"MainActivity.kt").read_text()
        engine=(SRC/"SpokenReplyEngine.kt").read_text()
        for forbidden in ("GPT-6 Astra","AiGatewayClient","AiGatewayPolicy",
                          "AiGatewaySettings","requestAiReply(","showAiSetupDialog(",
                          "consulta nube: y una confirmación"):
            self.assertNotIn(forbidden,main)
        self.assertNotIn("speakAiReply",engine)
        self.assertNotIn("HttpsURLConnection",main)

    def test_old_tokens_are_purged_and_not_reused(self):
        cleaner=(SRC/"RetiredCloudCredentialCleaner.kt").read_text()
        self.assertIn('getSharedPreferences("omega_ai_gateway_v1"',cleaner)
        self.assertIn('deleteEntry("jarvis_omega_ai_gateway_token_v1")',cleaner)
        self.assertIn("RetiredCloudCredentialCleaner.purge(this)",(SRC/"MainActivity.kt").read_text())
        self.assertNotIn("OPENAI_API_KEY",cleaner)

    def test_premium_ui_and_local_knowledge_preserved(self):
        m=(SRC/"MainActivity.kt").read_text()
        self.assertIn("OmegaUiKit.hero(this)",m)
        self.assertIn('primaryButton("HABLAR CON JARVIS")',m)
        self.assertIn('primaryButton("PROBAR AUDIO JARVIS")',m)
        self.assertIn("localDialogue.reply(message)",m)
        self.assertIn("localDialogue.replyVoice(transcript)",m)
        self.assertIn("spokenReplyEngine.speakChatByTap(",m)
        icon=(ROOT/"apps/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").read_text()
        self.assertIn("@drawable/jarvis_reference",icon)

if __name__=="__main__":
    unittest.main()
