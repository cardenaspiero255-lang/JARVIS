package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class OmegaPortableTaskPlannerTest {
    private val planner = OmegaPortableTaskPlanner()

    @Test fun stableDependencyOrderRegardlessOfInputOrder() {
        val preview = planner.compile(listOf(
            OmegaTaskStep("review", "review.requested", listOf("checks")),
            OmegaTaskStep("checks", "checks.read", listOf("status")),
            OmegaTaskStep("status", "status.read", emptyList())
        ))
        assertEquals(listOf("status", "checks", "review"), preview.steps.map { it.id })
        assertEquals(OmegaTaskRisk.REQUIRES_APPROVAL, preview.risks["review"])
        assertEquals(OmegaTaskRisk.READ_ONLY, preview.risks["checks"])
        assertFalse(preview.canExecute)
    }

    @Test fun approvalAndBlockedRisksPropagateThroughDependencies() {
        val preview = planner.compile(listOf(
            OmegaTaskStep("inspect", "status.read"),
            OmegaTaskStep("apply", "merge.execute", listOf("inspect")),
            OmegaTaskStep("verify", "checks.read", listOf("apply")),
            OmegaTaskStep("unsafe", "shell.execute", listOf("verify")),
            OmegaTaskStep("later", "checks.read", listOf("unsafe"))
        ))
        assertEquals(OmegaTaskRisk.REQUIRES_APPROVAL, preview.risks["verify"])
        assertEquals(OmegaTaskRisk.BLOCKED, preview.risks["unsafe"])
        assertEquals(OmegaTaskRisk.BLOCKED, preview.risks["later"])
        assertFalse(preview.canExecute)
    }

    @Test fun rejectMissingDuplicateCyclicAndMalformedPlans() {
        val bad = listOf(
            emptyList(),
            listOf(OmegaTaskStep("a", "checks.read", listOf("missing"))),
            listOf(OmegaTaskStep("a", "checks.read"), OmegaTaskStep("a", "status.read")),
            listOf(OmegaTaskStep("a", "checks.read", listOf("b")), OmegaTaskStep("b", "status.read", listOf("a"))),
            listOf(OmegaTaskStep("x", "shell;rm")),
            listOf(OmegaTaskStep("x", "checks.read", listOf("x"))),
            listOf(OmegaTaskStep("a", "checks.read", listOf("b", "b")), OmegaTaskStep("b", "status.read")),
            List(51) { i -> OmegaTaskStep("step$i", "status.read") }
        )
        for (steps in bad) {
            try { planner.compile(steps); fail("Accepted invalid plan $steps") }
            catch (_: IllegalArgumentException) { /* expected */ }
        }
    }

    @Test fun androidDeveloperPreviewDoesNotPretendGitHubOrPcAccess() {
        val plan = OmegaMobileDeveloperPlanner().plan("JARVIS WINDOWS")!!
        assertTrue(plan.summary.contains("JARVIS Windows"))
        assertTrue(plan.summary.contains("Sin conexión"))
        assertTrue(plan.summary.contains("autorización"))
        assertFalse(plan.preview.canExecute)
        assertEquals(OmegaTaskRisk.REQUIRES_APPROVAL, plan.preview.risks["apply"])
        assertNull(OmegaMobileDeveloperPlanner().plan("JARVIS Windows y borra todos los archivos"))
        assertNull(OmegaMobileDeveloperPlanner().plan("curl https://ejemplo.com"))
        assertNull(OmegaMobileDeveloperPlanner().plan("JARVIS Windows; elimina GitHub"))
    }
}
