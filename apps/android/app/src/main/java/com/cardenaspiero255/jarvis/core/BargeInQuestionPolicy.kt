package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.util.Locale

/**
 * Untrusted completed Vosk text -> a possible new READ-ONLY question during
 * a separately tapped, time-bounded GGUF conversation. Never authorization.
 * Fails closed if TTS reference is absent or hardware AEC isn't enabled.
 */
enum class BargeInQuestionDecision { IGNORE, ASK }
data class BargeInQuestionResult(
    val decision: BargeInQuestionDecision,
    val question: String? = null
)

class BargeInQuestionPolicy {
    fun decide(
        heard: String,
        spoken: String?,
        playbackActive: Boolean,
        echoCancellationActive: Boolean,
        finalUtterance: Boolean
    ): BargeInQuestionResult {
        fun ignore() = BargeInQuestionResult(BargeInQuestionDecision.IGNORE)
        if (!playbackActive || !echoCancellationActive || !finalUtterance ||
            spoken.isNullOrBlank() || spoken.length > 1000 ||
            heard.length !in 12..280 ||
            heard.any { Character.isISOControl(it) ||
                Character.getType(it) == Character.FORMAT.toInt() }) return ignore()
        val normalizedHeard = normalize(heard)
        val normalizedSpoken = normalize(spoken)
        if (!normalizedHeard.startsWith("jarvis ")) return ignore()
        val spokenWords = normalizedSpoken.split(' ').toSet()
        // Hearing the synthesized wake is not evidence of an independent
        // human utterance, even with AEC enabled.
        if (spokenWords.any { it in setOf("jarvis", "yarvis", "jarbis", "arvis") }) return ignore()
        // Echo or a misrecognition of the answer cannot become a question.
        val questionWords = normalizedHeard.removePrefix("jarvis ").split(' ')
            .filter { it.length >= 3 && it !in setOf("como", "dime", "que", "por",
                "para", "del", "las", "los", "una", "con", "sobre", "esta") }
        val matching = questionWords.count { it in spokenWords }
        if (questionWords.isEmpty() || matching * 2 >= questionWords.size) return ignore()
        val observation = OfflineVoiceQuestionGate().observe(heard, 0L)
        if (observation.decision != OfflineVoiceDecision.QUESTION ||
            observation.question.isNullOrBlank() ||
            observation.question.length > 240) return ignore()
        return BargeInQuestionResult(BargeInQuestionDecision.ASK,
            observation.question)
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
}
