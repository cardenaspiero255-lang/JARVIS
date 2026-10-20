"""Fail-closed release invariants for the new opt-in offline voice bridge."""
from pathlib import Path
import unittest

BASE = Path(__file__).resolve().parents[1]
SRC = BASE / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"


class LocalVoiceBridgeContract(unittest.TestCase):
    def test_not_an_exported_implicit_or_boot_service(self):
        manifest = (BASE / "apps/android/app/src/main/AndroidManifest.xml").read_text()
        self.assertIn('android:name=".HandsFreePreviewService"', manifest)
        self.assertIn('android:exported="false"', manifest)
        self.assertNotIn('android.permission.INTERNET', manifest)
        self.assertNotIn("android.intent.action.BOOT_COMPLETED", manifest)

    def test_explicit_one_turn_user_button_and_tap_gate(self):
        activity = (SRC / "MainActivity.kt").read_text()
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        self.assertIn('CONVERSAR OFFLINE CON JARVIS', activity)
        self.assertIn('toggleHandsFreePreviewByTap(conversation = true)', activity)
        self.assertIn('ACTION_START_CONVERSATION', service)
        self.assertIn('getBooleanExtra(EXTRA_TAPPED, false)', service)
        self.assertIn('startForeground(', service)
        self.assertIn('localGgufStore.hasModel()', activity)

    def test_input_cannot_gain_phone_privileges(self):
        gate = (SRC / "core/OfflineVoiceQuestionGate.kt").read_text()
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        self.assertIn("LocalGgufPolicy.validPrompt", gate)
        self.assertIn("mayExecuteDeviceActions = false", gate)
        self.assertIn('onQuestion = { question ->', service)
        self.assertIn('OfflineVoiceAnswerPolicy.forTts(response)', service)
        for unsafe in ["Shizuku.requestPermission", "AccessibilityService.performGlobalAction",
                       "startActivityForResult(", "Termux.RUN_COMMAND"]:
            self.assertNotIn(unsafe, gate)

    def test_native_mic_released_before_inference_and_stop_cancels_it(self):
        runner = (SRC / "NativeSpanishSpeechRunner.kt").read_text()
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        self.assertLess(runner.index("microphone?.release()"), runner.index("onQuestion(question)"))
        self.assertIn("localConversationInference?.cancelActive()", service)
        self.assertIn("localConversationInference?.close()", service)
        self.assertIn("conversationTimeout", service)
        self.assertIn("90_000L", service)
        self.assertIn("timeGate.stop()", service)

    def test_unrestricted_offline_asr_only_for_explicit_conversation(self):
        runner = (SRC / "NativeSpanishSpeechRunner.kt").read_text()
        self.assertIn("if (conversationMode) Recognizer(model, 16_000.0f)", runner)
        self.assertIn("else createCommandRecognizer(model, folder)", runner)
        self.assertIn("questionGate.observe(", runner)
        self.assertIn("SpeakerEvidence.UNAVAILABLE", runner)


if __name__ == "__main__":
    unittest.main()
