"""Android TTS privacy/manifest safety contract."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / "apps/android/app/src/main"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class SpeechManifestTests(unittest.TestCase):
    def test_text_to_speech_engine_discovery_without_microphone_grant(self):
        root = ET.parse(ROOT / "AndroidManifest.xml").getroot()
        intents = root.findall("./queries/intent/action")
        actions = {a.attrib.get(ANDROID + "name") for a in intents}
        self.assertIn("android.intent.action.TTS_SERVICE", actions)
        grants = [item.get(ANDROID + "name")
                  for item in root.findall("uses-permission")]
        self.assertNotIn("android.permission.RECORD_AUDIO", grants)

    def test_no_always_listening_service_or_untrusted_text_channel(self):
        src = (ROOT / "java/com/cardenaspiero255/jarvis/SpokenReplyEngine.kt").read_text()
        self.assertIn("SpokenReplyPolicy", src)
        self.assertIn("TextToSpeech.QUEUE_FLUSH", src)
        self.assertIn("session.stop()", src)
        self.assertNotIn("SpeechRecognizer", src)
        self.assertNotIn("EXTRA_RESULTS", src)
