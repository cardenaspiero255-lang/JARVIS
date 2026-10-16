package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeVoiceReadOnlyRoutinesTest {
    @Test fun exactSpanishRequestsOnlyOpenPreview() {
        assertEquals(SafeRoutine.DEVICE_DIAGNOSTIC,
            SafeAutomationVoiceRouter.parse("Jarvis, diagnostica teléfono"))
        assertEquals(SafeRoutine.GAMING_READINESS,
            SafeAutomationVoiceRouter.parse("Jarvis, prepara juego"))
        assertEquals(SafeRoutine.LIST_APPS,
            SafeAutomationVoiceRouter.parse("Jarvis, muestra aplicaciones"))
    }

    @Test fun conversationalNegationsCannotInvokeRoutine() {
        assertNull(SafeAutomationVoiceRouter.parse("jarvis no prepares juego"))
        assertNull(SafeAutomationVoiceRouter.parse("jarvis como diagnosticar telefono"))
        assertNull(SafeAutomationVoiceRouter.parse("jarvis borra datos del telefono"))
    }

    @Test fun noVoiceExecutionWithoutSeparateTap() {
        val stub = object : SafeAutomationGateway {
            var calls = 0
            override fun perform(step: SafeAutomationStep): Boolean { calls++; return true }
        }
        val engine = SafeAutomationEngine(stub)
        val routine = SafeRoutine.GAMING_READINESS
        engine.prepare(routine)
        assertEquals(SafeAutomationOutcome.DENIED, engine.confirmAndRun(routine, false).outcome)
        assertEquals(0, stub.calls)
        assertEquals(SafeAutomationOutcome.COMPLETED, engine.confirmAndRun(routine, true).outcome)
        assertEquals(1, stub.calls)
        assertEquals(SafeAutomationOutcome.DENIED, engine.confirmAndRun(routine, true).outcome)
    }

    @Test fun newRoutinesHaveFixedReadOnlySteps() {
        assertEquals(listOf(SafeAutomationStep.READ_DEVICE_DIAGNOSTIC),
            SafeRoutine.DEVICE_DIAGNOSTIC.steps)
        assertEquals(listOf(SafeAutomationStep.READ_GAMING_STATUS),
            SafeRoutine.GAMING_READINESS.steps)
        assertTrue(SafeRoutine.LIST_APPS.steps.all {
            it == SafeAutomationStep.LIST_LAUNCHABLE_APPS
        })
    }
}
