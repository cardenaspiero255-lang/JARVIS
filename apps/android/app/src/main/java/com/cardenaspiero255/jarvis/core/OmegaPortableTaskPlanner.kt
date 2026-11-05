package com.cardenaspiero255.jarvis.core

import java.text.Normalizer
import java.util.Locale

/**
 * Android-side, side-effect-free port of the JARVIS task-planning contract.
 * Mirrors the desktop contract's bounded DAG, deterministic ordering and
 * cascading risk classification. Nothing here can dispatch to GitHub or a PC.
 * Wire transport, device identity and user approval are deliberately absent.
 */
enum class OmegaTaskRisk { READ_ONLY, REQUIRES_APPROVAL, BLOCKED }

data class OmegaTaskStep(
    val id: String,
    val action: String,
    val dependsOn: List<String> = emptyList()
)

data class OmegaTaskPreview(
    val steps: List<OmegaTaskStep>,
    val risks: Map<String, OmegaTaskRisk>
) {
    /** A preview is NEVER an execution grant, including for read-only actions. */
    val canExecute: Boolean get() = false
}

class OmegaPortableTaskPlanner {
    private val idPattern = Regex("[a-z][a-z0-9_-]{0,47}")
    private val actionPattern = Regex("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*){1,3}")
    private val readOnly = setOf(
        "status.read", "pull_request.read", "memory.lookup", "device.status", "checks.read"
    )
    private val needsApproval = setOf(
        "merge.execute", "payment.submit", "message.send", "review.requested",
        "repository.delete", "game.launch", "network.optimize"
    )

    fun compile(raw: List<OmegaTaskStep>): OmegaTaskPreview {
        require(raw.size in 1..50) { "A plan must contain 1 to 50 steps" }
        for (step in raw) {
            require(idPattern.matches(step.id) && step.action.length <= 64 &&
                actionPattern.matches(step.action) && step.dependsOn.size <= 50 &&
                step.dependsOn.all(idPattern::matches) &&
                step.dependsOn.distinct().size == step.dependsOn.size &&
                step.id !in step.dependsOn) { "Invalid step identifier, action or dependencies" }
        }
        val indexed = raw.associateBy { it.id }
        require(indexed.size == raw.size) { "Duplicate step IDs" }
        require(raw.all { it.dependsOn.all(indexed::containsKey) }) { "Unknown dependency" }

        val pending = indexed.keys.toMutableSet()
        val ordered = mutableListOf<OmegaTaskStep>()
        val risks = linkedMapOf<String, OmegaTaskRisk>()
        val severity = mapOf(
            OmegaTaskRisk.READ_ONLY to 0,
            OmegaTaskRisk.REQUIRES_APPROVAL to 1,
            OmegaTaskRisk.BLOCKED to 2
        )
        while (pending.isNotEmpty()) {
            val ready = pending.filter { id ->
                indexed.getValue(id).dependsOn.all(risks::containsKey)
            }.sorted()
            require(ready.isNotEmpty()) { "Cyclic plan" }
            for (id in ready) {
                val step = indexed.getValue(id)
                val direct = when (step.action) {
                    in readOnly -> OmegaTaskRisk.READ_ONLY
                    in needsApproval -> OmegaTaskRisk.REQUIRES_APPROVAL
                    else -> OmegaTaskRisk.BLOCKED
                }
                val inherited = step.dependsOn.map { risks.getValue(it) }
                risks[id] = (inherited + direct).maxBy { severity.getValue(it) }
                ordered.add(step.copy(dependsOn = step.dependsOn.toList()))
                pending.remove(id)
            }
        }
        return OmegaTaskPreview(ordered.toList(), risks.toMap())
    }
}

/**
 * Text-only engineering task previews derived from the Windows review workflow.
 * Exact project names only: unknown text and mixed commands do not become plans.
 * Android remains fully functional with no PC, network access or GitHub token.
 */
data class OmegaDeveloperPlan(val summary: String, val preview: OmegaTaskPreview)

class OmegaMobileDeveloperPlanner {
    fun plan(raw: String): OmegaDeveloperPlan? {
        if (raw.isBlank() || raw.length > 80 || raw.any {
                Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt()
            }) return null
        val normalized = Normalizer.normalize(raw.lowercase(Locale.ROOT),
            Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\s+"), " ").trim()
        val target = when (normalized) {
            "jarvis windows" -> "JARVIS Windows"
            "jarvis android" -> "JARVIS Android"
            "gamehub ultra" -> "GameHub Ultra"
            "ultra sentinel" -> "Ultra Sentinel"
            else -> return null
        }
        val preview = OmegaPortableTaskPlanner().compile(listOf(
            OmegaTaskStep("status", "status.read"),
            OmegaTaskStep("checks", "checks.read", listOf("status")),
            OmegaTaskStep("review", "review.requested", listOf("checks")),
            OmegaTaskStep("apply", "merge.execute", listOf("review"))
        ))
        return OmegaDeveloperPlan(
            "Plan OMEGA para $target: inspeccionar código y CI, revisar cambios, " +
                "probar y solicitar autorización antes de integrar. " +
                "Sin conexión al PC ni GitHub; no se ejecutó ninguna acción.",
            preview
        )
    }
}
