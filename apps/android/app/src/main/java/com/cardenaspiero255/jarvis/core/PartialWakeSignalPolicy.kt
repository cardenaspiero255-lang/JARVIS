package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.util.Locale

/**
 * One UI-only signal per Vosk partial utterance. This is neither hotword KWS
 * nor owner verification: the caller MUST NOT use it to grant any action.
 */
class PartialWakeSignalPolicy {
    private var announced = false

    fun shouldReport(partial: String): Boolean {
        if (announced || partial.isBlank() || partial.length > 32) return false
        val normalized = Normalizer.normalize(
            partial.lowercase(Locale.ROOT), Normalizer.Form.NFD
        ).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
        if (normalized != "jarvis") return false
        announced = true
        return true
    }

    fun onUtteranceCompleted() { announced = false }
}
