"""Fail-closed integration contracts for optional Android companion apps."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "apps/android/app"
SRC = ROOT / "src/main/java/com/cardenaspiero255/jarvis"
CORE = SRC / "core/CompanionConnectionPolicy.kt"


class CompanionBridgeContractTest(unittest.TestCase):
    def test_only_narrow_companion_packages_visible_and_no_root(self):
        manifest = (ROOT / "src/main/AndroidManifest.xml").read_text()
        for package in (
            "moe.shizuku.privileged.api", "com.rosan.dhizuku",
            "com.bintianqi.owndroid", "com.termux"
        ):
            self.assertIn(f'<package android:name="{package}"', manifest)
        self.assertIn('android:name=".CompanionCenterActivity"', manifest)
        self.assertIn('android:exported="false"', manifest)
        self.assertNotIn("android.permission.QUERY_ALL_PACKAGES", manifest)
        self.assertNotIn("android.permission.MANAGE_DEVICE_ADMINS", manifest)

    def test_shizuku_ready_requires_live_binder_and_permission(self):
        code = (SRC / "CompanionCenterActivity.kt").read_text()
        gate = CORE.read_text()
        self.assertIn("Shizuku.pingBinder()", code)
        self.assertIn("Shizuku.checkSelfPermission()", code)
        self.assertIn("Shizuku.requestPermission(1217)", code)
        self.assertIn("if (!evidence.transportReady)", gate)
        self.assertIn("if (!evidence.authorized)", gate)

    def test_other_companions_must_not_claim_automatic_authorization(self):
        gate = CORE.read_text()
        code = (SRC / "CompanionCenterActivity.kt").read_text()
        self.assertIn("if (kind != CompanionKind.SHIZUKU)", gate)
        self.assertIn("return CompanionState.NEEDS_SETUP", gate)
        self.assertIn("fun mayExecutePrivilegedAction(): Boolean = false", gate)
        for forbidden in (
            "sendBroadcast(", "startService(", "Runtime.getRuntime()",
            "ProcessBuilder(", "DevicePolicyManager", "set-device-owner"
        ):
            self.assertNotIn(forbidden, code)

    def test_v2_navigation_adds_only_one_system_entrypoint(self):
        home = (SRC / "JarvisHomeActivity.kt").read_text()
        self.assertIn('action("CONEXIONES  /  SHIZUKU Y HERRAMIENTAS")', home)
        self.assertIn("Intent(this, CompanionCenterActivity::class.java)", home)
        self.assertIn("modules[MODULE_SISTEMA] = systemCard", home)


if __name__ == "__main__":
    unittest.main()
