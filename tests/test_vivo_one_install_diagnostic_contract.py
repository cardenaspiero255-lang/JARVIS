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
        self.assertIn("ApplicationInfo.FLAG_DEBUGGABLE", screen)
        self.assertIn("ApplicationInfo.FLAG_DEBUGGABLE", main)
        self.assertIn("Intent.ACTION_SEND", screen)
        self.assertIn("text/plain", screen)
        self.assertIn("startForegroundService", screen)
        self.assertIn("EXTRA_TAPPED", screen)
        self.assertIn("ACTION_DIAGNOSE", screen)
        self.assertIn("ACTION_START_NATIVE", screen)
        self.assertIn("ACTION_START_LONG_NATIVE", screen)
        self.assertIn("ACTION_START_LONG_CONVERSATION", screen)
        self.assertIn("ACTION_START_CONVERSATION", screen)
        self.assertNotIn("ACTION_START_LONG_NATIVE_QA", screen)
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
        self.assertIn("if (extendedListening)", service)
        self.assertIn('remove("lockscreen_off_samples")', service)
        self.assertIn('remove("lockscreen_time_commands")', service)
        self.assertIn('remove("lockscreen_stop_commands")', service)
        self.assertIn('lockscreen_last_extended', service)
        self.assertIn('LOCKSCREEN_NO_CORROBORADO', report)
        self.assertIn('STOP_SIN_SESION_ACTIVA', report)

    def test_current_gguf_mode_is_separate_and_report_remains_uncertified(self):
        activity=(SRC/"VivoDiagnosticActivity.kt").read_text()
        report=(SRC/"core/VivoDiagnosticReport.kt").read_text()
        runner=(SRC/"NativeSpanishSpeechRunner.kt").read_text()
        self.assertIn("ACTION_START_LONG_CONVERSATION", activity)
        self.assertIn("ACTION_START_CONVERSATION", activity)
        self.assertIn("BARGE_IN", report)
        self.assertIn("ECHO_REJECTION", report)
        self.assertIn("MIC_REVOKED", report)
        self.assertIn("NO_CERTIFICADO", report)
        self.assertIn("BargeInQuestionPolicy", runner)
        self.assertNotIn("SpeakerEvidence.MATCHED", activity)

    def test_gguf_lab_supports_explicit_private_import_without_fabricated_success(self):
        screen = (SRC / "VivoDiagnosticActivity.kt").read_text()
        classifier = (SRC / "core/VivoGgufProbePolicy.kt").read_text()
        self.assertIn("Intent.ACTION_OPEN_DOCUMENT", screen)
        self.assertIn("Intent.CATEGORY_OPENABLE", screen)
        self.assertIn("Intent.FLAG_GRANT_READ_URI_PERMISSION", screen)
        self.assertIn("ContentResolver.SCHEME_CONTENT", screen)
        self.assertIn("importModel(selected)", screen)
        self.assertIn("VivoGgufProbePolicy.classify", screen)
        self.assertIn("VivoGgufEvidenceResetPlan.invalidate", (SRC / "LocalGgufModelStore.kt").read_text())
        self.assertIn("OfflineVoiceAnswerPolicy.forTts", classifier)
        self.assertNotIn("android.permission.INTERNET", screen)
        self.assertNotIn("startActivityForResult", screen.split("override fun onCreate")[0])

    def test_aec_and_revocation_physical_signals_are_distinct(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        runner = (SRC / "NativeSpanishSpeechRunner.kt").read_text()
        report = (SRC / "core/VivoDiagnosticReport.kt").read_text()
        self.assertIn("AEC_estado_sesion=", report)
        self.assertIn("Revocacion_MIC_observada=", report)
        self.assertIn('aec_runtime_status', service)
        self.assertIn('mic_revocation_status', service)
        self.assertIn("ASR_NATIVE_AEC_CONFIGURED", runner)
        self.assertIn("NO_CERTIFICADO", report)

    def test_negative_audio_read_preserves_original_cause_to_final_delivery(self):
        runner = (SRC / "NativeSpanishSpeechRunner.kt").read_text()
        self.assertIn("NativeAudioExitPolicy.resolveFailure", runner)
        self.assertIn("NativeAudioExitPolicy.mayDeliverRecognizedSpeech", runner)
        self.assertIn("if (finalFailure != null) onFailure(finalFailure)", runner)

    def test_stop_blocks_queued_wake_before_service_destruction(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        self.assertIn("NativeWakeDeliveryGate.allow", service)
        self.assertIn("nativeRunner?.stop()", service.split("private fun finish(reason: String)")[1]
                      .split("private fun acquireShortWakeLock")[0])
        self.assertIn("timeGate.stop()", service.split("private fun finish(reason: String)")[1]
                      .split("private fun acquireShortWakeLock")[0])
        self.assertIn("!endedNormally && nativeModeRequested", service.split("onStatus = { message ->")[1]
                      .split("onAudioProgress =")[0])
        self.assertIn("!endedNormally", service.split("onFailure = { message ->")[1]
                      .split("conversationMode = conversationMode")[0])

    def test_model_mutation_invalidates_evidence_outside_activity_lifecycle(self):
        store = (SRC / "LocalGgufModelStore.kt").read_text()
        activity = (SRC / "VivoDiagnosticActivity.kt").read_text()
        self.assertIn("VivoGgufEvidenceResetPlan.invalidate", store)
        self.assertLess(store.index("VivoGgufEvidenceResetPlan.invalidate"),
                        store.index("tmp.renameTo(file)"))
        self.assertIn("modelImportEngine?.close()", activity)
        self.assertNotIn('prefs.edit().putString("qwen", "NO_PROBADO").remove("qwen_ms")',
                         activity)

    def test_voice_gguf_diagnostic_survives_exit_of_direct_probe(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        report = (SRC / "core/VivoDiagnosticReport.kt").read_text()
        activity = (SRC / "VivoDiagnosticActivity.kt").read_text()
        self.assertIn('voice_gguf_success_count', service)
        self.assertIn('voice_gguf_last_latency_ms', service)
        self.assertIn('Voice_GGUF_respuestas_medido=', report)
        self.assertIn('VivoVoiceGgufEvidencePolicy.isContradictory', report)
        self.assertIn('voiceGgufAnswers = audio.getInt', activity)

    def test_screenoff_receipts_persist_before_delayed_ui_callbacks(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        runner = (SRC / "NativeSpanishSpeechRunner.kt").read_text()
        self.assertIn("VivoScreenOffReceiptPolicy.totalAfterProgress", service)
        self.assertIn("screenOffFrames.drainScreenOff()", runner)
        self.assertIn("diagnostics.edit().putLong(\"lockscreen_off_samples\"", service)

    def test_echo_and_permission_diagnostics_remain_not_certified(self):
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        report = (SRC / "core/VivoDiagnosticReport.kt").read_text()
        self.assertIn('barge_in_question_count', service)
        self.assertIn('tts_started_count', service)
        self.assertIn('BargeIn_preguntas_interrumpidas=', report)
        self.assertIn('TTS_inicios_reportados_por_Android=', report)
        self.assertIn('Revocacion_MIC_observada=', report)
        self.assertIn('NO_CERTIFICADO', report)

    def test_latency_profile_bounds_output_without_disabling_integrity(self):
        inference = (SRC / "LocalGgufInference.kt").read_text()
        service = (SRC / "HandsFreePreviewService.kt").read_text()
        cpp = (ROOT / "apps/android/app/src/main/cpp/jarvis_llm.cpp").read_text()
        self.assertIn("FastVoiceGgufPolicy.VOICE_MAX_TOKENS", service)
        self.assertIn("maxNewTokens", inference)
        self.assertIn("FastVoiceGgufPolicy.warmCacheMaxBytes", inference)
        self.assertIn("std::thread::hardware_concurrency()", cpp)
        self.assertIn("validPrompt", (SRC / "core/LocalGgufPolicy.kt").read_text())

    def test_debug_apk_is_uploaded_from_ci(self):
        workflow = (ROOT / ".github/workflows/android-shell.yml").read_text()
        self.assertIn("jarvis-omega-debug-apk", workflow)
        self.assertIn("app-debug.apk", workflow)

if __name__ == "__main__":
    unittest.main()
