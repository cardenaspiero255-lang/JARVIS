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

    def test_device_only_path_never_automatically_rearms_or_contacts_network(self):
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
            "HttpURLConnection", "HttpsURLConnection", "startListeningLoop",
            "AlarmManager", "WorkManager", "BOOT_COMPLETED",
            "requestAiReply(", "performGlobalAction(", "onPartialResults(partialResults: Bundle?) {",
        ):
            self.assertNotIn(forbidden, service)
        self.assertIn("current.startListening(", service)
        self.assertEqual(2, service.count("current.startListening("))

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

    def test_offline_spanish_is_selected_from_actual_android_support(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text(encoding="utf-8")
        policy = (SRC / "core/OfflineSpanishSupportPolicy.kt").read_text(encoding="utf-8")
        self.assertIn("checkRecognitionSupport(", service)
        self.assertIn("installedOnDeviceLanguages", service)
        self.assertIn("supportedOnDeviceLanguages", service)
        self.assertIn("spanishPolicy.selectInstalled(", service)
        self.assertIn("SpanishAvailability.DOWNLOAD_REQUIRED", service)
        self.assertIn("SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED", service)
        self.assertNotIn('putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")', service)
        self.assertIn('fun errorReason(errorCode: Int)', policy)
        self.assertNotIn("android.permission.INTERNET", (ROOT / "AndroidManifest.xml").read_text())

    def test_system_recognizer_requires_separate_visible_consent_and_no_implicit_fallback(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text(encoding="utf-8")
        activity = (SRC / "MainActivity.kt").read_text(encoding="utf-8")
        self.assertIn('ACTION_START_SYSTEM', service)
        self.assertIn('SpeechRecognizer.createSpeechRecognizer(this)', service)
        self.assertIn('SpeechRecognizer.createOnDeviceSpeechRecognizer(this)', service)
        self.assertIn('systemRecognizerRequested', service)
        self.assertIn('SpeechRecognizer.isRecognitionAvailable(this)', service)
        self.assertIn('PROBAR RECONOCIMIENTO DEL SISTEMA', activity)
        self.assertIn('puede usar Internet', activity)
        self.assertIn('HandsFreePreviewService.ACTION_START_SYSTEM', activity)
        self.assertIn('setPositiveButton("ACEPTAR Y PROBAR"', activity)
        self.assertIn('explicitUserTap = true', service)
        self.assertIn('systemRequested -> systemAvailable', service)
        self.assertIn('systemMode -> systemAvailable', activity)
        self.assertIn('else -> offline', service)
        self.assertIn('else -> true // native Vosk model bundled with the APK', activity)
        self.assertIn('if (systemRecognizerRequested)', service)
        self.assertIn('if (systemRecognizerRequested &&', service)
        self.assertIn('DIAGNÓSTICO MICRO Y VOZ · 4 S', activity)
        # Offline recognizer must never silently reroute to the system provider.
        self.assertNotIn('if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED) systemRecognizerRequested = true', service)

    def test_default_60s_session_uses_native_spanish_with_pinned_model(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text(encoding="utf-8")
        activity = (SRC / "MainActivity.kt").read_text(encoding="utf-8")
        native = (SRC / "NativeSpanishSpeechRunner.kt").read_text(encoding="utf-8")
        installer = (SRC / "NativeSpanishModelStore.kt").read_text(encoding="utf-8")
        gradle = (ROOT.parents[1] / "build.gradle.kts").read_text(encoding="utf-8")
        manifest = (ROOT / "AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn("ACTION_START_NATIVE", service)
        self.assertIn("startNativeSpanishPreview()", service)
        self.assertIn("main.postDelayed(nativeStartupTimeout, 90_000L)", service)
        self.assertIn("main.postDelayed(timeout, 60_000L)", service)
        self.assertIn("HandsFreePreviewService.ACTION_START_NATIVE", activity)
        self.assertIn("nativeRequested -> true", service)
        self.assertIn("NativeSpanishSpeechRunner(", service)
        self.assertIn("NativeSpanishModelStore(context).ensureInstalled", native)
        self.assertIn("AudioRecord(", native)
        self.assertIn("Recognizer(model, 16_000.0f)", native)
        self.assertIn("SpeakerEvidence.UNAVAILABLE", native)
        self.assertIn("ownerOnly = false", native)
        self.assertIn("vosk-android:0.3.47", gradle)
        self.assertIn("bundleOfflineSpanish", gradle)
        self.assertIn("09b239888f633ef2f0b4e09736e3d9936acfd810bc65d53fad45261762c6511f", gradle)
        self.assertIn("MessageDigest.getInstance(\"SHA-256\")", installer)
        self.assertIn("Invalid model archive path", installer)
        self.assertIn("MAX_EXPANDED_BYTES", installer)
        self.assertNotIn("android.permission.INTERNET", manifest)
        self.assertNotIn("SpeechRecognizer.create", native)
        self.assertNotIn("HttpURLConnection", native)
        self.assertNotIn("FileOutputStream", native)

    def test_hardware_mic_and_offline_tts_can_be_diagnosed_without_asr(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text(encoding="utf-8")
        activity = (SRC / "MainActivity.kt").read_text(encoding="utf-8")
        meter = (SRC / "core/AudioInputMeter.kt").read_text(encoding="utf-8")
        self.assertIn('const val ACTION_DIAGNOSE', service)
        self.assertIn('diagnostic -> true', service)
        self.assertIn('startMicrophoneDiagnostic()', service)
        self.assertIn('AudioRecord(', service)
        self.assertIn('input.read(', service)
        self.assertIn('input?.release()', service)
        self.assertIn('micDiagnosticSummary', service)
        self.assertIn('speakThenStop("Prueba de voz.', service)
        self.assertIn('HandsFreePreviewService.ACTION_DIAGNOSE', activity)
        self.assertIn('DIAGNÓSTICO MICRO Y VOZ · 4 S', activity)
        self.assertNotIn('privateMemory.save(transcript)', service)
        self.assertNotIn('FileOutputStream', service)
        self.assertNotIn('ByteArrayOutputStream', service)
        self.assertIn('no se recibieron muestras', meter)

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
