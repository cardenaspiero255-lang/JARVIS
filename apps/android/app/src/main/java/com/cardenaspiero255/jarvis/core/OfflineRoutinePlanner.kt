package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.util.Locale

/**
 * Conservative local intent-to-plan adapter. This is a deterministic planner,
 * not a neural LLM or an autonomous Android executor.
 *
 * Recognized phrases yield a preview that must be explicitly confirmed on
 * the existing CompanionCenter screen; no user speech confers authority.
 */
data class OfflineRoutinePlan(val routine: SafeRoutine) {
    val steps: List<SafeAutomationStep> get() = routine.steps.toList()
    val summary: String get() = "Plan local: ${routine.summary} " +
        "Requiere confirmación visible antes de actuar."
    val authorizedToExecute: Boolean get() = false
    val requiresExplicitTap: Boolean get() = true
    val rollbackGuaranteed: Boolean get() = false
    val externalOutcomeVerified: Boolean get() = false
}

class OfflineRoutinePlanner {
    fun plan(raw: String): OfflineRoutinePlan? {
        if (raw.isBlank() || raw.length > 160 || raw.any {
                Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt()
            }) return null
        val accentless = Normalizer.normalize(raw.lowercase(Locale.ROOT),
            Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        // Reject operators, semicolons, quotes and unexpected Unicode; do NOT
        // strip them because that could turn a dangerous mixed command safe.
        if (!accentless.matches(Regex("[a-z0-9 ¿?¡!., ]+"))) return null
        val normalized = accentless.replace(Regex("[¿?¡!.,]+"), " ")
            .replace(Regex("\\s+"), " ").trim()
        val routine = when (normalized) {
            "jarvis revisa shizuku y abre ajustes" ->
                SafeRoutine.SHIZUKU_SETTINGS_DIAGNOSTIC
            "jarvis revisa dhizuku y abre ajustes" ->
                SafeRoutine.DHIZUKU_SETTINGS_DIAGNOSTIC
            else -> SafeAutomationVoiceRouter.parse(raw)
        }
        return routine?.let(::OfflineRoutinePlan)
    }
}
