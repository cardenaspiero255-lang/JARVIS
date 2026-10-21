package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.util.Locale

/**
 * Conservatively adapted from the echo-vs-spoken comparison strategy used by
 * judejulius/jarvis VoiceInput.kt (MIT; notice in app assets/legal).
 *
 * A completed Vosk segment may STOP Android TTS, but NEVER start a command,
 * invoke a tool, infer an owner identity, or authorize an Android capability.
 * AEC is a prerequisite, not proof of acoustic identity or replay protection.
 */
enum class BargeInStopDecision { IGNORE, STOP_TTS }

class BargeInStopPolicy {
    fun decide(
        heard: String,
        spoken: String?,
        playbackActive: Boolean,
        echoCancellationActive: Boolean,
        finalUtterance: Boolean
    ): BargeInStopDecision {
        if (!playbackActive || !echoCancellationActive || !finalUtterance ||
            spoken.isNullOrBlank() || heard.isBlank() ||
            heard.length > 160 || spoken.length > 1000 ||
            heard.any { Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() }
        ) return BargeInStopDecision.IGNORE
        val heardNormalized = normalize(heard)
        val spokenWords = normalize(spoken).split(' ').filter { it.isNotEmpty() }.toSet()
        // Do not accept stop if any of its signal words can be the TTS's
        // own echo. False negatives are safer: notification STOP still works.
        if (spokenWords.any { it in setOf("jarvis", "para", "detente", "stop", "alto", "cancelar") }) {
            return BargeInStopDecision.IGNORE
        }
        return if (heardNormalized in setOf("jarvis para", "jarvis detente", "jarvis stop")) {
            BargeInStopDecision.STOP_TTS
        } else {
            BargeInStopDecision.IGNORE
        }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
}
