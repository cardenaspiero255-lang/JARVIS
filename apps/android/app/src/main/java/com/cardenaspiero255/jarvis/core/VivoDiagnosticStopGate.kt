package com.cardenaspiero255.jarvis.core

/**
 * A STOP tap can stop microphone and Qwen independently. Do not overwrite
 * Qwen evidence when there was no Qwen generation in progress.
 *
 * A service STOP is not confirmed until its receipt matches the initiating
 * request AND the foreground service is inactive and has persisted inactive.
 */
class VivoDiagnosticStopGate {
    fun qwenStatusAfterStop(qwenWasActive: Boolean, previous: String): String =
        if (qwenWasActive) "CANCELADO_POR_USUARIO" else previous

    fun confirmStop(
        requestedId: Long,
        receivedId: Long,
        serviceRunning: Boolean,
        servicePersistedActive: Boolean,
        sessionWasActive: Boolean
    ): Boolean = sessionWasActive && requestedId > 0L &&
        requestedId == receivedId && !serviceRunning && !servicePersistedActive
}
