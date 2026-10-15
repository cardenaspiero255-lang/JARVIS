package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.util.Locale

enum class VoiceAction {
    READ_STATUS,
    OPEN_ACCESSIBILITY_SETTINGS,
    OPEN_GAMEHUB_ULTRA,
    UNSUPPORTED
}

/**
 * Narrow, deterministic Spanish offline intent classifier.
 * Unknown transcripts do not become executable instructions.
 */
class VoiceCommandParser {
    fun parse(transcript: String): VoiceAction {
        val normalized = Normalizer.normalize(
            transcript.lowercase(Locale.ROOT), Normalizer.Form.NFD
        ).replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        return when (normalized) {
            "estado", "jarvis estado", "estado jarvis",
            "jarvis como estas", "como estas jarvis" -> VoiceAction.READ_STATUS
            "jarvis abre configuracion", "abre configuracion",
            "abrir ajustes", "jarvis ajustes" -> VoiceAction.OPEN_ACCESSIBILITY_SETTINGS
            "jarvis abre gamehub ultra", "abre gamehub ultra",
            "abrir gamehub ultra", "jarvis inicia gamehub ultra" -> VoiceAction.OPEN_GAMEHUB_ULTRA
            else -> VoiceAction.UNSUPPORTED
        }
    }
}
