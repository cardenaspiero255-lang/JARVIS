"""Diagnostic APK must report physical evidence without claiming device certification."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "apps/android/app/src/main/java/com/cardenaspiero255/jarvis"
MANIFEST = ROOT / "apps/android/app/src/main/AndroidManifest.xml"

class VivoOneInstallDiagnosticContract(unittest.TestCase):
    def test_visible_explicit_one_install_entry(self):
        main = (SRC / "MainActivity.kt").read_text()
        screen = (SRC / "VivoDiagnosticActivity.kt").read_text()
        manifest = MANIFEST.read_text()
        self.assertIn("VivoDiagnosticActivity::class.java", main)
        self.assertIn('android:name=".VivoDiagnosticActivity"', manifest)
        self.assertIn('android:exported="false"', manifest)
        self.assertIn("FLAG_SECURE", screen)
        self.assertIn("Intent.ACTION_SEND", screen)
        self.assertIn("text/plain", screen)
        self.assertIn("startForegroundService", screen)
        self.assertIn("EXTRA_TAPPED", screen)
        self.assertIn("ACTION_DIAGNOSE", screen)
        self.assertIn("ACTION_START_NATIVE", screen)
        self.assertIn("ACTION_START_LONG_NATIVE", screen)
        self.assertIn("ACTION_STOP", screen)
        self.assertIn("OwnerVoiceEnrollmentActivity::class.java", screen)
        self.assertIn("LocalGgufInference", screen)
        self.assertIn("SystemClock.elapsedRealtime", screen)
        self.assertIn("lockscreen_off_samples", screen)
        self.assertIn("Debug.MemoryInfo", screen)
        self.assertIn("DeviceHealthReader", screen)

    def test_no_unconsented_capture_or_false_certification(self):
        screen = (SRC / "VivoDiagnosticActivity.kt").read_text()
        report = (SRC / "core/VivoDiagnosticReport.kt").read_text()
        self.assertIn("checkSelfPermission", screen)
        self.assertIn("requestPermissions", screen)
        self.assertIn("USER_REPORTED", report)
        self.assertIn("NO_CERTIFICADO", report)
        self.assertIn("NO_PROBADO", report)
        self.assertIn("MICROPHONE", report)
        self.assertIn("WAKE", report)
        self.assertIn("LOCKSCREEN", report)
        self.assertIn("OWNER", report)
        self.assertIn("QWEN", report)
        self.assertIn("TTS", report)
        self.assertNotIn("RECORD_AUDIO", MANIFEST.read_text().split("</manifest>")[0].split("<application")[1])
        for prohibited in ('android.permission.INTERNET', 'READ_PHONE_STATE', 'READ_CONTACTS',
                           'MANAGE_EXTERNAL_STORAGE', 'MediaRecorder(', 'AudioRecord('):
            self.assertNotIn(prohibited, screen)
        self.assertNotIn("MODE_WORLD_READABLE", screen)
        self.assertNotIn("startForegroundService", screen.split("override fun onCreate")[0])

    def test_real_stop_ack_and_qwen_evidence_preservation(self):
        activity = (SRC / "VivoDiagnosticActivity.kt").read_text()
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        report = (SRC / "core/VivoDiagnosticReport.kt").read_text()
        gate = (SRC / "core/VivoDiagnosticStopGate.kt").read_text()
        self.assertIn('EXTRA_STOP_DIAGNOSTIC_REQUEST_ID', service)
        self.assertIn('stop_ack_id', service)
        self.assertIn('stop_ack_id', activity)
        self.assertIn('STOP_SERVICIO_FINALIZADO', activity)
        self.assertIn('STOP_NO_CONFIRMADO', activity)
        self.assertIn('pollStopReceipt', activity)
        self.assertIn('qwenWasActive', activity)
        self.assertIn('if (qwenWasActive)', activity)
        self.assertIn('!serviceRunning && !servicePersistedActive', gate)
        self.assertIn('CONTRADICCION_QWEN', report)
        self.assertIn('STOP_ejecución=', report)

    def test_screenoff_recognized_commands_are_separate_from_pcm(self):
        runner = (SRC / "NativeSpanishSpeechRunner.kt").read_text()
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        report = (SRC / "core/VivoDiagnosticReport.kt").read_text()
        screen = (SRC / "VivoDiagnosticActivity.kt").read_text()
        self.assertIn("LockScreenCaptureAccumulator", runner)
        self.assertIn("screenOffFrames.observe(received, interactiveAtCapture)", runner)
        self.assertIn("onAudioProgress(capturedWhileScreenOff, progressNow)", runner)
        self.assertIn("screenOffAtRecognition", service)
        self.assertIn("lockscreen_time_commands", service)
        self.assertIn("lockscreen_stop_commands", service)
        self.assertIn("Comandos_HORA_recibidos_pantalla_apagada=", report)
        self.assertIn("Comandos_STOP_recibidos_pantalla_apagada=", report)
        self.assertIn('lockscreenTimeCommandCount = audio.getInt', screen)
        self.assertIn('IMPORTAR GGUF PRIVADO', screen)
        self.assertIn('NO contiene los 491 MB', screen)
        self.assertNotIn("android.permission.INTERNET", screen)

    def test_short_mic_probe_must_not_erase_last_extended_session(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        report = (SRC / "core/VivoDiagnosticReport.kt").read_text()
        self.assertIn("if (extendedRequested)", service)
        self.assertIn('remove("lockscreen_off_samples")', service)
        self.assertIn('remove("lockscreen_time_commands")', service)
        self.assertIn('remove("lockscreen_stop_commands")', service)
        self.assertIn('lockscreen_last_extended', service)
        self.assertIn('LOCKSCREEN_NO_CORROBORADO', report)
        self.assertIn('STOP_SIN_SESION_ACTIVA', report)

    def test_debug_apk_is_uploaded_from_ci(self):
        workflow = (ROOT / ".github/workflows/android-shell.yml").read_text()
        self.assertIn("jarvis-omega-debug-apk", workflow)
        self.assertIn("app-debug.apk", workflow)

if __name__ == "__main__":
    unittest.main()
