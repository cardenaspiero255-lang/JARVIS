package com.cardenaspiero255.jarvis.core

/** Distinguishes a completed voice response from an independent debug tap probe.
 * A voice response still does not prove audible speech, semantic accuracy or owner identity.
 */
object VivoVoiceGgufEvidencePolicy {
    fun hasMeasuredSuccess(
        directRun: String,
        directLatencyMs: Long?,
        voiceAnswers: Int,
        lastVoiceLatencyMs: Long?
    ): Boolean =
        (directRun == "TEXTO_NO_VACIO_NO_CERTIFICADO" &&
            directLatencyMs != null && directLatencyMs > 0L) ||
        (voiceAnswers > 0 && lastVoiceLatencyMs != null && lastVoiceLatencyMs > 0L)

    fun isContradictory(
        imported: Boolean,
        userReported: VivoReportedResult,
        directRun: String,
        directLatencyMs: Long?,
        voiceAnswers: Int,
        voiceLatencyMs: Long?
    ): Boolean = userReported == VivoReportedResult.FUNCIONA &&
        (!imported || !hasMeasuredSuccess(directRun, directLatencyMs,
            voiceAnswers, voiceLatencyMs))
}
