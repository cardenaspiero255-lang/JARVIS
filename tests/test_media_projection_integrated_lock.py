"""Privacy and lifecycle contracts for the rebased MediaProjection integration."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/"apps/android/app/src/main"
JAVA=BASE/"java/com/cardenaspiero255/jarvis"
ANDROID="{http://schemas.android.com/apk/res/android}"

class MediaProjectionLifecycleContract(unittest.TestCase):
    def test_service_is_nonexported_and_permission_constrained(self):
        root=ET.parse(BASE/"AndroidManifest.xml").getroot()
        perms={x.get(ANDROID+"name") for x in root.findall("uses-permission")}
        self.assertIn("android.permission.POST_NOTIFICATIONS",perms)
        self.assertIn("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",perms)
        self.assertIn("android.permission.INTERNET",perms)
        self.assertNotIn("AiGatewayClient", (JAVA/"ScreenObservationService.kt").read_text())
        services=[x for x in root.findall("./application/service")
                  if x.get(ANDROID+"name")==".ScreenObservationService"]
        self.assertEqual(len(services),1)
        self.assertEqual(services[0].get(ANDROID+"exported"),"false")
        self.assertEqual(services[0].get(ANDROID+"foregroundServiceType"),"mediaProjection")

    def test_session_lock_cancels_pending_consent_and_active_service(self):
        activity=(JAVA/"MainActivity.kt").read_text()
        lock=activity.split("private fun lockSessionByTap()",1)[1].split(
            "private fun handsStatus()",1)[0]
        self.assertIn("SessionLockChannel.SCREEN_PROJECTION",lock)
        self.assertIn("screenConsentRequested = false",lock)
        self.assertIn("ScreenObservationService.stopNow()",lock)
        self.assertIn("check(",lock)
        self.assertIn("ScreenObservationService.authorizeOneStart()",activity)
        self.assertIn("ScreenObservationService.stopNow()",activity)

    def test_one_use_approval_revoked_by_lock(self):
        service=(JAVA/"ScreenObservationService.kt").read_text()
        self.assertIn("pendingStart.consume()",service)
        self.assertIn("pendingStart.revoke()",service)
        self.assertIn("fun stopNow(): Boolean",service)
        self.assertIn("fun authorizeOneStart(): Boolean",service)
        self.assertIn("terminate(stopProjection = true)",service)
        self.assertIn("stopSelf(startId)",service)
        gate=(JAVA/"core/ProjectionStartPermit.kt").read_text()
        self.assertIn("AtomicBoolean(false)",gate)
        self.assertIn("authorized.compareAndSet(true, false)",gate)

    def test_previous_sensitive_data_still_not_retained(self):
        service=(JAVA/"ScreenObservationService.kt").read_text()
        for forbidden in ("FileOutputStream", "SharedPreferences", "HttpURLConnection",
                          "java.net.", "OmegaHandsService.goHome", "performGlobalAction("):
            self.assertNotIn(forbidden,service)
        self.assertIn("liveHintCount",service)
        self.assertIn("MAX_SESSION_MILLIS",service)

if __name__=="__main__":
    unittest.main()
