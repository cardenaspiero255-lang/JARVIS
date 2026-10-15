"""Standalone validation of protected one-time JARVIS AI pairing on Android."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class BundledGatewayContracts(unittest.TestCase):
    def test_public_address_is_fixed_but_no_private_secret_is_bundled(self):
        policy=(JAVA/"core/AiGatewayPolicy.kt").read_text()
        store=(JAVA/"AiGatewaySettings.kt").read_text()
        main=(JAVA/"MainActivity.kt").read_text()
        self.assertIn(
            'const val DEFAULT_ENDPOINT = "https://jarvis-ai-gateway.vercel.app/v1/chat"',
            policy)
        self.assertIn("AiGatewayPolicy.officialEndpointAllowed(endpoint)",store)
        self.assertIn("AiGatewayPolicy.DEFAULT_ENDPOINT",main)
        self.assertNotIn("endpointInput",main)
        self.assertIn("val tokenInput = EditText(this)",main)
        self.assertNotIn("JARVIS_GATEWAY_TOKEN =",store)
        self.assertNotIn("Bearer sk-",main)

    def test_debug_status_distinguishes_local_and_encrypted_pairing(self):
        main=(JAVA/"MainActivity.kt").read_text()
        self.assertIn('message.equals("estado ia", ignoreCase = true)',main)
        self.assertIn("aiGatewaySettings.hasStoredPairing()",main)
        self.assertIn("IA no emparejada",main)
        self.assertIn("Modo gratuito y local activo",main)
        self.assertIn("aiGatewaySettings.configureByTap(",main)
        self.assertIn("tokenInput.text.toString()",main)
        self.assertIn("aiGatewayClient.isConfigured()",main)
        self.assertIn("requestAiReply(paidPrompt, fromVoice = false)",main)

    def test_permissions_voice_ui_icon_remain_unchanged(self):
        main=(JAVA/"MainActivity.kt").read_text()
        self.assertIn('primaryButton("HABLAR CON JARVIS")',main)
        self.assertIn('primaryButton("PROBAR AUDIO JARVIS")',main)
        self.assertIn('primaryButton("ENVIAR MENSAJE")',main)
        self.assertIn("OmegaUiKit.hero(this)",main)
        icon=(ROOT/"apps/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").read_text()
        self.assertIn("@drawable/jarvis_reference",icon)

    def test_gateway_http_failure_reason_is_specific_without_secret_exposure(self):
        client=(JAVA/"AiGatewayClient.kt").read_text()
        for code in ("401", "403", "429", "502", "503"):
            self.assertIn("responseCode == "+code,client)
        self.assertIn("class GatewayFailure",client)
        self.assertIn("safe.safeMessage",client)
        self.assertIn("instanceFollowRedirects = false",client)
        self.assertNotIn('Log.d(',client)

if __name__=="__main__":
    unittest.main()
