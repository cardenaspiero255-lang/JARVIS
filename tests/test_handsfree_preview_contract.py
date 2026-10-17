"""Phase-1 hands-free privacy contract; physical Vivo certification is separate."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "apps/android/app/src/main"
SRC = ROOT / "java/com/cardenaspiero255/jarvis"
NS = "{http://schemas.android.com/apk/res/android}"


class HandsFreePreviewContract(unittest.TestCase):
    def test_preview_install_is_separate_from_existing_qwen_debug(self):
        gradle = (ROOT.parents[1] / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('applicationIdSuffix = ".handsfreepreview"', gradle)
        self.assertIn('resValue("string", "jarvis_application_label", "JARVIS MANOS LIBRES TEST")', gradle)
        activity = (SRC / "MainActivity.kt").read_text(encoding="utf-8")
        self.assertIn("handsFreePreviewStatus.text = HandsFreePreviewService.readDiagnostic(this)", activity)

    def test_only_explicit_unexported_microphone_service(self):
        manifest = ET.parse(ROOT / "AndroidManifest.xml").getroot()
        services = manifest.findall("./application/service")
        mic = [s for s in services if s.get(NS + "foregroundServiceType") == "microphone"]
        self.assertEqual(1, len(mic))
        self.assertEqual(".HandsFreePreviewService", mic[0].get(NS + "name"))
        self.assertEqual("false", mic[0].get(NS + "exported"))
        self.assertEqual("true", mic[0].get(NS + "stopWithTask"))
        self.assertFalse(any(
            f.get(NS + "name", "").endswith("BOOT_COMPLETED")
            for f in manifest.findall("./application/receiver/intent-filter/action")
        ))

    def test_never_uses_cloud_recognition_or_automatically_rearms(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text(encoding="utf-8")
        for expected in (
            "SpeechRecognizer.createOnDeviceSpeechRecognizer(this)",
            "SpeechRecognizer.isOnDeviceRecognitionAvailable(this)",
            "START_NOT_STICKY",
            "startForeground(",
            "60_000L",
            "SpeakerEvidence.UNAVAILABLE",
            "stopSelf()",
            "getService(",
            "SpeechRecognizer.RESULTS_RECOGNITION",
        ):
            self.assertIn(expected, service)
        for forbidden in (
            "SpeechRecognizer.createSpeechRecognizer(this)",
            "HttpURLConnection", "HttpsURLConnection", "startListeningLoop",
            "AlarmManager", "WorkManager", "BOOT_COMPLETED",
            "requestAiReply(", "performGlobalAction(", "onPartialResults(partialResults: Bundle?) {",
        ):
            self.assertNotIn(forbidden, service)
        self.assertIn("current.startListening(", service)
        self.assertEqual(1, service.count("current.startListening("))

    def test_screen_lock_does_not_kill_local_asr_at_first_silence(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text(encoding="utf-8")
        policy = (SRC / "core/HandsFreePreviewPolicy.kt").read_text(encoding="utf-8")
        manifest = (ROOT / "AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn("recoverFromAsrError(error)", service)
        self.assertIn("PreviewAsrDecision.STOP", service)
        self.assertIn("PreviewAsrDecision.RETRY", policy)
        self.assertIn("main.postDelayed(task, decision.delayMillis)", service)
        self.assertIn("recognizerGeneration++", service)
        self.assertIn("wakeLock?.release()", service)
        self.assertIn("PowerManager.PARTIAL_WAKE_LOCK", service)
        self.assertIn('android.permission.WAKE_LOCK', manifest)
        self.assertIn("6, 7 -> PreviewAsrResult(", policy)
        self.assertIn("remainingMillis", policy)
        self.assertIn('putBoolean("active", active).commit()', service)
        self.assertIn("PreviewDiagnosticSummary.visible(", service)
        self.assertNotIn("privateMemory.save(transcript)", service)

    def test_owner_match_is_not_assumed_and_emergency_stop_revokes(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text(encoding="utf-8")
        policy = (SRC / "core/HandsFreePreviewPolicy.kt").read_text(encoding="utf-8")
        activity = (SRC / "MainActivity.kt").read_text(encoding="utf-8")
        self.assertIn("ownerOnly && speaker != SpeakerEvidence.MATCHED", policy)
        self.assertIn("SpeakerEvidence.UNAVAILABLE", service)
        self.assertIn("stopHandsFreePreview()", activity)
        self.assertIn("SessionLockChannel.VOICE_REQUEST", activity)
        self.assertIn("requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS)", activity)
        self.assertIn("REQUEST_PREVIEW_NOTIFICATIONS", activity)
        self.assertIn("ACTION_STOP", service)
        self.assertNotIn("android.permission.INTERNET", (ROOT / "AndroidManifest.xml").read_text())


if __name__ == "__main__":
    unittest.main()
