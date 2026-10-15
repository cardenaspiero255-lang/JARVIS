"""Fail-closed experimental screen capture gate for main integration."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
MANIFEST=ROOT/"apps/android/app/src/main/AndroidManifest.xml"

class ScreenProjectionReleaseGateTests(unittest.TestCase):
    def test_experimental_media_projection_disabled_in_manifest(self):
        app=ET.parse(MANIFEST).getroot().find("application")
        ns="{http://schemas.android.com/apk/res/android}"
        service=next(x for x in app.findall("service")
            if x.get(ns+"name")==".ScreenObservationService")
        self.assertEqual(service.get(ns+"enabled"),"false")
        self.assertEqual(service.get(ns+"exported"),"false")

    def test_release_gate_cannot_be_enabled_by_ui_or_a_model(self):
        policy=(SRC/"core/ScreenObservationReleaseGate.kt").read_text()
        self.assertIn("const val ENABLED = false",policy)
        self.assertNotIn("SharedPreferences",policy)
        self.assertNotIn("http",policy)
        main=(SRC/"MainActivity.kt").read_text()
        service=(SRC/"ScreenObservationService.kt").read_text()
        self.assertIn("if (!ScreenObservationReleaseGate.ENABLED)",main)
        self.assertIn("if (!ScreenObservationReleaseGate.ENABLED || intent?.action",service)
        self.assertIn("if (!ScreenObservationReleaseGate.ENABLED || live != null)",service)
        self.assertIn("if (!ScreenObservationReleaseGate.ENABLED)",main.split(
            "private fun requestScreenObservation()",1)[1].split(
            "override fun onRequestPermissionsResult",1)[0])
        self.assertIn('primaryButton("ANALIZAR IMAGEN SELECCIONADA")',main)

    def test_privacy_still_revokes_pending_projection(self):
        main=(SRC/"MainActivity.kt").read_text()
        self.assertIn("ScreenObservationService.stopNow()",main)
        self.assertIn("screenConsentGate.cancel()",main)
        self.assertIn('primaryButton("BLOQUEAR SESIÓN — PRIVACIDAD")',main)

if __name__=="__main__":
    unittest.main()
