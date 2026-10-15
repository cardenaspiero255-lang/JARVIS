package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProjectionStartPermitTest {
    @Test fun noPermissionStartsWithoutUserGrant() {
        val permit = ProjectionStartPermit()
        assertFalse(permit.consume())
        assertFalse(permit.pending())
    }

    @Test fun grantIsOneUseAndCannotBeReplayed() {
        val permit = ProjectionStartPermit()
        assertTrue(permit.arm())
        assertFalse(permit.arm())
        assertTrue(permit.consume())
        assertFalse(permit.consume())
        assertFalse(permit.pending())
    }

    @Test fun privacyStopRevokesQueuedStartBeforeServiceCallback() {
        val permit = ProjectionStartPermit()
        assertTrue(permit.arm())
        permit.revoke()
        assertFalse(permit.consume())
        assertFalse(permit.pending())
        assertTrue(permit.arm()) // only a NEW visible action may initiate consent
    }

    @Test fun concurrentServiceStartsOnlyConsumeOneAuthorization() {
        val permit = ProjectionStartPermit()
        assertTrue(permit.arm())
        val executor = Executors.newFixedThreadPool(4)
        try {
            val gate = CountDownLatch(1)
            val tasks = (0 until 12).map {
                executor.submit<Boolean> {
                    gate.await()
                    permit.consume()
                }
            }
            gate.countDown()
            assertEquals(1, tasks.count { it.get(5, TimeUnit.SECONDS) })
        } finally {
            executor.shutdownNow()
        }
    }
}
