"""17 contract checks for NOT YET IMPLEMENTED roadmap features.

These tests protect existing denial/security boundaries. Passing them means
the missing features remain gated; it is NOT positive functional coverage.
Each test cites a concrete source or policy controlling accidental exposure.
"""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

from jarvis_contracts.task_plan import ActionRisk, PlanCompiler

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "apps/android/app/src/main"
SRC = APP / "java/com/cardenaspiero255/jarvis"
CORE = SRC / "core"
TRACKER = (ROOT / "docs/SSS_PLUS_50_TRACKER.md").read_text()
ANDROID = "{http://schemas.android.com/apk/res/android}"


def source(path):
    return path.read_text(encoding="utf-8")


def pending(number):
    line = next((l for l in TRACKER.splitlines()
                 if re.match(rf"^\|\s*{number}\s*\|", l)), None)
    if line is None:
        raise AssertionError(f"Missing roadmap row {number}")
    assert "Pendiente" in line.split("|")[4], f"Feature {number} cannot be marked done"
    return line


def permissions():
    manifest = ET.parse(APP / "AndroidManifest.xml").getroot()
    return manifest, {node.get(ANDROID + "name")
                      for node in manifest.findall("uses-permission")}


class NotImplementedFeatureGuards(unittest.TestCase):
    """One explicit negative release gate for each of the 17 known holes."""

    def test_03_model_auto_selection_is_not_silently_claimed(self):
        pending(3)
        runner = source(SRC / "NativeSpanishSpeechRunner.kt")
        self.assertIn("Vosk", runner)
        self.assertIn("offline", runner.lower())
        self.assertNotIn("autoSelectCloudModel", runner)

    def test_04_independent_verifier_must_not_execute_plan(self):
        pending(4)
        plan = PlanCompiler().compile(
            {"steps": [{"id": "a", "action": "status.read", "dependsOn": []}]})
        self.assertFalse(plan.can_execute)
        self.assertEqual(ActionRisk.READ_ONLY, plan.risks["a"])

    def test_06_learning_routines_not_autorun(self):
        pending(6)
        engine = source(CORE / "SafeAutomationEngine.kt")
        self.assertIn("explicitTap", engine)
        self.assertIn("emergencyStopped", engine)
        self.assertNotIn("learnFromBackgroundAudio", engine)

    def test_07_confidence_calibration_does_not_grant_match(self):
        pending(7)
        policy = source(CORE / "SpeakerEnrollmentPolicy.kt")
        self.assertIn("return SpeakerEvidence.UNCERTAIN", policy)
        self.assertIn("return SpeakerEvidence.UNAVAILABLE", policy)

    def test_09_persistent_goals_not_executable_without_approval(self):
        pending(9)
        preview = PlanCompiler().compile({"steps": [
            {"id": "write", "action": "merge.execute", "dependsOn": []}]})
        self.assertFalse(preview.can_execute)
        self.assertEqual(ActionRisk.REQUIRES_APPROVAL, preview.risks["write"])

    def test_10_research_cannot_claim_cloud_access_from_apk(self):
        pending(10)
        _, perms = permissions()
        self.assertNotIn("android.permission.INTERNET", perms)
        self.assertNotIn("android.permission.ACCESS_FINE_LOCATION", perms)

    def test_11_contextual_automation_is_previews_only(self):
        pending(11)
        engine = source(CORE / "SafeAutomationEngine.kt")
        self.assertIn("confirmAndRun(", engine)
        self.assertIn("explicitTap: Boolean", engine)
        self.assertNotIn("arbitraryShell", engine)

    def test_13_adaptive_gestures_cannot_use_privileged_injection(self):
        pending(13)
        device = source(CORE / "DeviceActionPolicy.kt")
        self.assertIn("PRESS_SCREEN", device)
        self.assertIn("else -> false", device)
        _, perms = permissions()
        self.assertNotIn("android.permission.INJECT_EVENTS", perms)

    def test_14_change_monitor_cannot_enable_background_capture(self):
        pending(14)
        manifest, _ = permissions()
        service = next((x for x in manifest.findall(".//service")
                        if x.get(ANDROID+"name") == ".ScreenObservationService"), None)
        self.assertIsNotNone(service)
        self.assertEqual("false", service.get(ANDROID+"enabled"))
        self.assertEqual("false", service.get(ANDROID+"exported"))

    def test_16_notifications_without_listener_or_exfiltration(self):
        pending(16)
        manifest, perms = permissions()
        self.assertIn("android.permission.POST_NOTIFICATIONS", perms)
        self.assertNotIn("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", perms)
        self.assertFalse(any("NotificationListenerService" in
                             (node.get(ANDROID+"name") or "")
                             for node in manifest.findall(".//service")))

    def test_19_offline_routines_are_fixed_and_never_script_execution(self):
        pending(19)
        engine = source(CORE / "SafeAutomationEngine.kt")
        self.assertIn("OPEN_TERMUX", engine)
        self.assertIn("no ejecutar scripts", engine)
        self.assertNotIn("Runtime.getRuntime().exec(", engine)

    def test_29_build_performance_not_fabricated_as_benchmark(self):
        pending(29)
        ci = source(ROOT / ".github/workflows/android-shell.yml")
        self.assertIn(":app:testDebugUnitTest", ci)
        self.assertIn(":app:assembleDebug", ci)
        self.assertNotIn("BENCHMARK_P95_MS=", ci)

    def test_36_rollback_not_implied_by_emergency_stop(self):
        pending(36)
        engine = source(CORE / "SafeAutomationEngine.kt")
        self.assertIn("SafeAutomationOutcome.STOPPED", engine)
        self.assertNotIn("SafeAutomationOutcome.ROLLED_BACK", engine)

    def test_43_multiagent_cannot_gain_github_merge_privilege(self):
        pending(43)
        policy = source(CORE / "DeviceActionPolicy.kt")
        self.assertIn("GITHUB_MERGE", policy)
        self.assertIn("else -> false", policy)

    def test_44_scheduler_does_not_start_unconsented_microphone(self):
        pending(44)
        manifest, _ = permissions()
        self.assertNotIn("BOOT_COMPLETED", source(APP / "AndroidManifest.xml"))
        self.assertFalse(any("Alarm" in (x.get(ANDROID+"name") or "")
                             for x in manifest.findall(".//receiver")))

    def test_45_sync_must_not_be_construed_from_encrypted_local_memory(self):
        pending(45)
        _, perms = permissions()
        self.assertNotIn("android.permission.INTERNET", perms)
        self.assertIn("android:allowBackup=\"false\"",
                      source(APP / "AndroidManifest.xml"))

    def test_46_pc_integration_cannot_run_untrusted_shell(self):
        pending(46)
        device = source(CORE / "DeviceActionPolicy.kt")
        self.assertIn("GITHUB_MERGE", device)
        self.assertIn("LAUNCH_OTHER_APP", device)
        self.assertIn("else -> false", device)
        self.assertNotIn("pcShell.execute(", source(CORE / "SafeAutomationEngine.kt"))


if __name__ == "__main__":
    unittest.main()
