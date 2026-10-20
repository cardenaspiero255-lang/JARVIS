"""Public regression checks: adding Android Back does not expand voice authority."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1] / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
BASE = Path(__file__).resolve().parents[1]


class HandsBackSafetyContract(unittest.TestCase):
    def test_back_requires_session_arm_and_explicit_tap(self):
        policy = (ROOT / "core/HandsPolicy.kt").read_text()
        self.assertIn("HandsOperation.GLOBAL_BACK -> HandsDecision.ALLOW_BACK", policy)
        for condition in ("!context.serviceConnected", "context.emergencyStopped",
                          "!context.explicitTap"):
            self.assertIn(condition, policy)
        self.assertIn("else -> HandsDecision.DENY", policy)

    def test_only_service_invokes_system_back_and_never_reads_events(self):
        service = (ROOT / "OmegaHandsService.kt").read_text()
        ui = (ROOT / "MainActivity.kt").read_text()
        self.assertIn("service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)", service)
        self.assertIn("fun goBack(context: Context, explicitTap: Boolean)", service)
        self.assertIn("OmegaHandsService.goBack(this, explicitTap = true)", ui)
        self.assertIn("VOLVER ATRÁS (PULSACIÓN EXPLÍCITA)", ui)
        self.assertNotIn("OmegaHandsService.goBack(", (ROOT / "NativeSpanishSpeechRunner.kt").read_text())
        self.assertNotIn("OmegaHandsService.goBack(", (ROOT / "HandsFreePreviewService.kt").read_text())
        self.assertNotIn("OmegaHandsService.goBack(", (ROOT / "core/SafeAutomationEngine.kt").read_text())
        self.assertIn("override fun onAccessibilityEvent(event: AccessibilityEvent?)", service)

    def test_android_permission_copy_discloses_scope(self):
        xml = (BASE / "apps/android/app/src/main/res/values/strings.xml").read_text()
        self.assertIn("Inicio o Atrás", xml)
        self.assertIn("no ejecuta gestos", xml)
        manifest = (BASE / "apps/android/app/src/main/AndroidManifest.xml").read_text()
        self.assertIn('android.permission.BIND_ACCESSIBILITY_SERVICE', manifest)
        self.assertNotIn("android.permission.INTERNET", manifest)


if __name__ == "__main__":
    unittest.main()
