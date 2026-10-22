package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VivoDiagnosticReportTest {
    private fun report(
        results: Map<VivoCheck, VivoReportedResult> = emptyMap(),
        stopRun: String = "NO_PROBADO",
        screenOffSamples: Long = 0L,
        screenOffObserved: Boolean = false,
        extended: Boolean = false,
        timeCommands: Int = 0,
        stopCommands: Int = 0,
        questions: Int = 0
    ) =
        VivoDiagnosticReport(
            model = "V2158\nprivate:key", sdk = 34, batteryPercent = 55,
            thermalStatus = 0, availableRamMib = 1536L, pssKib = 94208,
            microphoneGranted = false, notificationsGranted = true, ggufImported = false,
            screenOffSamples = screenOffSamples, screenOffObserved = screenOffObserved,
            lockscreenLastExtended = extended,
            lockscreenTimeCommandCount = timeCommands,
            lockscreenStopCommandCount = stopCommands,
            lockscreenQuestionCount = questions,
            qwenRun = "NO_PROBADO",
            qwenLatencyMs = null, stopRun = stopRun, results = results
        ).toShareText()

    @Test fun untestedFeaturesNeverBecomeCertifiedByDefault() {
        val out = report()
        assertTrue(out.contains("NO_CERTIFICADO"))
        for (check in VivoCheck.entries) {
            assertTrue(out.contains("USER_REPORTED_${check.name}=NO_PROBADO"))
        }
        assertFalse(out.contains("serial="))
        assertFalse(out.contains("raw_pcm"))
        assertFalse(out.contains("USER_REPORTED_OWNER=FUNCIONA"))
    }

    @Test fun contradictoryPhysicalReportMustNotClaimQwenOrOwnerAreCertified() {
        val out = report(mapOf(
            VivoCheck.QWEN to VivoReportedResult.FUNCIONA,
            VivoCheck.OWNER to VivoReportedResult.FUNCIONA,
            VivoCheck.STOP to VivoReportedResult.FUNCIONA
        ))
        assertTrue(out.contains("CONTRADICCION_QWEN"))
        assertTrue(out.contains("OWNER_NO_CERTIFICADO"))
        assertTrue(out.contains("STOP_NO_CONFIRMADO"))
        assertTrue(out.contains("NO_CERTIFICADO"))
    }

    @Test fun physicalReportDifferentiatesCapturedPcmAndRecognizedCommands() {
        val out = report(mapOf(VivoCheck.LOCKSCREEN to VivoReportedResult.FALLA))
        assertTrue(out.contains("Comandos_HORA_recibidos_pantalla_apagada=0"))
        assertTrue(out.contains("Comandos_STOP_recibidos_pantalla_apagada=0"))
        assertTrue(out.contains("NO_CERTIFICADO"))
    }

    @Test fun manuallyWorkingLockscreenWithoutAnyPcmOrCommandCannotBeCorroborated() {
        val out = report(mapOf(VivoCheck.LOCKSCREEN to VivoReportedResult.FUNCIONA))
        assertTrue(out.contains("LOCKSCREEN_NO_CORROBORADO"))
        assertTrue(out.contains("NO_CERTIFICADO"))
    }

    @Test fun screenOffPcmWithoutRecognizedOrdersMustNotClaimSamplesAreMissing() {
        val out = report(
            results = mapOf(VivoCheck.LOCKSCREEN to VivoReportedResult.FUNCIONA),
            extended = true, screenOffSamples = 67_584L, screenOffObserved = true
        )
        assertTrue(out.contains("LOCKSCREEN_PCM_SIN_ORDEN"))
        assertFalse(out.contains("sin muestras y orden"))
        assertTrue(out.contains("Pantalla_apagada_respuesta_audible=SOLO_USER_REPORTED"))
        assertTrue(out.contains("NO_CERTIFICADO"))
    }

    @Test fun screenOffRecognitionAloneMustNotCertifyAnAudibleAnswer() {
        val out = report(
            results = mapOf(VivoCheck.LOCKSCREEN to VivoReportedResult.FUNCIONA),
            extended = true, screenOffSamples = 48_000L,
            screenOffObserved = true, timeCommands = 1
        )
        assertTrue(out.contains("LOCKSCREEN_RECONOCIMIENTO_PARCIAL"))
        assertTrue(out.contains("NO_VERIFICADO_ACUSTICAMENTE"))
        assertTrue(out.contains("NO_CERTIFICADO"))
    }

    @Test fun ggufQuestionsAreCountedSeparatelyFromTimeAndStop() {
        val out = report(
            results = mapOf(VivoCheck.LOCKSCREEN to VivoReportedResult.FUNCIONA),
            extended = true, screenOffSamples = 48_000L,
            screenOffObserved = true, questions = 2
        )
        assertTrue(out.contains("Preguntas_GGUF_recibidas_pantalla_apagada=2"))
        assertTrue(out.contains("Comandos_HORA_recibidos_pantalla_apagada=0"))
        assertTrue(out.contains("LOCKSCREEN_RECONOCIMIENTO_PARCIAL"))
    }

    @Test fun staleOrAbsentEvidenceMustBeCalledOutSeparately() {
        val reported = mapOf(VivoCheck.LOCKSCREEN to VivoReportedResult.FUNCIONA)
        assertTrue(report(results = reported, extended = false, screenOffSamples = 67_584L,
            screenOffObserved = true).contains("LOCKSCREEN_SESION_NO_ACREDITADA"))
        assertTrue(report(results = reported, extended = true, timeCommands = 1,
            screenOffObserved = false).contains("LOCKSCREEN_PCM_NO_CORROBORADO"))
    }

    @Test fun stopMarkedWorkingWhileNoSessionExistedIsExplicitlyInvalid() {
        val out = report(
            mapOf(VivoCheck.STOP to VivoReportedResult.FUNCIONA),
            stopRun = "SIN_SESION_ACTIVA"
        )
        assertTrue(out.contains("STOP_SIN_SESION_ACTIVA"))
        assertTrue(out.contains("NO_CERTIFICADO"))
    }

    @Test fun bargeInEchoAndRevocationAreManualNotCertified() {
        val out = report(mapOf(
            VivoCheck.BARGE_IN to VivoReportedResult.FUNCIONA,
            VivoCheck.ECHO_REJECTION to VivoReportedResult.FUNCIONA,
            VivoCheck.MIC_REVOKED to VivoReportedResult.FUNCIONA
        ))
        assertTrue(out.contains("USER_REPORTED_BARGE_IN=FUNCIONA"))
        assertTrue(out.contains("USER_REPORTED_ECHO_REJECTION=FUNCIONA"))
        assertTrue(out.contains("USER_REPORTED_MIC_REVOKED=FUNCIONA"))
        assertTrue(out.contains("NO_CERTIFICADO"))
    }

    @Test fun aecAndRevocationAreRuntimeSignalsNotAcousticCertification() {
        val report = VivoDiagnosticReport(
            model = "V2158", sdk = 34,
            batteryPercent = 80, thermalStatus = 0,
            availableRamMib = 4000L, pssKib = 90000,
            microphoneGranted = false, notificationsGranted = true,
            ggufImported = false, screenOffSamples = 67584L,
            screenOffObserved = true, lockscreenLastExtended = true,
            lockscreenTimeCommandCount = 0, lockscreenStopCommandCount = 0,
            qwenRun = "GGUF_NO_IMPORTADO", qwenLatencyMs = null,
            stopRun = "STOP_SERVICIO_FINALIZADO",
            results = mapOf(VivoCheck.ECHO_REJECTION to VivoReportedResult.FUNCIONA),
            aecRuntime = "AEC_ACTIVADO_SIN_PRUEBA_ACUSTICA",
            micRevocationRuntime = "PERMISO_DENEGADO_EN_LECTURA"
        ).toShareText()
        assertTrue(report.contains("AEC_estado_sesion=AEC_ACTIVADO_SIN_PRUEBA_ACUSTICA"))
        assertTrue(report.contains("Revocacion_MIC_observada=PERMISO_DENEGADO_EN_LECTURA"))
        assertTrue(report.contains("NO_CERTIFICADO"))
        assertFalse(report.contains("AEC_CERTIFICADO"))
    }

    @Test fun reportSeparatesManualObservationFromSystemMeasurement() {
        val out = report(mapOf(
            VivoCheck.MICROPHONE to VivoReportedResult.FUNCIONA,
            VivoCheck.OWNER to VivoReportedResult.FALLA
        ))
        assertTrue(out.contains("USER_REPORTED_MICROPHONE=FUNCIONA"))
        assertTrue(out.contains("USER_REPORTED_OWNER=FALLA"))
        assertTrue(out.contains("Android_API=34"))
        assertTrue(out.contains("PCM_muestras=0"))
        assertTrue(out.contains("NO verifica identidad"))
        assertFalse(out.contains("\nprivate:key"))
    }
}
