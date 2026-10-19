"""Source-level complement to JVM behavioral tests for local plans."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
CORE = APP / "core"


class LocalPlannerUiPrivacyContract(unittest.TestCase):
    def test_spoken_plan_is_preview_not_transcript_as_execution(self):
        main = (APP / "MainActivity.kt").read_text(encoding="utf8")
        companion = (APP / "CompanionCenterActivity.kt").read_text(encoding="utf8")
        planner = (CORE / "OfflineRoutinePlanner.kt").read_text(encoding="utf8")
        self.assertIn("offlineRoutinePlanner.plan(transcript)", main)
        self.assertIn("REVISAR PLAN", main)
        self.assertIn("EXTRA_ROUTINE_PREVIEW", main)
        self.assertIn('android', (ROOT / "apps/android/app/src/main/AndroidManifest.xml").read_text())
        self.assertIn("autoriza", main.lower())
        self.assertIn("confirmAndRun(routine, explicitTap = true)", companion)
        self.assertIn("Sin", companion)
        self.assertIn("authorizedToExecute: Boolean get() = false", planner)
        self.assertNotIn("Runtime.getRuntime().exec(", planner)
        self.assertNotIn("Shizuku.newProcess(", planner)

    def test_user_consent_expires_and_real_outcomes_are_not_assumed(self):
        engine = (CORE / "SafeAutomationEngine.kt").read_text(encoding="utf8")
        companion = (APP / "CompanionCenterActivity.kt").read_text(encoding="utf8")
        self.assertIn("PREVIEW_VALIDITY_MS = 30_000L", engine)
        self.assertIn("now >= issued", engine)
        self.assertIn("now - issued < PREVIEW_VALIDITY_MS", engine)
        self.assertIn("rollbackVerified: Boolean = false", engine)
        self.assertIn("externalOutcomeVerified: Boolean = false", engine)
        self.assertIn("Resultado externo sin verificar", companion)
        self.assertIn("Paso fallido:", companion)

    def test_no_new_remote_network_or_mic_privileges(self):
        manifest = (ROOT / "apps/android/app/src/main/AndroidManifest.xml").read_text()
        self.assertNotIn('android:name="android.permission.INTERNET"', manifest)
        self.assertNotIn("BOOT_COMPLETED", manifest)


if __name__ == "__main__":
    unittest.main()
