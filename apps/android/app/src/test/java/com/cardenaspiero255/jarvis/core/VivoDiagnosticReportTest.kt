package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VivoDiagnosticReportTest {
    private fun report(results: Map<VivoCheck, VivoReportedResult> = emptyMap()) =
        VivoDiagnosticReport(
            model = "V2158\nprivate:key", sdk = 34, batteryPercent = 55,
            thermalStatus = 0, availableRamMib = 1536L, pssKib = 94208,
            microphoneGranted = false, notificationsGranted = true, ggufImported = false,
            screenOffSamples = 0L, screenOffObserved = false,
            lockscreenTimeCommandCount = 0, lockscreenStopCommandCount = 0,
            qwenRun = "NO_PROBADO",
            qwenLatencyMs = null, stopRun = "NO_PROBADO", results = results
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

    @Test fun stopMarkedWorkingWhileNoSessionExistedIsExplicitlyInvalid() {
        val out = report(mapOf(VivoCheck.STOP to VivoReportedResult.FUNCIONA))
        assertTrue(out.contains("STOP_SIN_SESION_ACTIVA"))
        assertTrue(out.contains("NO_CERTIFICADO"))
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
