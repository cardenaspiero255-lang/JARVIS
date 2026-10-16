"""JARVIS OS must remain a reversible Android HOME application over Funtouch OS."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "apps/android/app/src/main"
JAVA = SRC / "java/com/cardenaspiero255/jarvis"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class JarvisOsLauncherContract(unittest.TestCase):
    def test_home_is_opt_in_and_regular_main_launcher_is_retained(self):
        root = ET.parse(SRC / "AndroidManifest.xml").getroot()
        activities = {
            a.get(ANDROID + "name"): a
            for a in root.findall("./application/activity")
        }
        self.assertIn(".MainActivity", activities)
        self.assertIn(".JarvisHomeActivity", activities)
        main = activities[".MainActivity"]
        home = activities[".JarvisHomeActivity"]
        self.assertEqual("true", home.get(ANDROID + "exported"))
        assert home is not None
        home_intents = home.findall("intent-filter")
        self.assertEqual(1, len(home_intents))
        categories = {
            c.get(ANDROID + "name")
            for f in home_intents
            for c in f.findall("category")
        }
        self.assertIn("android.intent.category.HOME", categories)
        self.assertIn("android.intent.category.DEFAULT", categories)
        main_categories = {
            c.get(ANDROID + "name")
            for f in main.findall("intent-filter")
            for c in f.findall("category")
        }
        self.assertIn("android.intent.category.LAUNCHER", main_categories)

    def test_no_new_privileged_permissions_or_broad_app_inventory(self):
        root = ET.parse(SRC / "AndroidManifest.xml").getroot()
        manifest = (SRC / "AndroidManifest.xml").read_text()
        permissions = {
            p.get(ANDROID + "name")
            for p in root.findall("uses-permission")
        }
        self.assertNotIn("android.permission.QUERY_ALL_PACKAGES", permissions)
        self.assertNotIn("android.permission.INTERNET", permissions)
        self.assertNotIn("android.permission.WRITE_SECURE_SETTINGS", permissions)
        self.assertNotIn("android.permission.SYSTEM_ALERT_WINDOW", permissions)
        self.assertNotIn("android.permission.PACKAGE_USAGE_STATS", permissions)
        self.assertNotIn("android.permission.REQUEST_INSTALL_PACKAGES", permissions)
        self.assertIn("android.intent.category.LAUNCHER", manifest)
        self.assertEqual("false", root.find("application").get(ANDROID + "allowBackup"))

    def test_debug_preview_installs_beside_existing_jarvis_without_replacing_it(self):
        gradle = (ROOT / "apps/android/app/build.gradle.kts").read_text()
        home = (JAVA / "JarvisHomeActivity.kt").read_text()
        manifest = (SRC / "AndroidManifest.xml").read_text()
        self.assertIn('applicationId = "com.cardenaspiero255.jarvis"', gradle)
        self.assertIn('applicationIdSuffix = ".osv2preview"', gradle)
        self.assertIn('resValue("string", "jarvis_application_label", "JARVIS OS V2 PREVIEW")', gradle)
        self.assertIn('resValue("string", "jarvis_application_label", "JARVIS OMEGA")', gradle)
        self.assertIn('android:label="@string/jarvis_application_label"', manifest)
        self.assertIn('packageManager.getLaunchIntentForPackage(ORIGINAL_JARVIS_PACKAGE)', home)
        self.assertIn('const val ORIGINAL_JARVIS_PACKAGE = "com.cardenaspiero255.jarvis"', home)

    def test_reversible_tap_only_actions_no_root_or_flashing(self):
        home = (JAVA / "JarvisHomeActivity.kt").read_text()
        main = (JAVA / "MainActivity.kt").read_text()
        self.assertIn('primaryButton("PROBAR JARVIS OS — SIN CAMBIAR FUNTOUCH")', main)
        self.assertIn("Settings.ACTION_HOME_SETTINGS", home)
        self.assertIn("Settings.ACTION_SETTINGS", home)
        self.assertIn("queryIntentActivities(", home)
        self.assertIn("JarvisHomeCatalog.visible(", home)
        self.assertIn("setOnClickListener { action() }", home)
        self.assertIn("setClassName(app.packageName, app.activityClassName)", home)
        for forbidden in (
            "Runtime.getRuntime()", "ProcessBuilder(", "su -c", "fastboot",
            "setPreferredActivity(", "addPersistentPreferredActivity(",
            "performGlobalAction(", "MediaProjectionManager(",
            "AccessibilityService", "requestPermissions(",
        ):
            self.assertNotIn(forbidden, home)

    def test_not_double_launcher_or_self_installing_new_os(self):
        root = ET.parse(SRC / "AndroidManifest.xml").getroot()
        activities = root.findall("./application/activity")
        home_owners = []
        launcher_owners = []
        for activity in activities:
            categories = {
                child.get(ANDROID + "name")
                for intent_filter in activity.findall("intent-filter")
                for child in intent_filter.findall("category")
            }
            if "android.intent.category.HOME" in categories:
                home_owners.append(activity.get(ANDROID + "name"))
            if "android.intent.category.LAUNCHER" in categories:
                launcher_owners.append(activity.get(ANDROID + "name"))
        self.assertEqual([".JarvisHomeActivity"], home_owners)
        self.assertEqual([".MainActivity"], launcher_owners)
        home = (JAVA / "JarvisHomeActivity.kt").read_text()
        self.assertIn("Funtouch OS", home)
        self.assertIn("Reversible HOME launcher", home)


if __name__ == "__main__":
    unittest.main()
