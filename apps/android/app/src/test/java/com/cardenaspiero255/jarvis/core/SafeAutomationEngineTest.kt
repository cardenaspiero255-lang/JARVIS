package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeAutomationEngineTest {
    private class FakeGateway : SafeAutomationGateway {
        val completed = mutableListOf<SafeAutomationStep>()
        var failOn: SafeAutomationStep? = null
        override fun perform(step: SafeAutomationStep): Boolean {
            completed.add(step)
            return step != failOn
        }
    }

    @Test fun explicitPerRunApprovalIsMandatoryAndOneUse() {
        val gateway = FakeGateway()
        val engine = SafeAutomationEngine(gateway)
        assertEquals(SafeAutomationOutcome.DENIED,
            engine.confirmAndRun(SafeRoutine.SHIZUKU_ASSIST, true).outcome)
        engine.prepare(SafeRoutine.SHIZUKU_ASSIST)
        assertEquals(SafeAutomationOutcome.DENIED,
            engine.confirmAndRun(SafeRoutine.SHIZUKU_ASSIST, false).outcome)
        assertTrue(gateway.completed.isEmpty())
        engine.prepare(SafeRoutine.SHIZUKU_ASSIST)
        assertEquals(SafeAutomationOutcome.COMPLETED,
            engine.confirmAndRun(SafeRoutine.SHIZUKU_ASSIST, true).outcome)
        assertEquals(listOf(SafeAutomationStep.CHECK_SHIZUKU,
            SafeAutomationStep.OPEN_SHIZUKU), gateway.completed)
        assertEquals(SafeAutomationOutcome.DENIED,
            engine.confirmAndRun(SafeRoutine.SHIZUKU_ASSIST, true).outcome)
    }

    @Test fun routineSelectionCannotBeSwappedAfterPreview() {
        val gateway = FakeGateway()
        val engine = SafeAutomationEngine(gateway)
        engine.prepare(SafeRoutine.SHIZUKU_ASSIST)
        assertEquals(SafeAutomationOutcome.DENIED,
            engine.confirmAndRun(SafeRoutine.OPEN_SETTINGS, true).outcome)
        assertTrue(gateway.completed.isEmpty())
    }

    @Test fun partialFailureAbortsRemainingSteps() {
        val gateway = FakeGateway()
        gateway.failOn = SafeAutomationStep.CHECK_SHIZUKU
        val engine = SafeAutomationEngine(gateway)
        engine.prepare(SafeRoutine.SHIZUKU_ASSIST)
        val result = engine.confirmAndRun(SafeRoutine.SHIZUKU_ASSIST, true)
        assertEquals(SafeAutomationOutcome.FAILED, result.outcome)
        assertEquals(0, result.completedSteps)
        assertEquals(listOf(SafeAutomationStep.CHECK_SHIZUKU), gateway.completed)
    }

    @Test fun emergencyStopRevokesPendingAuthorization() {
        val gateway = FakeGateway()
        val engine = SafeAutomationEngine(gateway)
        engine.prepare(SafeRoutine.OPEN_SETTINGS)
        engine.stop()
        assertEquals(SafeAutomationOutcome.DENIED,
            engine.confirmAndRun(SafeRoutine.OPEN_SETTINGS, true).outcome)
        assertTrue(gateway.completed.isEmpty())
    }

    @Test fun stopIssuedDuringStepPreventNextStep() {
        lateinit var engine: SafeAutomationEngine
        val completed = mutableListOf<SafeAutomationStep>()
        engine = SafeAutomationEngine(object : SafeAutomationGateway {
            override fun perform(step: SafeAutomationStep): Boolean {
                completed.add(step)
                engine.stop()
                return true
            }
        })
        engine.prepare(SafeRoutine.SHIZUKU_ASSIST)
        assertEquals(SafeAutomationOutcome.STOPPED,
            engine.confirmAndRun(SafeRoutine.SHIZUKU_ASSIST, true).outcome)
        assertEquals(listOf(SafeAutomationStep.CHECK_SHIZUKU), completed)
    }

    @Test fun spokenPunctuationCanPreviewButNotExecuteOrExpandCommands() {
        assertEquals(SafeRoutine.SHIZUKU_ASSIST,
            SafeAutomationVoiceRouter.parse("¡JARVIS, revisa Shizuku!"))
        assertEquals(SafeRoutine.OPEN_SETTINGS,
            SafeAutomationVoiceRouter.parse("Jarvis, abre ajustes."))
        assertEquals(null,
            SafeAutomationVoiceRouter.parse("Jarvis revisa Shizuku y borra datos"))
        assertEquals(null,
            SafeAutomationVoiceRouter.parse("Jarvis abre Termux; rm -rf"))
    }

    @Test fun voiceRouterCannotEmitUnboundedOrDestructiveActions() {
        assertEquals(SafeRoutine.SHIZUKU_ASSIST,
            SafeAutomationVoiceRouter.parse("Jarvis, revisa shizuku"))
        assertEquals(SafeRoutine.OPEN_SETTINGS,
            SafeAutomationVoiceRouter.parse("jarvis abre ajustes"))
        assertEquals(null, SafeAutomationVoiceRouter.parse(
            "jarvis elimina mis apps y reinicia"))
        assertEquals(null, SafeAutomationVoiceRouter.parse(
            "jarvis ejecuta rm -rf /"))
        assertFalse(SafeAutomationVoiceRouter.parse("jarvis borra todo") != null)
    }
}
