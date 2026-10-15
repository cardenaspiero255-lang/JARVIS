package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLockCoordinatorTest {
    @Test fun locksEveryDeclaredChannelInDeterministicOrder() {
        val calls = mutableListOf<SessionLockChannel>()
        val actions = SessionLockChannel.values().associateWith { channel ->
            { calls.add(channel); Unit }
        }
        val report = SessionLockCoordinator().lock(actions)
        assertEquals(SessionLockChannel.values().toList(), calls)
        assertTrue(report.complete)
        assertTrue(report.failed.isEmpty())
    }

    @Test fun oneFailedModuleCannotPreventLockingRemainingModules() {
        val calls = mutableListOf<SessionLockChannel>()
        val actions: Map<SessionLockChannel, () -> Unit> =
            SessionLockChannel.values().associateWith { channel ->
                {
                    calls.add(channel)
                    if (channel == SessionLockChannel.IMAGE_OCR) {
                        throw IllegalStateException("OCR already stopped")
                    }
                }
            }
        val report = SessionLockCoordinator().lock(actions)
        assertFalse(report.complete)
        assertEquals(setOf(SessionLockChannel.IMAGE_OCR), report.failed)
        assertEquals(SessionLockChannel.values().toList(), calls)
    }

    @Test fun missingStopStepIsReportedNotSilentlyConsideredComplete() {
        val report = SessionLockCoordinator().lock(
            mapOf(SessionLockChannel.VOICE_REQUEST to { Unit })
        )
        assertFalse(report.complete)
        assertEquals(
            SessionLockChannel.values().toSet() - SessionLockChannel.VOICE_REQUEST,
            report.failed
        )
    }

    @Test fun repeatedLockIsSafeAndNeverArmsAChannel() {
        val actions = SessionLockChannel.values().associateWith { { Unit } }
        val gate = SessionLockCoordinator()
        assertTrue(gate.lock(actions).complete)
        assertTrue(gate.lock(actions).complete)
    }
}
