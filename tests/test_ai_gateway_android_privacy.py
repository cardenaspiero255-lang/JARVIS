"""Privacy boundary: remote conversation is opt-in and never embeds OpenAI secrets."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]/"apps/android/app/src/main"
SRC=ROOT/"java/com/cardenaspiero255/jarvis"

class AiGatewayAndroidContracts(unittest.TestCase):
    def test_network_is_separate_from_microphone_vision_and_hands(self):
        root=ET.parse(ROOT/"AndroidManifest.xml").getroot()
        ns="{http://schemas.android.com/apk/res/android}"
        grants={p.get(ns+"name") for p in root.findall("uses-permission")}
        self.assertIn("android.permission.INTERNET",grants)
        self.assertNotIn("android.permission.FOREGROUND_SERVICE_MICROPHONE",grants)
        self.assertEqual("false",root.find("application").get(ns+"usesCleartextTraffic"))
        for path in ("VisionRecognizer.kt","OmegaHandsService.kt","ForegroundSpeechRecognizer.kt"):
            src=(SRC/path).read_text()
            self.assertNotIn("AiGatewayClient",src)
            self.assertNotIn("HttpURLConnection",src)

    def test_pairing_stays_encrypted_not_baked_into_apk(self):
        store=(SRC/"AiGatewaySettings.kt").read_text()
        for item in ("AndroidKeyStore","AES/GCM/NoPadding","GCMParameterSpec",
                     "AiGatewayPolicy.officialEndpointAllowed", "AiGatewayPolicy.tokenAllowed",
                     "configureByTap(", "revokeByTap("):
            self.assertIn(item,store)
        self.assertNotIn("OPENAI_API_KEY",store)
        self.assertNotIn("sk-proj-",store)
        self.assertNotIn("Log.",store)

    def test_network_requires_opt_in_and_does_not_do_android_actions(self):
        gateway=(SRC/"AiGatewayClient.kt").read_text()
        activity=(SRC/"MainActivity.kt").read_text()
        self.assertIn("AiGatewayPolicy.allowCloudRequest(",gateway)
        self.assertIn("HttpsURLConnection",gateway)
        self.assertIn("instanceFollowRedirects = false",gateway)
        self.assertIn("connectTimeout",gateway)
        self.assertIn("readTimeout",gateway)
        self.assertNotIn("performGlobalAction(",gateway)
        self.assertNotIn("startActivity(",gateway)
        self.assertIn('message.equals("conectar ia", ignoreCase = true)',activity)
        self.assertIn('message.equals("desconectar ia", ignoreCase = true)',activity)
        self.assertIn("aiGatewayClient.cancelAndClear()",activity)
        self.assertIn("AiGatewaySettings",activity)

    def test_pinned_url_requires_no_manual_https_but_never_embeds_token(self):
        policy=(SRC/"core/AiGatewayPolicy.kt").read_text()
        settings=(SRC/"AiGatewaySettings.kt").read_text()
        activity=(SRC/"MainActivity.kt").read_text()
        self.assertIn('const val DEFAULT_ENDPOINT = "https://jarvis-ai-gateway.vercel.app/v1/chat"',policy)
        self.assertIn("AiGatewayPolicy.DEFAULT_ENDPOINT",settings)
        dialog=activity.split("private fun showAiSetupDialog()",1)[1].split(
            "private fun requestAiReply(",1)[0]
        self.assertNotIn("endpointInput",dialog)
        self.assertIn("aiGatewaySettings.configureByTap(",dialog)
        self.assertIn("tokenInput.text.toString()",dialog)
        self.assertNotIn("sk-proj-",dialog)
        self.assertNotIn("OPENAI_API_KEY",dialog)
        self.assertNotIn("setText(\"Bearer ",dialog)

    def test_connection_failures_do_not_disguise_as_local_model_unavailable(self):
        activity=(SRC/"MainActivity.kt").read_text()
        self.assertIn('message.equals("estado ia", ignoreCase = true)',activity)
        self.assertIn("aiGatewaySettings.hasStoredPairing()",activity)
        self.assertIn("IA no emparejada",activity)
        self.assertIn("aiGatewayClient.isConfigured()",activity)
        self.assertIn("requestAiReply(paidPrompt, fromVoice = false)",activity)
        gateway=(SRC/"AiGatewayClient.kt").read_text()
        for marker in ("responseCode == 401", "responseCode == 403",
                       "responseCode == 429", "responseCode == 502"):
            self.assertIn(marker,gateway)

    def test_remote_text_speech_requires_explicit_voice_permission_not_auto_actions(self):
        activity=(SRC/"MainActivity.kt").read_text()
        engine=(SRC/"SpokenReplyEngine.kt").read_text()
        self.assertIn("speakAiReplyAfterRecognizedTap",engine)
        self.assertIn("speakAiReplyByTap",engine)
        self.assertIn("AiGatewayPolicy.spokenModelText(",engine)
        self.assertIn("speakAiReplyAfterRecognizedTap(",activity)
        self.assertIn("privateMemory.isEnabled()",activity)
        self.assertNotIn("privateMemory.save(transcript)",activity)
        self.assertNotIn("privateMemory.save(lastAiReply)",activity)

if __name__=="__main__":
    unittest.main()
