"""GameHub preflight handoff must be explicit, one-use and freshly re-evaluated."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis/MainActivity.kt"
GATE=ROOT/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis/core/GameHubPreflightGate.kt"

class VerifiedGameHubHandoff(unittest.TestCase):
    def test_two_distinct_visible_gestures_and_recheck_before_launch(self):
        src=APP.read_text()
        self.assertIn('primaryButton("PREPARAR APERTURA SEGURA DE GAMEHUB")',src)
        self.assertIn('primaryButton("CONFIRMAR APERTURA SEGURA DE GAMEHUB")',src)
        prep=src.split('primaryButton("PREPARAR APERTURA SEGURA DE GAMEHUB")',1)[1].split(
            'primaryButton("CONFIRMAR APERTURA SEGURA DE GAMEHUB")',1)[0]
        confirm=src.split('primaryButton("CONFIRMAR APERTURA SEGURA DE GAMEHUB")',1)[1].split(
            'layout.addView(label("OMEGA VISION',1)[0]
        self.assertIn("DeviceHealthReader(this).read()",prep)
        self.assertIn("gameHubPreflight.stageByTap(",prep)
        self.assertNotIn("GameHubLauncher(this).launchOnTap()",prep)
        self.assertIn("gameHubPreflight.consumeByTap(",confirm)
        self.assertIn("DeviceHealthReader(this).read()",confirm)
        self.assertIn("GameReadinessAdvisor.assess(",confirm)
        self.assertIn("GameReadinessLevel.READY",confirm)
        self.assertIn("GameReadinessLevel.CAUTION",confirm)
        self.assertIn("GameHubLauncher(this).launchOnTap()",confirm)

    def test_session_and_background_revoke_queued_handoff(self):
        src=APP.read_text()
        self.assertIn("SessionLockChannel.GAMEHUB_PREFLIGHT to",src)
        self.assertIn("gameHubPreflight.cancel()",src)
        lifecycle=src.split("override fun onStop()",1)[1].split("override fun onDestroy()",1)[0]
        self.assertIn("gameHubPreflight.cancel()",lifecycle)
        gate=GATE.read_text()
        self.assertIn("MAX_WAIT_MS = 30_000L",gate)
        for forbidden in ("SharedPreferences","FileOutputStream","HttpURLConnection","startActivity("):
            self.assertNotIn(forbidden,gate)

    def test_direct_launcher_still_rejects_other_package(self):
        source=(ROOT/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis/GameHubLauncher.kt").read_text()
        self.assertIn("resolvedPackage = resolvedPackage",source)
        self.assertIn("intent.setPackage(policy.packageName)",source)

if __name__=="__main__":
    unittest.main()
