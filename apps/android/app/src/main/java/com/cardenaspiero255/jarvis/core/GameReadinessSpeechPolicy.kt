package com.cardenaspiero255.jarvis.core

/**
 * Short, deterministic and read-only spoken gaming advice after the user's
 * explicit HABLAR tap. Only the trusted assessment level is read; never speak
 * untrusted observations, OCR, model output or the captured transcript.
 */
class GameReadinessSpeechPolicy {
    fun textForSpeech(
        report: GameReadinessReport,
        explicitTap: Boolean,
        ready: Boolean,
        stopped: Boolean
    ): String? {
        if (!explicitTap || !ready || stopped) return null
        return when (report.level) {
            GameReadinessLevel.READY ->
                "Tu teléfono está listo con reservas para jugar. " +
                    "La lectura actual no garantiza los fotogramas por segundo."
            GameReadinessLevel.CAUTION ->
                "Puedes jugar con precaución. Revisa en pantalla los avisos " +
                    "de batería, memoria y estado térmico antes de continuar."
            GameReadinessLevel.POSTPONE ->
                "Te recomiendo posponer el juego y dejar enfriar el teléfono " +
                    "porque Android reporta un estado térmico crítico."
            GameReadinessLevel.UNKNOWN ->
                "No tengo suficientes mediciones para confirmar " +
                    "el estado del teléfono para jugar."
        }
    }
}
