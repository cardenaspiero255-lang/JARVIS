"""Legacy cloud autoconnect has been retired; assert zero paid paths in Android."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class NoAutoconnectContracts(unittest.TestCase):
    def test_no_server_address_or_token_in_android(self):
        main=(JAVA/"MainActivity.kt").read_text()
        assert "api.openai.com" not in main
        assert "jarvis-ai-gateway.vercel.app" not in main
        assert "JARVIS_GATEWAY_TOKEN" not in main
        assert "GPT-6 Astra" not in main
        self.assertFalse((JAVA/"AiGatewayClient.kt").exists())
        self.assertFalse((JAVA/"AiGatewaySettings.kt").exists())
        self.assertIn('message.equals("estado ia", ignoreCase = true)',main)
        self.assertIn("RetiredCloudCredentialCleaner.purge(this)",main)

    def test_voice_ui_icon_and_local_functionality_preserved(self):
        main=(JAVA/"MainActivity.kt").read_text()
        for command in ('primaryButton("HABLAR CON JARVIS")',
                        'primaryButton("PROBAR AUDIO JARVIS")',
                        'primaryButton("ENVIAR MENSAJE")',
                        "OmegaUiKit.hero(this)","localDialogue.reply(message)"):
            self.assertIn(command,main)
        icon=(ROOT/"apps/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").read_text()
        self.assertIn("@drawable/jarvis_reference",icon)

if __name__=="__main__":
    unittest.main()
