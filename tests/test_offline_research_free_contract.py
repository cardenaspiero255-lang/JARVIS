"""Regression contracts for free offline knowledge and strict paid-request boundary."""
from pathlib import Path
import re
import unittest

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class LocalResearchNoBillingTests(unittest.TestCase):
    def test_curated_science_and_tech_without_model_weights(self):
        engine=(JAVA/"core/OfflineKnowledgeEngine.kt").read_text()
        topics=re.findall(r'Topic\("([A-Z_]+)"',engine)
        self.assertGreaterEqual(len(topics),20)
        self.assertEqual(len(topics),len(set(topics)))
        self.assertIn("SAFE_SPOKEN_REPLIES",engine)
        self.assertIn("contextToken()",engine)
        self.assertIn("restoreContextToken(",engine)
        for forbidden in ("http://","https://","HttpURLConnection",
                          "SpeechRecognizer(", "performGlobalAction(", "startActivity("):
            self.assertNotIn(forbidden,engine)

    def test_existing_chats_and_voice_reach_offline_knowledge(self):
        dialogue=(JAVA/"core/LocalDialogue.kt").read_text()
        self.assertIn("knowledgeEngine.answer(sanitized)",dialogue)
        self.assertIn("knowledgeEngine.answer(input)",dialogue)
        self.assertIn("knowledgeEngine.contextToken()",dialogue)
        self.assertIn('lines.add("Conocimiento: $knowledgeContext")',dialogue)
        self.assertIn('knowledgeEngine.restoreContextToken(',dialogue)
        voice=dialogue.split("fun replyVoice(",1)[1].split("fun history()",1)[0]
        self.assertNotIn("turns.add(",voice)
        speech=(JAVA/"core/ChatSpeechPolicy.kt").read_text()
        self.assertIn("OfflineKnowledgeEngine.SAFE_SPOKEN_REPLIES",speech)
        self.assertIn("answer.takeIf { it in fixed || spokenNumbers.matches(it) }",speech)

    def test_paired_token_does_not_turn_on_paid_chats(self):
        main=(JAVA/"MainActivity.kt").read_text()
        self.assertIn("OfflineCostPolicy.isPaidRequest(message)",main)
        self.assertIn("OfflineCostPolicy.paidPrompt(message)",main)
        self.assertIn('setTitle("Confirmar consulta de pago")',main)
        self.assertIn('setPositiveButton("Enviar de pago")',main)
        self.assertIn("requestAiReply(paidPrompt, fromVoice = false)",main)
        self.assertNotIn("aiGatewayClient.isConfigured() -> {\n                    chatInput.text.clear()\n                    requestAiReply(message",main)
        body=main.split("private fun processRecognizedSpeech(transcript: String)",1)[1].split(
            "private fun showAiSetupDialog()",1)[0]
        self.assertIn("localDialogue.replyVoice(transcript)",body)
        self.assertNotIn("requestAiReply(transcript",body)
        self.assertNotIn("aiGatewayClient.request(",body)
        policy=(JAVA/"core/OfflineCostPolicy.kt").read_text()
        self.assertIn('PAID_PREFIX = "consulta nube:"',policy)

    def test_preserves_icon_and_sound(self):
        main=(JAVA/"MainActivity.kt").read_text()
        self.assertIn("OmegaUiKit.hero(this)",main)
        self.assertIn('primaryButton("HABLAR CON JARVIS")',main)
        self.assertIn('primaryButton("PROBAR AUDIO JARVIS")',main)
        icon=(ROOT/"apps/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").read_text()
        self.assertIn("@drawable/jarvis_reference",icon)
        engine=(JAVA/"SpokenReplyEngine.kt").read_text()
        self.assertIn("spokenReplyEngine",main)
        self.assertIn("chatSpeech.textForSpeech",engine)

if __name__=="__main__":
    unittest.main()
