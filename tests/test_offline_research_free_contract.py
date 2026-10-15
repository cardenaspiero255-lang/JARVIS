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

    def test_local_only_chat_cannot_trigger_paid_requests(self):
        main=(JAVA/"MainActivity.kt").read_text()
        self.assertIn("localDialogue.reply(message)",main)
        self.assertIn("localDialogue.replyVoice(transcript)",main)
        self.assertIn("La conexión de pago fue retirada.",main)
        self.assertNotIn("requestAiReply(",main)
        self.assertNotIn("OfflineCostPolicy",main)
        self.assertNotIn("AiGatewayClient",main)
        self.assertFalse((JAVA/"core/OfflineCostPolicy.kt").exists())
        self.assertFalse((JAVA/"AiGatewayClient.kt").exists())

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
