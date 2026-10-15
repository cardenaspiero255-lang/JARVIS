"""Device-health voice is read-only and TTS needs a separate visible tap."""
from pathlib import Path
import unittest

JAVA=Path(__file__).resolve().parents[1]/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class HealthVoiceSafetyContracts(unittest.TestCase):
    def test_recognition_never_speaks_or_executes_actions(self):
        source=(JAVA/"MainActivity.kt").read_text()
        branch=source.split("VoiceAction.READ_DEVICE_HEALTH -> {",1)[1].split(
            "VoiceAction.OPEN_ACCESSIBILITY_SETTINGS -> {",1
        )[0]
        self.assertIn("DeviceHealthReader(this).read()",branch)
        self.assertIn("DeviceHealthFormatter.format(reading)",branch)
        for bad in ("speak(", "startActivity(", "GameHubLauncher",
                    "OmegaHandsService.goHome", "privateMemory.save", "speakHealthByTap"):
            self.assertNotIn(bad,branch)

    def test_manual_speech_uses_structured_local_reading_and_offline_session(self):
        activity=(JAVA/"MainActivity.kt").read_text()
        engine=(JAVA/"SpokenReplyEngine.kt").read_text()
        self.assertIn('primaryButton("LEER SALUD DEL TELÉFONO EN VOZ ALTA")',activity)
        self.assertIn("spokenReplyEngine.speakHealthByTap(reading)",activity)
        self.assertIn("healthSpeech.textForSpeech(snapshot, explicitTap = true",engine)
        self.assertIn("ready = ready && !closed, stopped = session.stopped",engine)
        self.assertIn("TextToSpeech.QUEUE_FLUSH",engine)

    def test_spoken_health_format_is_offline_and_bounded(self):
        source=(JAVA/"core/DeviceHealthSpeechPolicy.kt").read_text()
        for bad in ("SharedPreferences", "HttpURLConnection", "startActivity(",
                    "performGlobalAction(", "Runtime.getRuntime("):
            self.assertNotIn(bad,source)
        self.assertIn("snapshot.batteryPercent?.takeIf { it in 0..100 }",source)
        self.assertIn("availableMemoryBytes <= snapshot.totalMemoryBytes",source)

if __name__=="__main__":
    unittest.main()
