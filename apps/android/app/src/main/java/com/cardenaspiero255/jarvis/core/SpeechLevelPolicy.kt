package com.cardenaspiero255.jarvis.core

/**
 * UI-only adaptation of optional Android RecognitionListener RMS callbacks.
 * This is not an audio recorder or a signal that RECORD_AUDIO is granted.
 */
object SpeechLevelPolicy {
    fun progress(rmsDb: Float): Int {
        if (!rmsDb.isFinite()) return 0
        return ((rmsDb + 2f) * 10f).toInt().coerceIn(0, 100)
    }

    fun readout(hasRmsCallback: Boolean, hasSpeechStart: Boolean): String = when {
        hasSpeechStart -> "Voz detectada por Android"
        hasRmsCallback -> "Recibiendo niveles de audio del servicio"
        else -> "Esperando información del servicio de voz"
    }
}
