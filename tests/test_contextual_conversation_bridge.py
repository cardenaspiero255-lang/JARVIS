"""Conversation continuity and privacy contracts for the existing Android premium shell."""
from pathlib import Path
import unittest

CORE=Path(__file__).resolve().parents[1]/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
RES=Path(__file__).resolve().parents[1]/"apps/android/app/src/main/res"

class ContextConversationBridgeTests(unittest.TestCase):
    def test_recognized_voice_uses_ephemeral_known_topic_reply_not_transcript_history(self):
        activity=(CORE/"MainActivity.kt").read_text()
        unsupported=activity.split("VoiceAction.UNSUPPORTED -> {",1)[1].split("\n            }",1)[0]
        self.assertIn("localDialogue.replyVoice(transcript)", unsupported)
        self.assertNotIn("localDialogue.reply(transcript)",unsupported)
        self.assertNotIn("privateMemory.save(",unsupported)
        self.assertNotIn("GameHubLauncher(",unsupported)
        self.assertNotIn("startActivity(",unsupported)
        local=(CORE/"core/LocalDialogue.kt").read_text()
        voice=local.split("fun replyVoice(",1)[1].split("fun history()",1)[0]
        self.assertIn("VoiceFaqRouter.answer(",voice)
        self.assertIn("topicDialogue.answer(",voice)
        self.assertNotIn("turns.add(",voice)
        self.assertNotIn("privateMemory",voice)

    def test_topic_answers_are_fixed_safe_speech_only(self):
        topic=(CORE/"core/LocalTopicDialogue.kt").read_text()
        speech=(CORE/"core/ChatSpeechPolicy.kt").read_text()
        self.assertIn("SAFE_SPOKEN_REPLIES",topic)
        self.assertIn("LocalTopicDialogue.SAFE_SPOKEN_REPLIES",speech)
        self.assertIn("answer.takeIf { it in fixed || spokenNumbers.matches(it) }",speech)
        for forbidden in ("startActivity(", "performGlobalAction(", "http://", "https://",
                          "SharedPreferences", "FileOutputStream", "Socket(", "SpeechRecognizer("):
            self.assertNotIn(forbidden,topic)

    def test_encrypted_memory_is_opt_in_and_cannot_persist_raw_voice(self):
        main=(CORE/"MainActivity.kt").read_text()
        local=(CORE/"core/LocalDialogue.kt").read_text()
        topic=(CORE/"core/LocalTopicDialogue.kt").read_text()
        self.assertIn("privateMemory.isEnabled()",main)
        self.assertIn("privateMemory.save(localDialogue.snapshotForStorage())",main)
        self.assertNotIn("privateMemory.save(transcript)",main)
        snapshot=local.split("fun snapshotForStorage(): String",1)[1].split(
            "/**",1)[0]
        self.assertIn("topicDialogue.contextToken()",snapshot)
        self.assertNotIn("replyVoice(",snapshot)
        self.assertNotIn("SpeechRecognizer",snapshot)
        self.assertIn("restoreContextToken(",topic)
        self.assertIn("Topic.values().firstOrNull",topic)

    def test_premium_icon_and_ui_are_unchanged(self):
        activity=(CORE/"MainActivity.kt").read_text()
        self.assertIn("OmegaUiKit.hero(this)",activity)
        self.assertIn('primaryButton("HABLAR CON JARVIS")',activity)
        self.assertIn('primaryButton("PROBAR AUDIO JARVIS")',activity)
        icon=(RES/"mipmap-anydpi-v26/ic_launcher.xml").read_text()
        self.assertIn("@drawable/jarvis_reference",icon)

if __name__=="__main__":
    unittest.main()
