"""Enforce least-privilege design of the Shizuku remote diagnostic."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
SERVICE = SRC / "JarvisShizukuReadOnlyService.kt"
CLIENT = SRC / "ShizukuSystemReadOnlyClient.kt"
POLICY = SRC / "core/ShizukuFixedProbePolicy.kt"
UI = SRC / "CompanionCenterActivity.kt"
AIDL = ROOT / "apps/android/app/src/main/aidl/com/cardenaspiero255/jarvis/IJarvisShizukuReadOnly.aidl"
GRADLE = ROOT / "apps/android/app/build.gradle.kts"


class ShizukuReadOnlyContract(unittest.TestCase):
    def test_only_two_fixed_commands_and_no_shell_interpreter(self):
        code = POLICY.read_text()
        self.assertIn('"/system/bin/getprop", "ro.build.version.release"', code)
        self.assertIn('"/system/bin/pm", "path", "android"', code)
        self.assertEqual(2, code.count("listOf(\"/system/bin/"))
        service = SERVICE.read_text()
        self.assertIn('ShizukuFixedProbe.fromCode(fixedCode)', service)
        self.assertIn('ShizukuFixedProbePolicy.isAllowedUid(Process.myUid())', service)
        self.assertNotIn('"/system/bin/sh"', code + service)
        self.assertNotIn('"su"', code + service)
        self.assertNotIn('ProcessBuilder(user', service)
        self.assertNotIn('getStringExtra', service)

    def test_binder_is_explicit_temporary_and_not_exported(self):
        client = CLIENT.read_text()
        self.assertIn('Shizuku.bindUserService', client)
        self.assertIn('Shizuku.unbindUserService', client)
        self.assertIn('.daemon(false)', client)
        self.assertIn('12_000L', client)
        self.assertIn('requestId != generation', client)
        self.assertIn('IJarvisShizukuReadOnly', AIDL.read_text())
        self.assertIn('aidl = true', GRADLE.read_text())

    def test_safety_and_user_consent(self):
        ui = UI.read_text()
        self.assertIn('SHIZUKU: DIAGNÓSTICO REAL ADB (SOLO LECTURA)', ui)
        self.assertIn('.setPositiveButton("PROBAR")', ui)
        self.assertIn('shizukuSystemReadOnly.cancel()', ui)
        self.assertNotIn('setDeviceOwner(', CLIENT.read_text())
        self.assertNotIn('wipeData(', SERVICE.read_text())


if __name__ == "__main__":
    unittest.main()
