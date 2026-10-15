"""Spoken FAQs remain non-executable, non-persistent and user-initiated."""
from pathlib import Path
import unittest

BASE=Path(__file__).resolve().parents[1]/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class VoiceFaqPrivacyContract(unittest.TestCase):
    def test_faq_is_strictly_local_and_does_not_control_android(self):
        source=(BASE/"core/VoiceFaqRouter.kt").read_text()
        for forbidden in ("startActivity(", "sendBroadcast(", "performGlobalAction(", "http://", "https://",
                          "SharedPreferences", "File(", "Runtime.getRuntime(", "SpeechRecognizer("):
            self.assertNotIn(forbidden,source)
        self.assertIn("else -> null",source)
        self.assertIn("transcript.length > 160",source)

    def test_vision_faq_does_not_deny_the_experimental_screen_mode(self):
        faq=(BASE/"core/VoiceFaqRouter.kt").read_text()
        screen=(BASE/"ScreenObservationService.kt").read_text()
        self.assertIn("Omega Vision",faq)
        self.assertNotIn("La visión continua aún no está incluida",faq)
        self.assertIn("permiso de Android",faq)
        self.assertIn("no pulsa botones",faq)
        self.assertIn("ScreenObservationPolicy.MAX_SESSION_MILLIS",screen)
        self.assertIn("ProjectionStartPermit",screen)

    def test_unsupported_voice_branch_fails_closed_without_history_save(self):
        activity=(BASE/"MainActivity.kt").read_text()
        block=activity.split("VoiceAction.UNSUPPORTED -> {",1)[1].split("\n            }",1)[0]
        self.assertIn("localDialogue.replyVoice(transcript)",block)
        local=(BASE/"core/LocalDialogue.kt").read_text()
        voice=local.split("fun replyVoice(",1)[1].split("fun history()",1)[0]
        self.assertIn("VoiceFaqRouter.answer(input)",voice)
        self.assertNotIn("turns.add(",voice)
        self.assertIn("voiceMathSession.answer(transcript,",block)
        self.assertNotIn("localDialogue.reply(",block)
        self.assertNotIn("privateMemory.save(",block)
        self.assertNotIn("startActivity(",block)
        self.assertNotIn("GameHubLauncher",block)
        self.assertNotIn("OmegaHandsService.goHome",block)

    def test_tts_is_explicit_and_allowlisted(self):
        policy=(BASE/"core/ChatSpeechPolicy.kt").read_text()
        self.assertIn("VoiceFaqRouter.VISION",policy)
        self.assertIn("VoiceFaqRouter.GAMEHUB",policy)
        self.assertIn("VoiceFaqRouter.STOP",policy)
        self.assertIn("explicitTap",policy)
        self.assertIn("answer.takeIf { it in fixed || spokenNumbers.matches(it) }",policy)

if __name__=="__main__":
    unittest.main()
