package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

/** Behavioral integration: deterministic offline planning NEVER grants execution. */
class OfflineRoutinePlannerTest {
    private val planner = OfflineRoutinePlanner()

    @Test fun combinesTwoAllowlistedReadOnlyActionsIntoExplicitPreview() {
        val shizuku = planner.plan("Jarvis, revisa Shizuku y abre ajustes")
        assertNotNull(shizuku)
        assertEquals(SafeRoutine.SHIZUKU_SETTINGS_DIAGNOSTIC, shizuku!!.routine)
        assertEquals(listOf(SafeAutomationStep.CHECK_SHIZUKU,
            SafeAutomationStep.OPEN_ANDROID_SETTINGS), shizuku.steps)
        assertFalse(shizuku.authorizedToExecute)
        assertTrue(shizuku.summary.contains("confirm"))
        val dhizuku = planner.plan("Jarvis revisa Dhizuku y abre ajustes")
        assertEquals(SafeRoutine.DHIZUKU_SETTINGS_DIAGNOSTIC, dhizuku?.routine)
        assertFalse(dhizuku!!.authorizedToExecute)
    }

    @Test fun returnsExistingRoutineWithoutInventingSteps() {
        assertEquals(SafeRoutine.OPEN_TERMUX, planner.plan("Jarvis abre Termux")?.routine)
        assertEquals(SafeRoutine.OPEN_SETTINGS, planner.plan("JARVIS abre ajustes")?.routine)
        assertNull(planner.plan("¿Qué pasaría si abro Termux?"))
        assertNull(planner.plan("abreme cualquier aplicación"))
    }

    @Test fun negationsHypotheticalsAndPromptInjectionNeverBecomePlans() {
        val rejected = listOf(
            "Jarvis no revises Shizuku y abre ajustes",
            "Jarvis revisa Shizuku y abre ajustes y borra datos",
            "Jarvis revisa Shizuku; abre ajustes; ejecuta shell",
            "Si Jarvis revisa Shizuku y abre ajustes",
            "Jarvis revisa shizuku o abre ajustes",
            "Jarvis revisa shizuku y abre ajustes luego paga",
            "Jarvis revisa shizuku y abre ajustes\u200B",
            "Jarvis revisa shizuku y abre ajustes " + "x".repeat(500))
        rejected.forEach { assertNull("Unexpected unsafe plan for $it", planner.plan(it)) }
    }

    @Test fun ownerCanPreviewReadOnlyTermuxIdentityProbeButNotAFreeformScript() {
        val plan = planner.plan("Jarvis comprueba Termux")!!
        assertEquals(SafeRoutine.TERMUX_ID_DIAGNOSTIC, plan.routine)
        assertEquals(listOf(SafeAutomationStep.REQUEST_TERMUX_ID), plan.steps)
        assertTrue(plan.requiresExplicitTap)
        assertFalse(plan.externalOutcomeVerified)
        assertNull(planner.plan("jarvis ejecuta en termux rm -rf"))
        assertNull(planner.plan("jarvis comprueba termux y corre un script"))
    }

    @Test fun rollbackAndExternalEffectsRemainExplicitlyUnverified() {
        val plan = planner.plan("jarvis revisa shizuku y abre ajustes")!!
        assertFalse(plan.rollbackGuaranteed)
        assertFalse(plan.externalOutcomeVerified)
        assertTrue(plan.requiresExplicitTap)
    }
}
