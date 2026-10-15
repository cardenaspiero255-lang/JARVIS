"""Security regression contracts for explicit on-device readiness inspection."""
from pathlib import Path
import unittest

SRC=Path(__file__).resolve().parents[1] / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class ReadOnlyDiagnosticsContract(unittest.TestCase):
    def test_only_visible_ui_button_invokes_read(self):
        app=(SRC/"MainActivity.kt").read_text()
        self.assertIn('primaryButton("DIAGNÓSTICO LOCAL DE JARVIS")',app)
        self.assertIn("LocalCapabilityDiagnostics.format(readCapabilities())",app)
        self.assertIn("private fun readCapabilities()",app)

    def test_diagnostics_never_runs_actions_or_sends_data(self):
        formatter=(SRC/"core/LocalCapabilityDiagnostics.kt").read_text()
        code=(SRC/"MainActivity.kt").read_text().split("private fun readCapabilities()",1)[1].split("private fun handsStatus()",1)[0]
        for bad in ("startActivity(", "goHome(", "performGlobalAction(", "sendBroadcast(", "http",
                    "privateMemory.save(", "SharedPreferences", "Runtime.getRuntime"):
            self.assertNotIn(bad,formatter)
            self.assertNotIn(bad,code)
        self.assertIn("getLaunchIntentForPackage",code)
        self.assertIn("SpeechRecognizer.isRecognitionAvailable",code)
        self.assertIn("offlineVoiceReady()",code)
        self.assertIn("SIN CERTIFICACIÓN",formatter)

if __name__=="__main__":
    unittest.main()
