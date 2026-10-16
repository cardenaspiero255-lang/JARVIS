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
    READ_DEVICE_DIAGNOSTIC,
    READ_GAMING_STATUS,
    LIST_LAUNCHABLE_APPS,
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
    DEVICE_DIAGNOSTIC(
        listOf(SafeAutomationStep.READ_DEVICE_DIAGNOSTIC),
        "Consultar batería, RAM y escala de animaciones, sin cambiar ajustes."
    ),
    GAMING_READINESS(
        listOf(SafeAutomationStep.READ_GAMING_STATUS),
        "Evaluar memoria disponible antes de jugar; sin detener procesos ni prometer FPS."
    ),
    LIST_APPS(
        listOf(SafeAutomationStep.LIST_LAUNCHABLE_APPS),
        "Mostrar un resumen de aplicaciones visibles; no instalarlas ni modificarlas."
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
)

/**
 * Explicit preview and ONE user confirmation required per run.
 * No periodic worker, background scheduler or automatic voice execution.
 */
class SafeAutomationEngine(private val gateway: SafeAutomationGateway) {
    @Volatile private var emergencyStopped = true
    @Volatile private var pending: SafeRoutine? = null

    fun prepare(routine: SafeRoutine): String = synchronized(this) {
        emergencyStopped = true
        pending = routine
        routine.summary
    }

    fun stop() {
        emergencyStopped = true
        pending = null
    }

    fun confirmAndRun(routine: SafeRoutine, explicitTap: Boolean): SafeAutomationReport {
        val approved = synchronized(this) {
            if (!explicitTap || pending != routine || !emergencyStopped) {
                false
            } else {
                pending = null
                emergencyStopped = false
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
            emergencyStopped = true
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
            "jarvis diagnostica telefono" -> SafeRoutine.DEVICE_DIAGNOSTIC
            "jarvis revisa mi telefono" -> SafeRoutine.DEVICE_DIAGNOSTIC
            "jarvis prepara juego" -> SafeRoutine.GAMING_READINESS
            "jarvis evalua modo juego" -> SafeRoutine.GAMING_READINESS
            "jarvis muestra aplicaciones" -> SafeRoutine.LIST_APPS
            else -> null
        }
    }
}
