"""The privacy lock must blank all on-screen one-shot diagnostic readouts."""
from pathlib import Path
import unittest

JAVA=Path(__file__).resolve().parents[1]/"apps/android/app/src/main/java/com/cardenaspiero255/jarvis"

class SessionReadoutPrivacyContracts(unittest.TestCase):
    def test_privacy_lock_has_independent_diagnostic_shutdown_channel(self):
        activity=(JAVA/"MainActivity.kt").read_text()
        enum=(JAVA/"core/SessionLockCoordinator.kt").read_text()
        lock=activity.split("private fun lockSessionByTap()",1)[1].split(
            "private fun readCapabilities()",1)[0]
        self.assertIn("LOCAL_DIAGNOSTICS",enum)
        self.assertIn("SessionLockChannel.LOCAL_DIAGNOSTICS to {",lock)
        self.assertIn("clearVisibleReadouts()",lock)

    def test_all_readouts_are_privately_owned_and_redacted_on_background(self):
        src=(JAVA/"MainActivity.kt").read_text()
        for field in ("healthStatusText","capabilityStatusText","gameReadinessText"):
            self.assertIn("private lateinit var "+field+": TextView",src)
            self.assertIn("::"+field+".isInitialized",src)
        clear=src.split("private fun clearVisibleReadouts()",1)[1].split(
            "private fun readCapabilities()",1)[0]
        for name in ("SensitiveReadout.CAPABILITIES","SensitiveReadout.DEVICE_HEALTH",
                     "SensitiveReadout.GAME_READINESS"):
            self.assertIn(name,clear)
        lifecycle=src.split("override fun onStop()",1)[1].split(
            "override fun onDestroy()",1)[0]
        self.assertIn("clearVisibleReadouts()",lifecycle)
        self.assertNotIn("privateMemory.eraseByTap()",clear)

    def test_redaction_does_not_store_or_transmit_readouts(self):
        s=(JAVA/"core/ReadoutRedactionPolicy.kt").read_text()
        for forbidden in ("SharedPreferences","FileOutputStream","HttpURLConnection",
                          "java.net.","startActivity(","sendBroadcast("):
            self.assertNotIn(forbidden,s)

if __name__=="__main__":
    unittest.main()
