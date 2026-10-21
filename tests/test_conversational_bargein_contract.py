"""Adversarial contracts for explicit local GGUF barge-in during TTS."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
class ConversationalBargeInContract(unittest.TestCase):
    def test_separate_explicit_multiturn_mode_and_physical_stop(self):
        main=(SRC/"MainActivity.kt").read_text()
        service=(SRC/"HandsFreePreviewService.kt").read_text()
        self.assertIn("conversationExtended: Boolean = false",main)
        self.assertIn("ACTION_START_LONG_CONVERSATION",main)
        self.assertIn("ACTION_START_LONG_CONVERSATION",service)
        self.assertIn("conversationRequested || longConversationRequested",service)
        self.assertIn("extendedRequested || longConversationRequested",service)
        self.assertIn("onBargeInQuestion = { question ->",service)
        self.assertIn("ACTION_STOP",service)
        self.assertIn("MAX_CONVERSATION_TURNS",service)

    def test_audio_fallback_and_single_native_inference_worker(self):
        runner=(SRC/"NativeSpanishSpeechRunner.kt").read_text()
        service=(SRC/"HandsFreePreviewService.kt").read_text()
        policy=(SRC/"core/NativeAudioSourceFailoverPolicy.kt").read_text()
        self.assertIn("NativeAudioSourceFailoverPolicy.open(possibleAec)",runner)
        self.assertIn("ASR_NATIVE_AEC_CAPTURE_FALLBACK_MIC",runner)
        self.assertIn("catch (revoked: SecurityException)",policy)
        self.assertIn("localConversationInference ?: LocalGgufInference(modelStore)",service)
        self.assertIn("if (extendedListening)",service)

    def test_gguf_turn_remains_read_only_and_single_mic(self):
        runner=(SRC/"NativeSpanishSpeechRunner.kt").read_text()
        service=(SRC/"HandsFreePreviewService.kt").read_text()
        self.assertIn("BargeInQuestionPolicy()",runner)
        self.assertIn("onBargeInQuestion(next)",runner)
        self.assertIn("questionGate.observe(",runner)
        self.assertIn("responsePlayback.set(true)",runner)
        self.assertIn("onQuestion(question)",runner)
        self.assertIn("if (conversationMode && continuous)",runner)
        self.assertIn("LocalGgufPolicy.validPrompt(question)",service)
        self.assertIn("OfflineVoiceAnswerPolicy.forTts(response)",service)
        self.assertNotIn("OmegaHandsService",runner)
        self.assertNotIn("SpeakerEvidence.MATCHED",runner)

    def test_revocation_and_inference_generation(self):
        service=(SRC/"HandsFreePreviewService.kt").read_text()
        self.assertIn("conversationGeneration",service)
        self.assertIn("conversationGeneration == ticket",service)
        self.assertIn("localConversationInference?.cancelActive()",service)
        self.assertIn("nativeRunner?.setResponsePlayback(true)",service)
        self.assertIn("tts?.stop()",service)
        self.assertIn("ttsPlaybackTicket",service)

    def test_experimental_bargein_is_debug_only_until_physical_audio_gate(self):
        main=(SRC/"MainActivity.kt").read_text()
        service=(SRC/"HandsFreePreviewService.kt").read_text()
        self.assertIn("ApplicationInfo.FLAG_DEBUGGABLE", main)
        self.assertIn("ApplicationInfo.FLAG_DEBUGGABLE", service)
        self.assertIn("longConversationRequested && !debugBuild", service)
        self.assertIn("if (debugBuild)", main)

    def test_unmodified_one_turn_conversation_and_no_new_permissions(self):
        service=(SRC/"HandsFreePreviewService.kt").read_text()
        self.assertIn("if (!extendedListening) {",service)
        self.assertIn("nativeRunner?.stop()",service)
        manifest=(ROOT/"apps/android/app/src/main/AndroidManifest.xml").read_text()
        for permission in ("READ_EXTERNAL_STORAGE","MANAGE_EXTERNAL_STORAGE","BIND_VOICE_INTERACTION"):
            self.assertNotIn(permission,manifest)

if __name__=="__main__": unittest.main()
