"""One-shot spoken replies after a verified HABLAR tap; preserve the premium UI."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]/"apps/android/app/src/main"
SRC=ROOT/"java/com/cardenaspiero255/jarvis"
ACT=SRC/"MainActivity.kt"
ENGINE=SRC/"SpokenReplyEngine.kt"
POLICY=SRC/"core/ChatSpeechPolicy.kt"

class SpokenConversationContracts(unittest.TestCase):
    def test_both_recognition_backends_use_the_same_verified_reply_dispatch(self):
        main=ACT.read_text()
        self.assertEqual(2,main.count("processRecognizedSpeech(transcript)"))
        self.assertIn("recognitionRequest.consume(SystemClock.elapsedRealtime())",main)
        self.assertIn("foregroundGate.canAcceptRecognitionResult()",main)
        process=main.split("private fun processRecognizedSpeech(transcript: String)",1)[1].split(
            "private fun openTtsSettingsByTap()",1)[0]
        self.assertIn("spokenReplyEngine.speakRecognizedAnswerAfterTap(",process)
        self.assertIn("spokenReplyEngine.speakRecognizedHealthAfterTap(",process)
        self.assertIn("spokenReplyEngine.speakRecognizedGameAfterTap(",process)
        self.assertIn("VoiceAction.STOP_SESSION -> {",process)
        self.assertIn("lockSessionByTap()",process)
        self.assertIn("if (action != VoiceAction.STOP_SESSION)",process)

    def test_tts_may_resume_after_one_validated_voice_request_but_not_on_lock(self):
        tts=ENGINE.read_text()
        self.assertIn("fun speakRecognizedAnswerAfterTap(",tts)
        self.assertIn("fun speakRecognizedHealthAfterTap(",tts)
        self.assertIn("fun speakRecognizedGameAfterTap(",tts)
        self.assertIn("autoStartBlocked = false",tts)
        self.assertIn("session.resume()",tts)
        self.assertIn("fun stop()",tts)
        self.assertIn("autoStartBlocked = true",tts)
        self.assertIn("tracker.cancel()",tts)
        self.assertIn("fun speakExplicitText(",tts)
        self.assertIn("preserveDisplayedAnswer",tts)

    def test_replies_are_locally_allowlisted_and_never_raw_screen_or_asr_text(self):
        main=ACT.read_text()
        engine=ENGINE.read_text()
        policy=POLICY.read_text()
        self.assertIn("chatSpeech.textForSpeech(answer, explicitTap = true",engine)
        self.assertIn("answer.takeIf { it in fixed || spokenNumbers.matches(it) }",policy)
        self.assertIn("GameReadinessSpeechPolicy",engine)
        self.assertIn("DeviceHealthSpeechPolicy",engine)
        self.assertNotIn("speakExplicitText(transcript)",engine)
        self.assertNotIn("speakRecognizedAnswerAfterTap(transcript)",main)

    def test_user_approved_interface_icon_are_unchanged(self):
        m=ACT.read_text()
        self.assertIn('primaryButton("HABLAR CON JARVIS")',m)
        self.assertIn('primaryButton("PROBAR AUDIO JARVIS")',m)
        self.assertIn('primaryButton("DETENER RESPUESTA DE VOZ")',m)
        self.assertIn("OmegaUiKit.hero(this)",m)
        icon=(ROOT/"res/mipmap-anydpi-v26/ic_launcher.xml").read_text()
        self.assertIn('@drawable/jarvis_reference',icon)

if __name__=="__main__":
    unittest.main()
