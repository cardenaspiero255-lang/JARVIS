package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.util.Locale

/**
 * Read-only offline Spanish question gate. Only completed Vosk utterances from
 * an explicitly started, time-limited microphone session may call this.
 * Wake words and speaker identity do not confer permission for Android actions.
 */
object OfflineVoiceQuestionPolicy {
    private val wake = Regex("""^jarvis\b[\s,¿?!.:-]*""", RegexOption.IGNORE_CASE)
    private val question = Regex(
        """^(que|quien|quienes|cual|cuales|como|cuando|donde|por que|cuanto|cuanta|cuantos|cuantas|explica|define)\b"""
    )

    fun extract(utterance: String, freshWakeFollowup: Boolean): String? {
        val raw = utterance.trim()
        if (raw.isBlank() || raw.length > 160 ||
            !LocalGgufPolicy.validPrompt(raw)) return null
        val recognizedWake = wake.find(raw)
        if (recognizedWake == null && !freshWakeFollowup) return null
        val candidate = (if (recognizedWake != null)
            raw.substring(recognizedWake.range.last + 1) else raw).trim()
        if (candidate.length < 5 || !LocalGgufPolicy.validPrompt(candidate)) return null
        val normalized = Normalizer.normalize(candidate.lowercase(Locale.ROOT),
            Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
            .trimStart('¿', '!', ' ', '\t')
        if (!question.containsMatchIn(normalized)) return null
        return candidate
    }
}
