"""Game-readiness advisor must be local, explicit and unable to change hardware."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class GameReadinessPrivacyContracts(unittest.TestCase):
    def test_advisor_is_pure_and_has_no_android_authority(self):
        src=(JAVA/"core/GameReadinessAdvisor.kt").read_text()
        for forbidden in ("android.", "java.net.", "SharedPreferences",
                          "Runtime.getRuntime", "startActivity(", "goHome(",
                          "performGlobalAction(", "sendBroadcast(", "Thread(", "HttpURLConnection",
                          "URL(", "GameHubLauncher("):
            self.assertNotIn(forbidden,src)
        self.assertIn("GameReadinessLevel.UNKNOWN",src)
        self.assertIn("GameReadinessLevel.POSTPONE",src)
        self.assertIn("Sin certificación",src)

    def test_only_tap_triggers_one_time_read_and_no_game_launch(self):
        src=(JAVA/"MainActivity.kt").read_text()
        self.assertIn('primaryButton("EVALUAR ANTES DE JUGAR")',src)
        # Scope this assertion to the original advice button, not to the
        # separate two-tap GameHub confirmation action placed afterward.
        block=src.split('primaryButton("EVALUAR ANTES DE JUGAR")',1)[1].split(
            'primaryButton("PREPARAR APERTURA SEGURA DE GAMEHUB")',1)[0]
        self.assertIn("DeviceHealthReader(this).read()",block)
        self.assertIn("GameReadinessAdvisor.assess(snapshot, gamingPreference)",block)
        self.assertIn("GameReadinessAdvisor.format(result)",block)
        for forbidden in ("startActivity(", "GameHubLauncher(", "voiceConsent.request(",
                          "privateMemory.save(", "spokenReplyEngine.speak"):
            self.assertNotIn(forbidden,block)

    def test_no_new_permissions_needed_for_game_advisor(self):
        manifest=(ROOT/"apps/android/app/src/main/AndroidManifest.xml").read_text()
        self.assertIn("android.permission.INTERNET", manifest)
        self.assertNotIn("HttpsURLConnection",
                         (JAVA/"core/GameReadinessAdvisor.kt").read_text())
        self.assertNotIn("android.permission.PACKAGE_USAGE_STATS",manifest)
        self.assertNotIn("android.permission.WRITE_SETTINGS",manifest)

if __name__=="__main__":
    unittest.main()
