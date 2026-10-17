"""Security boundary: Internet is granted only for user-approved Qwen model weights.

The earlier fully offline manifest contract has intentionally changed. Prompts,
microphone results, vision data and private memory must NEVER be uploaded.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "apps/android/app/src/main"
JAVA = BASE / "java/com/cardenaspiero255/jarvis"
ATTR = "{http://schemas.android.com/apk/res/android}"


class OfficialQwenNetworkBoundary(unittest.TestCase):
    def test_network_permission_but_no_cleartext_or_backup(self):
        root = ET.parse(BASE / "AndroidManifest.xml").getroot()
        grants = {x.get(ATTR + "name") for x in root.findall("uses-permission")}
        self.assertIn("android.permission.INTERNET", grants)
        app = root.find("application")
        self.assertEqual("false", app.get(ATTR + "allowBackup"))
        self.assertEqual("false", app.get(ATTR + "usesCleartextTraffic"))

    def test_only_model_installer_can_open_network_connections(self):
        engine = (JAVA / "OfflineLlmEngine.kt").read_text()
        self.assertIn("fun installOfficial(", engine)
        self.assertIn("LocalLlmPolicy.MODEL_DOWNLOAD_URL", engine)
        self.assertIn("OfficialQwenDownload.install(", engine)
        self.assertIn("HttpsURLConnection", engine)
        self.assertIn("httpStatus = https.responseCode", engine)
        self.assertIn("httpStatus != 200", engine)
        self.assertIn("LocalModelIntegrity.verify(modelPath,", engine)
        self.assertIn("isCancelled = { closed || epoch != session.get() }", engine)
        # A remote connection may be opened only by the dedicated model installer,
        # never by microphone, vision, chat, screen or device-control modules.
        for path in JAVA.rglob("*.kt"):
            if path.name == "OfflineLlmEngine.kt":
                continue
            source = path.read_text()
            for forbidden in (
                "import java.net.URL", "import java.net.HttpURLConnection",
                "import javax.net.ssl.HttpsURLConnection",
            ):
                self.assertNotIn(forbidden, source, str(path))
        generating = engine.split("fun generate(", 1)[1].split(
            "private fun thermalStatus(", 1
        )[0]
        self.assertNotIn("openConnection(", generating)
        self.assertNotIn("installOfficial(", generating)

    def test_user_confirmation_and_fixed_official_sha(self):
        activity = (JAVA / "MainActivity.kt").read_text()
        policy = (JAVA / "core/LocalLlmPolicy.kt").read_text()
        copier = (JAVA / "core/OfficialQwenDownload.kt").read_text()
        self.assertIn('primaryButton("INSTALAR QWEN OFICIAL — 491 MB")', activity)
        self.assertIn(".setPositiveButton(\"INSTALAR\")", activity)
        self.assertIn("offlineLlm.installOfficial(", activity)
        self.assertIn("Qwen2.5-0.5B-Instruct-GGUF/resolve/", policy)
        self.assertIn("df5bf01389a39c743ab467d734bf501681e041c5", policy)
        self.assertIn("74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db", policy)
        self.assertIn("MessageDigest.isEqual(", copier)
        self.assertIn("StandardCopyOption.ATOMIC_MOVE", copier)
        self.assertIn("StandardCopyOption.REPLACE_EXISTING", copier)
        self.assertIn("isCancelled()", copier)
        self.assertNotIn("requestAiReply(", activity)
        self.assertFalse((JAVA / "AiGatewayClient.kt").exists())


if __name__ == "__main__":
    unittest.main()
