package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenPrivacyIntegrationTest {
    @Test fun privacyLockIncludesProjectionBeforeMemoryOrChat() {
        val channels = SessionLockChannel.values().toList()
        val visionIndex = channels.indexOf(SessionLockChannel.SCREEN_PROJECTION)
        assertTrue(visionIndex >= 0)
        assertTrue(visionIndex < channels.indexOf(SessionLockChannel.PRIVATE_MEMORY))
    }

    @Test fun screenStopRunsDespiteEarlierFailure() {
        val visited = mutableListOf<SessionLockChannel>()
        val actions: Map<SessionLockChannel, () -> Unit> =
            SessionLockChannel.values().associateWith { channel ->
                {
                    visited.add(channel)
                    if (channel == SessionLockChannel.SPOKEN_REPLY) {
                        throw IllegalStateException("TTS already shut down")
                    }
                }
            }
        val report = SessionLockCoordinator().lock(actions)
        assertFalse(report.complete)
        assertEquals(setOf(SessionLockChannel.SPOKEN_REPLY), report.failed)
        assertTrue(visited.contains(SessionLockChannel.SCREEN_PROJECTION))
    }

    @Test fun stoppingProjectionRevokesSamplingAndPreventsReusingPreviousConsent() {
        val policy = ScreenObservationPolicy()
        assertTrue(policy.start(true, 1_000L))
        assertTrue(policy.sample(1_000L))
        policy.stop()
        assertFalse(policy.isActive(4_000L))
        assertFalse(policy.sample(4_000L))
    }
}
