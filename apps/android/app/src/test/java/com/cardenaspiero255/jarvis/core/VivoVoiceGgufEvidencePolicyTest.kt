package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class VivoVoiceGgufEvidencePolicyTest {
    @Test fun successfulVoiceAnswerCorroboratesModelEvenWhenDirectProbeWasCancelled() {
        assertTrue(VivoVoiceGgufEvidencePolicy.hasMeasuredSuccess(
            "CANCELADO_AL_SALIR", null, voiceAnswers = 1, lastVoiceLatencyMs = 1532))
        assertFalse(VivoVoiceGgufEvidencePolicy.isContradictory(
            imported = true, userReported = VivoReportedResult.FUNCIONA,
            directRun = "CANCELADO_AL_SALIR", directLatencyMs = null,
            voiceAnswers = 1, voiceLatencyMs = 1532))
    }

    @Test fun unmeasuredVoiceAndCancelledProbeMustNotBeCalledSuccess() {
        assertTrue(VivoVoiceGgufEvidencePolicy.isContradictory(
            imported = true, userReported = VivoReportedResult.FUNCIONA,
            directRun = "CANCELADO_AL_SALIR", directLatencyMs = null,
            voiceAnswers = 0, voiceLatencyMs = null))
        assertFalse(VivoVoiceGgufEvidencePolicy.hasMeasuredSuccess(
            "CANCELADO_AL_SALIR", null, 0, null))
        assertFalse(VivoVoiceGgufEvidencePolicy.hasMeasuredSuccess(
            "NO_PROBADO", null, 1, 0))
    }

    @Test fun voiceAndDirectProbesStayDistinct() {
        assertTrue(VivoVoiceGgufEvidencePolicy.hasMeasuredSuccess(
            "TEXTO_NO_VACIO_NO_CERTIFICADO", 1100, 0, null))
        assertFalse(VivoVoiceGgufEvidencePolicy.isContradictory(
            imported = true, userReported = VivoReportedResult.FUNCIONA,
            directRun = "TEXTO_NO_VACIO_NO_CERTIFICADO", directLatencyMs = 1100,
            voiceAnswers = 0, voiceLatencyMs = null))
    }
}
