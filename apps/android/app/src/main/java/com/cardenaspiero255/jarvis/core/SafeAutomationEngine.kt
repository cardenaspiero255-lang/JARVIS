package com.cardenaspiero255.jarvis.core

/** Fixed, bounded operations; no arbitrary shell text, URLs or app packages. */
enum class SafeAutomationStep {
    CHECK_SHIZUKU,
    OPEN_SHIZUKU,
    CHECK_DHIZUKU,
    OPEN_DHIZUKU,
    OPEN_OWNDROID,
    OPEN_TERMUX,
    OPEN_ANDROID_SETTINGS,
}

enum class SafeRoutine(val steps: List<SafeAutomationStep>, val summary: String) {
    SHIZUKU_ASSIST(
        listOf(SafeAutomationStep.CHECK_SHIZUKU, SafeAutomationStep.OPEN_SHIZUKU),
        "Comprobar la autorización de Shizuku y abrir su panel."
    ),
    DHIZUKU_ASSIST(
        listOf(SafeAutomationStep.CHECK_DHIZUKU, SafeAutomationStep.OPEN_DHIZUKU),
        "Verificar Dhizuku ya autorizado y abrir su panel, sin cambiar Device Owner."
    ),
    OPEN_TERMUX(
        listOf(SafeAutomationStep.OPEN_TERMUX),
        "Abrir Termux; no ejecutar scripts."
    ),
    OPEN_OWNDROID(
        listOf(SafeAutomationStep.OPEN_OWNDROID),
        "Abrir OwnDroid; no enviar políticas ni API keys."
    ),
    OPEN_SETTINGS(
        listOf(SafeAutomationStep.OPEN_ANDROID_SETTINGS),
        "Abrir Ajustes de Android; no modificar la configuración."
    ),
}

interface SafeAutomationGateway {
    /** True means this local step was accepted, not that a remote operation finished. */
    fun perform(step: SafeAutomationStep): Boolean
}

enum class SafeAutomationOutcome { DENIED, COMPLETED, FAILED, STOPPED }
data class SafeAutomationReport(
    val outcome: SafeAutomationOutcome,
    val completedSteps: Int,
    val totalSteps: Int,
    // A successful local intent dispatch cannot certify third-party completion.
    val externalOutcomeVerified: Boolean = false,
    // Emergency STOP halts *future* steps, not already executed side effects.
    val rollbackVerified: Boolean = false,
)

/**
 * Explicit preview and ONE user confirmation required per run.
 * No periodic worker, background scheduler or automatic voice execution.
 */
class SafeAutomationEngine(private val gateway: SafeAutomationGateway) {
    @Volatile private var emergencyStopped = true
    @Volatile private var pending: SafeRoutine? = null
    // Guards the full execution lifetime, not just the approval transaction.
    private var activeRun = false

    fun prepare(routine: SafeRoutine): String = synchronized(this) {
        if (activeRun) {
            // No silent cancellation or replacement of an in-flight action.
            "Rutina en ejecución; detén o finaliza antes de preparar otra."
        } else {
            emergencyStopped = true
            pending = routine
            routine.summary
        }
    }

    fun stop() = synchronized(this) {
        emergencyStopped = true
        pending = null
    }

    fun confirmAndRun(routine: SafeRoutine, explicitTap: Boolean): SafeAutomationReport {
        val approved = synchronized(this) {
            if (activeRun || !explicitTap || pending != routine ||
                !emergencyStopped) {
                false
            } else {
                pending = null
                emergencyStopped = false
                activeRun = true
                true
            }
        }
        if (!approved) return SafeAutomationReport(
            SafeAutomationOutcome.DENIED, 0, routine.steps.size
        )
        var done = 0
        try {
            for (step in routine.steps) {
                if (emergencyStopped) return SafeAutomationReport(
                    SafeAutomationOutcome.STOPPED, done, routine.steps.size
                )
                val accepted = try { gateway.perform(step) }
                               catch (_: RuntimeException) { false }
                if (!accepted) return SafeAutomationReport(
                    SafeAutomationOutcome.FAILED, done, routine.steps.size
                )
                done += 1
            }
            if (emergencyStopped) return SafeAutomationReport(
                SafeAutomationOutcome.STOPPED, done, routine.steps.size
            )
            return SafeAutomationReport(
                SafeAutomationOutcome.COMPLETED, done, routine.steps.size
            )
        } finally {
            synchronized(this) {
                emergencyStopped = true
                activeRun = false
            }
        }
    }
}

/** Spoken text is untrusted: exact matching returns a PREVIEW only, never executes. */
object SafeAutomationVoiceRouter {
    fun parse(spokenText: String): SafeRoutine? {
        val normalized = java.text.Normalizer.normalize(
            spokenText.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD
        ).replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ").trim()
        return when (normalized) {
            "jarvis revisa shizuku" -> SafeRoutine.SHIZUKU_ASSIST
            "jarvis revisa dhizuku" -> SafeRoutine.DHIZUKU_ASSIST
            "jarvis abre ajustes" -> SafeRoutine.OPEN_SETTINGS
            "jarvis abre termux" -> SafeRoutine.OPEN_TERMUX
            "jarvis abre owndroid" -> SafeRoutine.OPEN_OWNDROID
            else -> null
        }
    }
}
