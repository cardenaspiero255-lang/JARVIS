"""Boundary tests for deterministic automation and privileged integrations."""
import unittest
from pathlib import Path

APP = Path(__file__).resolve().parents[1] / "apps/android/app"
SRC = APP / "src/main/java/com/cardenaspiero255/jarvis"
CORE = SRC / "core/SafeAutomationEngine.kt"


class SafeAutomationContract(unittest.TestCase):
    def test_exact_voice_allowlist_and_no_implicit_execution(self):
        code = CORE.read_text()
        for phrase in (
            "jarvis revisa shizuku",
            "jarvis revisa dhizuku",
            "jarvis abre ajustes",
            "jarvis abre termux",
            "jarvis abre owndroid",
        ):
            self.assertIn(phrase, code)
        self.assertIn('else -> null', code)
        self.assertIn('pending != routine', code)
        self.assertIn('explicitTap', code)
        self.assertIn('emergencyStopped', code)
        self.assertNotIn('ProcessBuilder', code)
        self.assertNotIn('Runtime.getRuntime()', code)

    def test_companion_sdks_require_real_grants_and_no_owner_provisioning(self):
        gradle = (APP / "build.gradle.kts").read_text()
        adapter = (SRC / "CompanionActionAdapters.kt").read_text()
        for sdk in (
            'dev.rikka.shizuku:api:13.1.5',
            'io.github.iamr0s:Dhizuku-API:2.5.4',
        ):
            self.assertIn(sdk, gradle)
        for gate in ('Shizuku.pingBinder()', 'Shizuku.checkSelfPermission()',
                     'Dhizuku.init(activity)', 'Dhizuku.isPermissionGranted()'):
            self.assertIn(gate, adapter)
        for forbidden in ('setDeviceOwner(', 'set-device-owner',
                          'wipeData(', 'clearApplicationUserData('):
            self.assertNotIn(forbidden, adapter)

    def test_termux_only_dispatches_one_hardcoded_readonly_command(self):
        adapter = (SRC / "CompanionActionAdapters.kt").read_text()
        manifest = (APP / "src/main/AndroidManifest.xml").read_text()
        self.assertIn('com.termux.permission.RUN_COMMAND', manifest)
        self.assertIn('checkSelfPermission("com.termux.permission.RUN_COMMAND")', adapter)
        self.assertIn('"/data/data/com.termux/files/usr/bin/id"', adapter)
        self.assertIn('emptyArray<String>()', adapter)
        self.assertNotIn('com.termux.RUN_COMMAND_STDIN', adapter)
        self.assertNotIn('com.termux.RUN_COMMAND_ARGUMENTS", arrayOf', adapter)

    def test_owndroid_only_supports_explicit_lock_and_nonpersistent_key(self):
        adapter = (SRC / "CompanionActionAdapters.kt").read_text()
        ui = (SRC / "CompanionCenterActivity.kt").read_text()
        self.assertIn('com.bintianqi.owndroid.action.LOCK', adapter)
        self.assertIn('com.bintianqi.owndroid.ApiReceiver', adapter)
        self.assertIn('key.isBlank()', adapter)
        self.assertIn('"CONFIRMAR BLOQUEO"', ui)
        self.assertIn('ephemeralKey', ui)
        for forbidden in ('SharedPreferences', 'putString("key"', 'CLEAR_APP_STORAGE',
                          'REBOOT', 'HIDE', 'Runtime.getRuntime()'):
            self.assertNotIn(forbidden, adapter)

    def test_original_launcher_safety_preserved(self):
        manifest = (APP / "src/main/AndroidManifest.xml").read_text()
        home = (SRC / "JarvisHomeActivity.kt").read_text()
        self.assertIn('android:name=".JarvisHomeActivity"', manifest)
        self.assertIn('android:name=".CompanionCenterActivity"', manifest)
        self.assertIn('android:exported="false"', manifest)
        self.assertNotIn("android.permission.QUERY_ALL_PACKAGES", manifest)
        self.assertIn('Settings.ACTION_HOME_SETTINGS', home)
        self.assertNotIn('setPreferredActivity(', home)


if __name__ == "__main__":
    unittest.main()
