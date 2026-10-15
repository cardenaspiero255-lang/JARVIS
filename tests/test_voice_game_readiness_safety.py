"""Voice-triggered game advice must stay read-only and use visible user profile."""
from pathlib import Path
import unittest

CORE=Path(__file__).resolve().parents[1]/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class VoiceGameReadinessSafetyContracts(unittest.TestCase):
    def test_voice_branch_only_reads_current_android_signals(self):
        s=(CORE/"MainActivity.kt").read_text()
        segment=s.split("VoiceAction.READ_GAME_READINESS -> {",1)[1].split(
            "VoiceAction.OPEN_ACCESSIBILITY_SETTINGS ->",1)[0]
        self.assertIn("DeviceHealthReader(this).read()",segment)
        self.assertIn("GameReadinessAdvisor.assess(reading, gamingPreference)",segment)
        self.assertIn("GameReadinessAdvisor.format(report)",segment)
        for forbidden in ("startActivity(", "GameHubLauncher(", "setEmergencyStop(",
                          "goHome(", "speak", "privateMemory.save(", "requestPermissions("):
            self.assertNotIn(forbidden,segment)

    def test_parser_uses_fixed_allowlist_not_keyword_matching(self):
        parser=(CORE/"core/VoiceCommandParser.kt").read_text()
        self.assertIn("READ_GAME_READINESS",parser)
        self.assertIn('"jarvis evalua juego"',parser)
        self.assertIn("-> VoiceAction.READ_GAME_READINESS",parser)
        self.assertNotIn('contains("jugar")',parser)

    def test_no_extra_permissions_or_network_for_preflight(self):
        manifest=Path(__file__).resolve().parents[1]/"apps/android/app/src/main/AndroidManifest.xml"
        s=manifest.read_text()
        for permission in ("android.permission.WRITE_SETTINGS",
                           "android.permission.PACKAGE_USAGE_STATS"):
            self.assertNotIn(permission,s)
        self.assertNotIn("android.permission.INTERNET",s)

if __name__=="__main__":
    unittest.main()
