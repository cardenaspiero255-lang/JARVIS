"""Regression coverage for user-approved voice handoff and result validation."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "apps/android/app"
MAIN = ROOT / "src/main/java/com/cardenaspiero255/jarvis"


class CompanionVoiceHandoffContract(unittest.TestCase):
    def test_voice_never_executes_unapproved_routines(self):
        main = (MAIN / "MainActivity.kt").read_text()
        center = (MAIN / "CompanionCenterActivity.kt").read_text()
        assert "SafeAutomationVoiceRouter.parse(transcript)" in main
        assert 'setPositiveButton("REVISAR PLAN")' in main
        assert "Intent(this, CompanionCenterActivity::class.java)" in main
        assert "EXTRA_ROUTINE_PREVIEW" in main
        assert "firstOrNull { it.name == request }" in center
        assert "confirmRoutine(routine)" in center
        assert 'setPositiveButton("AUTORIZAR UNA VEZ")' in center
        assert "automation.confirmAndRun(routine, explicitTap = true)" in center
        assert "getStringExtra(EXTRA_ROUTINE_PREVIEW)" in center
        assert "if (savedInstanceState == null)" in center

    def test_verified_termux_result_only_with_explicit_private_one_shot_callback(self):
        manifest = (ROOT / "src/main/AndroidManifest.xml").read_text()
        adapters = (MAIN / "CompanionActionAdapters.kt").read_text()
        receiver = (MAIN / "TermuxResultReceiver.kt").read_text()
        assert 'android:name=".TermuxResultReceiver"' in manifest
        assert 'android:exported="false"' in manifest
        for key in ("com.termux.RUN_COMMAND_PENDING_INTENT", "PendingIntent.FLAG_ONE_SHOT",
                    "PendingIntent.getBroadcast", "com.termux.RUN_COMMAND_BACKGROUND", "true"):
            assert key in adapters
        assert 'getBundleExtra("result")' in receiver
        for key in ('"exitCode"', '"err"', '"stdout"'):
            assert key in receiver
        assert "TermuxResultSession.tracker.receive" in receiver
        assert "TermuxResultState.VERIFIED" in (MAIN / "CompanionCenterActivity.kt").read_text()

    def test_owndroid_lock_observation_does_not_claim_verified_execution(self):
        center = (MAIN / "CompanionCenterActivity.kt").read_text()
        policy = (MAIN / "core/CompanionResultPolicy.kt").read_text()
        assert "isDeviceLocked" in center
        assert "OwnDroidLockEvidence.evaluate" in center
        assert "origen no comprobable" in center
        assert "beforeLocked -> OwnDroidLockState.INCONCLUSIVE" in policy
        assert "OBSERVED_LOCKED" in policy
        assert "android.permission.QUERY_ALL_PACKAGES" not in (
            ROOT / "src/main/AndroidManifest.xml").read_text()


if __name__ == "__main__":
    unittest.main()
