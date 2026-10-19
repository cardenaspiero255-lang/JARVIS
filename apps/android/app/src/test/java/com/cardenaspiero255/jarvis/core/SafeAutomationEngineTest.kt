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


    @Test fun previewCannotReplaceActiveRunOrStopItImplicitly() {
        val began = java.util.concurrent.CountDownLatch(1)
        val resume = java.util.concurrent.CountDownLatch(1)
        val finished = java.util.concurrent.atomic.AtomicReference<SafeAutomationReport>()
        val gateway = object : SafeAutomationGateway {
            override fun perform(step: SafeAutomationStep): Boolean {
                began.countDown()
                check(resume.await(4, java.util.concurrent.TimeUnit.SECONDS))
                return true
            }
        }
        val engine = SafeAutomationEngine(gateway)
        engine.prepare(SafeRoutine.OPEN_SETTINGS)
        val thread = Thread {
            finished.set(engine.confirmAndRun(SafeRoutine.OPEN_SETTINGS, true))
        }
        thread.start()
        try {
            assertTrue(began.await(4, java.util.concurrent.TimeUnit.SECONDS))
            val blocked = engine.prepare(SafeRoutine.OPEN_TERMUX)
            assertTrue("Must reject preview substitution during a running routine",
                blocked.contains("en ejecución"))
        } finally {
            resume.countDown()
            thread.join(4_000L)
        }
        assertEquals(SafeAutomationOutcome.COMPLETED, finished.get()?.outcome)
        assertEquals(SafeAutomationOutcome.DENIED,
            engine.confirmAndRun(SafeRoutine.OPEN_TERMUX, true).outcome)
    }

    @Test fun concurrentReconfirmationCannotLaunchDuplicateRoutine() {
        val began = java.util.concurrent.CountDownLatch(1)
        val resume = java.util.concurrent.CountDownLatch(1)
        val engine = SafeAutomationEngine(object : SafeAutomationGateway {
            override fun perform(step: SafeAutomationStep): Boolean {
                began.countDown()
                check(resume.await(4, java.util.concurrent.TimeUnit.SECONDS))
                return true
            }
        })
        engine.prepare(SafeRoutine.OPEN_SETTINGS)
        val thread = Thread { engine.confirmAndRun(SafeRoutine.OPEN_SETTINGS, true) }
        thread.start()
        try {
            assertTrue(began.await(4, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(SafeAutomationOutcome.DENIED,
                engine.confirmAndRun(SafeRoutine.OPEN_SETTINGS, true).outcome)
        } finally {
            resume.countDown()
            thread.join(4_000L)
        }
    }

    @Test fun localGatewayAcceptanceMustNotBeSoldAsExternalActionVerification() {
        val gateway = FakeGateway()
        val engine = SafeAutomationEngine(gateway)
        engine.prepare(SafeRoutine.OPEN_TERMUX)
        val report = engine.confirmAndRun(SafeRoutine.OPEN_TERMUX, true)
        assertEquals(SafeAutomationOutcome.COMPLETED, report.outcome)
        // COMPLETED currently means gateway accepted a local step, not that
        // Termux executed a command or external tools confirmed their effects.
        assertFalse(report.externalOutcomeVerified)
        assertFalse(report.rollbackVerified)
    }


    @Test fun previewExpiresAtThirtySecondsEvenIfDialogStaysVisible() {
        var now = 10_000L
        val gateway = FakeGateway()
        val engine = SafeAutomationEngine(gateway, clockMillis = { now })
        engine.prepare(SafeRoutine.OPEN_SETTINGS)
        now += 30_000L
        assertEquals(SafeAutomationOutcome.DENIED,
            engine.confirmAndRun(SafeRoutine.OPEN_SETTINGS, explicitTap = true).outcome)
        assertTrue(gateway.completed.isEmpty())
        engine.prepare(SafeRoutine.OPEN_SETTINGS)
        now += 29_999L
        assertEquals(SafeAutomationOutcome.COMPLETED,
            engine.confirmAndRun(SafeRoutine.OPEN_SETTINGS, explicitTap = true).outcome)
        assertEquals(1, gateway.completed.size)
    }

    @Test fun rolledBackMonotonicClockNeverReplaysStaleApproval() {
        var now = 50_000L
        val gateway = FakeGateway()
        val engine = SafeAutomationEngine(gateway, clockMillis = { now })
        engine.prepare(SafeRoutine.OPEN_TERMUX)
        now = 49_000L
        assertEquals(SafeAutomationOutcome.DENIED,
            engine.confirmAndRun(SafeRoutine.OPEN_TERMUX, explicitTap = true).outcome)
        assertTrue(gateway.completed.isEmpty())
    }

    @Test fun twoStepDiagnosticDoesNotLaunchSettingsWhenShizukuUnavailable() {
        val gateway = FakeGateway()
        gateway.failOn = SafeAutomationStep.CHECK_SHIZUKU
        val engine = SafeAutomationEngine(gateway)
        engine.prepare(SafeRoutine.SHIZUKU_SETTINGS_DIAGNOSTIC)
        val report = engine.confirmAndRun(SafeRoutine.SHIZUKU_SETTINGS_DIAGNOSTIC, true)
        assertEquals(SafeAutomationOutcome.FAILED, report.outcome)
        assertEquals(listOf(SafeAutomationStep.CHECK_SHIZUKU), gateway.completed)
        assertEquals(SafeAutomationStep.CHECK_SHIZUKU, report.failedStep)
        assertFalse(report.rollbackVerified)
        assertFalse(report.externalOutcomeVerified)
    }

    @Test fun previouslyAcceptedExternalDispatchCannotBeClaimedRolledBackOnFailure() {
        val gateway = FakeGateway()
        gateway.failOn = SafeAutomationStep.OPEN_ANDROID_SETTINGS
        val engine = SafeAutomationEngine(gateway)
        engine.prepare(SafeRoutine.SHIZUKU_SETTINGS_DIAGNOSTIC)
        val report = engine.confirmAndRun(SafeRoutine.SHIZUKU_SETTINGS_DIAGNOSTIC, true)
        assertEquals(SafeAutomationOutcome.FAILED, report.outcome)
        assertEquals(1, report.completedSteps)
        assertEquals(SafeAutomationStep.OPEN_ANDROID_SETTINGS, report.failedStep)
        assertFalse(report.rollbackVerified)
        assertFalse(report.externalOutcomeVerified)
        assertEquals(SafeAutomationOutcome.DENIED,
            engine.confirmAndRun(SafeRoutine.SHIZUKU_SETTINGS_DIAGNOSTIC, true).outcome)
    }

    @Test fun recoveryDistinguishesSafeRetryFromUnknownExternalSideEffects() {
        val neverStarted = SafeAutomationReport(
            SafeAutomationOutcome.FAILED, completedSteps = 0, totalSteps = 2,
            failedStep = SafeAutomationStep.CHECK_SHIZUKU)
        assertEquals(SafeRecoveryAdvice.RETRY_REQUIRES_FRESH_CONSENT, neverStarted.recoveryAdvice)
        val partlySent = SafeAutomationReport(
            SafeAutomationOutcome.FAILED, completedSteps = 1, totalSteps = 2,
            failedStep = SafeAutomationStep.OPEN_ANDROID_SETTINGS)
        assertEquals(SafeRecoveryAdvice.MANUAL_RECONCILIATION_REQUIRED,
            partlySent.recoveryAdvice)
        val sent = SafeAutomationReport(SafeAutomationOutcome.COMPLETED, 1, 1)
        assertEquals(SafeRecoveryAdvice.CHECK_EXTERNAL_RESULT, sent.recoveryAdvice)
        val denied = SafeAutomationReport(SafeAutomationOutcome.DENIED, 0, 1)
        assertEquals(SafeRecoveryAdvice.REAUTHORIZE, denied.recoveryAdvice)
        assertFalse(partlySent.rollbackVerified)
    }

    @Test fun termuxIdentityProbeNeedsFreshVisibleConsentAndExternalCallback() {
        val gateway = FakeGateway()
        val engine = SafeAutomationEngine(gateway)
        assertEquals(SafeAutomationOutcome.DENIED,
            engine.confirmAndRun(SafeRoutine.TERMUX_ID_DIAGNOSTIC, true).outcome)
        engine.prepare(SafeRoutine.TERMUX_ID_DIAGNOSTIC)
        val result = engine.confirmAndRun(SafeRoutine.TERMUX_ID_DIAGNOSTIC, true)
        assertEquals(SafeAutomationOutcome.COMPLETED, result.outcome)
        assertEquals(listOf(SafeAutomationStep.REQUEST_TERMUX_ID), gateway.completed)
        assertFalse(result.externalOutcomeVerified)
        assertEquals(SafeRecoveryAdvice.CHECK_EXTERNAL_RESULT, result.recoveryAdvice)
        assertEquals(SafeAutomationOutcome.DENIED,
            engine.confirmAndRun(SafeRoutine.TERMUX_ID_DIAGNOSTIC, true).outcome)
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
