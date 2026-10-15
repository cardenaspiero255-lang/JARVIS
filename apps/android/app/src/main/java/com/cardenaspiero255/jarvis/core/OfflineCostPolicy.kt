package com.cardenaspiero255.jarvis.core

/**
 * Offline by default, irrespective of any previously stored paid API token.
 * Optional paid OpenAI requests require an explicit text prefix AND a new
 * positive confirmation dialog; captured voice can never trigger a charge.
 */
object OfflineCostPolicy {
    private const val PAID_PREFIX = "consulta nube:"

    fun isPaidRequest(message: String): Boolean =
        message.trimStart().startsWith(PAID_PREFIX, ignoreCase = true)

    fun paidPrompt(message: String): String? {
        if (!isPaidRequest(message)) return null
        val cleaned = message.trimStart().substring(PAID_PREFIX.length).trim()
        return cleaned.takeIf { it.length in 1..1200 &&
            it.none { c -> Character.isISOControl(c) } }
    }
}
