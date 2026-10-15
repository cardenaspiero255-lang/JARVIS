"""Read-only Android device health feature: no polling, telemetry or new permissions."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "apps/android/app/src/main"
JAVA = ANDROID / "java/com/cardenaspiero255/jarvis"
ATTR = "{http://schemas.android.com/apk/res/android}"

class DeviceHealthPrivacyContract(unittest.TestCase):
    def test_scoped_qwen_network_and_no_sensitive_privileges(self):
        manifest = ET.parse(ANDROID / "AndroidManifest.xml").getroot()
        permissions = {x.get(ATTR + "name") for x in manifest.findall("uses-permission")}
        self.assertIn("android.permission.INTERNET", permissions)
        self.assertNotIn("HttpsURLConnection", (JAVA / "DeviceHealthReader.kt").read_text())
        self.assertNotIn("android.permission.PACKAGE_USAGE_STATS", permissions)
        self.assertNotIn("android.permission.BATTERY_STATS", permissions)
        self.assertNotIn("android.permission.READ_PHONE_STATE", permissions)

    def test_readonly_one_shot_query_requires_explicit_button(self):
        activity = (JAVA / "MainActivity.kt").read_text()
        reader = (JAVA / "DeviceHealthReader.kt").read_text()
        self.assertIn('primaryButton("CONSULTAR BATERÍA, ESTADO TÉRMICO Y RAM")', activity)
        self.assertIn("DeviceHealthReader(this).read()", activity)
        self.assertIn("registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))", reader)
        self.assertIn("manager.getMemoryInfo(info)", reader)
        self.assertIn("currentThermalStatus", reader)
        for dangerous in ("sendBroadcast(", "HttpURLConnection", "FileOutputStream",
                          "startForeground(", "AlarmManager", "WorkManager",
                          "setOnThermalStatusChangedListener"):
            self.assertNotIn(dangerous, reader)

    def test_formatter_treats_invalid_readings_as_unavailable(self):
        formatter = (JAVA / "core/DeviceHealthSnapshot.kt").read_text()
        self.assertIn("it in 0..100", formatter)
        self.assertIn("available <= total", formatter)
        self.assertIn("Estado térmico: no disponible", formatter.replace("$thermalLabel", "no disponible"))
        self.assertIn("Advertencia térmica", formatter)

if __name__ == "__main__":
    unittest.main()
