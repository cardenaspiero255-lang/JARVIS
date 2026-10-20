"""Cross-feature security: merged Hands and Voice must preserve both controls."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class HandsVoiceIntegrationTests(unittest.TestCase):
    def test_visible_home_stop_voice_consent_and_british_profile_survive_merge(self):
        text = (ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis/MainActivity.kt").read_text()
        for expected in (
            "PARADA DE EMERGENCIA HANDS",
            "AUTORIZAR HANDS — INICIO Y ATRÁS",
            "OmegaHandsService.goHome(",
            "VoiceStyle.BRITISH_ASSISTANT",
            "CONFIRMAR AJUSTES SOLICITADOS POR VOZ",
            "voiceConsent.consume(",
            "foregroundGate.canAcceptRecognitionResult()",
        ):
            with self.subTest(expected=expected):
                self.assertIn(expected, text)

    def test_both_accessibility_and_tts_discovery_are_in_manifest(self):
        text = (ROOT / "apps/android/app/src/main/AndroidManifest.xml").read_text()
        self.assertIn('android.intent.action.TTS_SERVICE', text)
        self.assertIn('android.permission.BIND_ACCESSIBILITY_SERVICE', text)
        self.assertIn('@xml/omega_hands_service', text)
