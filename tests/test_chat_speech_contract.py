"""Static invariants for manual, allowlisted chat speech (not device E2E evidence)."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
MANIFEST = ROOT / "apps/android/app/src/main/AndroidManifest.xml"


class ChatSpeechContractTests(unittest.TestCase):
    def test_no_network_permission_or_background_audio_service(self):
        root = ET.parse(MANIFEST).getroot()
        namespace = "{http://schemas.android.com/apk/res/android}"
        permissions = {x.get(namespace + "name") for x in root.findall("uses-permission")}
        self.assertNotIn("android.permission.INTERNET", permissions)
        self.assertFalse((SOURCE / "AiGatewayClient.kt").exists())
        # RECORD_AUDIO is needed for foreground opt-in speech. The new mic FGS
        # is unexported, time-limited and never auto-started or network based.
        self.assertIn("android.permission.RECORD_AUDIO", permissions)
        self.assertIn("android.permission.FOREGROUND_SERVICE_MICROPHONE", permissions)
        services = root.findall("./application/service")
        mic_services = [s for s in services
                        if s.get(namespace + "foregroundServiceType") == "microphone"]
        self.assertEqual(1, len(mic_services))
        self.assertEqual(".HandsFreePreviewService", mic_services[0].get(namespace + "name"))
        self.assertEqual("false", mic_services[0].get(namespace + "exported"))
        self.assertEqual("true", mic_services[0].get(namespace + "stopWithTask"))
        self.assertFalse(any(r.get(namespace + "name", "").endswith("BootReceiver")
                             for r in root.findall("./application/receiver")))
        tts = (SOURCE / "SpokenReplyEngine.kt").read_text()
        self.assertNotIn("SpeechRecognizer", tts)
        self.assertNotIn("RECORD_AUDIO", tts)

    def test_chat_speech_requires_button_and_fresh_answer(self):
        activity = (SOURCE / "MainActivity.kt").read_text()
        self.assertIn('primaryButton("LEER ÚLTIMA RESPUESTA DEL CHAT")', activity)
        self.assertIn("spokenReplyEngine.speakChatByTap(answer)", activity)
        self.assertIn("lastChatReply = null // Restored rows", activity)
        self.assertIn("lastChatReply = null\n        if (::visionRecognizer", activity)
        self.assertNotIn("speakChatByTap(answer)\n                lastChatReply =", activity)

    def test_tts_enforces_allowlist_and_session_stopped_state(self):
        engine = (SOURCE / "SpokenReplyEngine.kt").read_text()
        policy = (SOURCE / "core/ChatSpeechPolicy.kt").read_text()
        self.assertIn("chatSpeech.textForSpeech(answer, explicitTap = true", engine)
        self.assertIn("ready = ready && !closed, stopped = session.stopped", engine)
        self.assertIn("if (safe == null)", engine)
        self.assertIn("answer.takeIf { it in fixed || spokenNumbers.matches(it) }", policy)
        self.assertNotIn("performGlobalAction(", engine)
        self.assertNotIn("http", policy)


if __name__ == "__main__":
    unittest.main()
