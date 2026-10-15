package com.cardenaspiero255.jarvis.core

/**
 * No arbitrary model text or captured screen content can be routed into
 * Android speech from this bootstrap component.
 */
enum class SpokenMessage { LOCAL_STATUS, PRIVACY }

class SpokenReplyPolicy {
    fun maySpeak(
        message: SpokenMessage,
        explicitTap: Boolean,
        ready: Boolean,
        stopped: Boolean,
    ): Boolean = explicitTap && ready && !stopped

    fun safeText(message: SpokenMessage): String = safeText(message, VoiceStyle.SPANISH)

    fun safeText(message: SpokenMessage, style: VoiceStyle): String = when (style) {
        VoiceStyle.SPANISH -> when (message) {
            SpokenMessage.LOCAL_STATUS ->
                "JARVIS está disponible en modo observador. Las funciones de control remoto están desactivadas."
            SpokenMessage.PRIVACY ->
                "La escucha continua está desactivada. Para hablar, pulsa el botón de voz."
        }
        VoiceStyle.BRITISH_ASSISTANT -> when (message) {
            SpokenMessage.LOCAL_STATUS ->
                "Good evening. JARVIS is standing by in observer mode. Remote control is disabled."
            SpokenMessage.PRIVACY ->
                "Continuous listening is disabled. Press the voice button to speak."
        }
    }
}

/** In-memory opt-in: a restarted process never speaks automatically. */
class SpeechSessionState {
    @Volatile var stopped: Boolean = true
        private set

    fun resume() { stopped = false }
    fun stop() { stopped = true }
}
