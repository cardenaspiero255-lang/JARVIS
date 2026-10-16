"""Regression contract for bugs reported on physical Vivo V25 Pro."""
import unittest
from pathlib import Path

ANDROID = Path(__file__).resolve().parents[1] / "apps/android/app"
MAIN = ANDROID / "src/main/java/com/cardenaspiero255/jarvis/MainActivity.kt"
VOICE = ANDROID / "src/main/java/com/cardenaspiero255/jarvis/core/ForegroundListeningSession.kt"

class VivoRecoveryTest(unittest.TestCase):
    def test_home_launcher_is_visible_without_opening_hidden_advanced_controls(self):
        source = MAIN.read_text()
        controls = source.split("val advancedNames = setOf(", 1)[1].split(")", 1)[0]
        self.assertIn('primaryButton("PROBAR JARVIS OS — SIN CAMBIAR FUNTOUCH")', source)
        self.assertNotIn('PROBAR JARVIS OS — SIN CAMBIAR FUNTOUCH', controls)
        self.assertIn('startActivity(Intent(this, JarvisHomeActivity::class.java))', source)

    def test_screenshot_permission_is_reachable_and_remains_opt_in(self):
        source = MAIN.read_text()
        controls = source.split("val advancedNames = setOf(", 1)[1].split(")", 1)[0]
        self.assertNotIn('PERMITIR CAPTURAS 60 S — DEPURACIÓN', controls)
        self.assertIn('primaryButton("PERMITIR CAPTURAS 60 S — DEPURACIÓN")', source)
        self.assertIn("capturePrivacyGate.allowForDebugByTap", source)
        self.assertIn("window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)", source)
        self.assertIn("window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)", source)

    def test_error_7_is_not_a_fatal_provider_crash_and_repeated_listening_stays_foreground(self):
        source = MAIN.read_text()
        gate = VOICE.read_text()
        self.assertIn("SpeechRecognizer.ERROR_NO_MATCH", source)
        self.assertIn("ListeningDecision.RETRY", source)
        self.assertIn('primaryButton("ACTIVAR ESCUCHA CONTINUA")', source)
        self.assertIn('stopContinuousListening()', source)
        self.assertIn('foregroundGate.onStop()', source)
        self.assertIn("if (fromContinuous)", source)
        self.assertIn("ListeningDecision.STOP", gate)
        self.assertIn("MAX_MISSED_ROUNDS = 5", gate)
        self.assertNotIn("FOREGROUND_SERVICE_MICROPHONE", (ANDROID / "src/main/AndroidManifest.xml").read_text())
        self.assertNotIn("startForegroundService(", source)

if __name__ == "__main__":
    unittest.main()
