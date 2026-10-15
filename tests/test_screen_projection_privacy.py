"""MediaProjection privacy and foreground lifecycle invariants."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
ANDROID="{http://schemas.android.com/apk/res/android}"
BASE=ROOT/"apps/android/app/src/main"

class ScreenProjectionPrivacyContracts(unittest.TestCase):
    def test_projection_service_not_exported_and_protected_by_android_permissions(self):
        manifest=ET.parse(BASE/"AndroidManifest.xml").getroot()
        grants={x.get(ANDROID+"name") for x in manifest.findall("uses-permission")}
        self.assertIn("android.permission.FOREGROUND_SERVICE",grants)
        self.assertIn("android.permission.POST_NOTIFICATIONS",grants)
        self.assertIn("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",grants)
        self.assertIn("android.permission.INTERNET",grants)
        self.assertNotIn("AiGatewayClient", (BASE/"java/com/cardenaspiero255/jarvis/ScreenObservationService.kt").read_text())
        self.assertNotIn("android.permission.CAMERA",grants)
        services=[x for x in manifest.findall("./application/service")
                  if x.get(ANDROID+"name")==".ScreenObservationService"]
        self.assertEqual(len(services),1)
        self.assertEqual(services[0].get(ANDROID+"exported"),"false")
        self.assertEqual(services[0].get(ANDROID+"foregroundServiceType"),"mediaProjection")

    def test_every_capture_requires_os_consent_and_has_stop_notification(self):
        activity=(BASE/"java/com/cardenaspiero255/jarvis/MainActivity.kt").read_text()
        service=(BASE/"java/com/cardenaspiero255/jarvis/ScreenObservationService.kt").read_text()
        for name in ("createScreenCaptureIntent()", "INICIAR VISIÓN DE PANTALLA",
                     "DETENER VISIÓN DE PANTALLA", "screenConsentRequested"):
            self.assertIn(name,activity)
        for name in ("startForeground(", "registerCallback(", "override fun onStop()",
                     "ACTION_STOP", "DETENER OBSERVACIÓN", "MAX_SESSION_MILLIS",
                     "reader?.close()", "virtualDisplay?.release()", "stopSelf()"):
            self.assertIn(name,service)
        self.assertNotIn("sendBroadcast(",service)
        self.assertNotIn("java.net.",service)

    def test_live_capture_does_not_persist_or_publish_ocr_text(self):
        service=(BASE/"java/com/cardenaspiero255/jarvis/ScreenObservationService.kt").read_text()
        self.assertIn("ButtonHintDetector().detect",service)
        self.assertIn("liveHintCount",service)
        for forbidden in ("SharedPreferences", "FileOutputStream", "HttpURLConnection",
                          "OkHttpClient", "OmegaHandsService.goHome", "performGlobalAction("):
            self.assertNotIn(forbidden,service)
        self.assertIn("if (!submitted) {",service)
        self.assertIn("pendingBitmap?.recycle()",service)
        self.assertIn("processing.set(false)",service)

    def test_notification_permission_is_required_by_activity_and_service(self):
        activity=(BASE/"java/com/cardenaspiero255/jarvis/MainActivity.kt").read_text()
        service=(BASE/"java/com/cardenaspiero255/jarvis/ScreenObservationService.kt").read_text()
        self.assertIn("ScreenNotificationGate.canBegin(",activity)
        self.assertIn("ScreenNotificationGate.canBegin(",service)
        self.assertIn("Manifest.permission.POST_NOTIFICATIONS",activity)
        self.assertIn("Manifest.permission.POST_NOTIFICATIONS",service)
        self.assertIn("REQUEST_SCREEN_NOTIFICATIONS",activity)
