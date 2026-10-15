package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameHubStatusPolicyTest {
    private val policy = GameHubStatusPolicy()

    @Test fun constantsMatchRealGameHubPublishedStatusContract() {
        assertEquals("com.cardenaspiero255.gamehubultra", policy.packageName)
        assertEquals("com.cardenaspiero255.gamehubultra.jarvis.status", policy.authority)
        assertEquals("com.cardenaspiero255.gamehubultra.permission.JARVIS_READ_STATUS",
            policy.readPermission)
        assertEquals("v1/status", policy.path)
    }

    @Test fun noHandshakeWithoutMatchingAndroidSigningCertificate() {
        assertFalse(policy.canQuery(true, false, true))
        assertFalse(policy.canQuery(true, true, false))
        assertFalse(policy.canQuery(false, true, true))
        assertTrue(policy.canQuery(true, true, true))
    }

    @Test fun onlyStrictVersionOneReadOnlyStatusIsAccepted() {
        assertTrue(policy.accept(1, policy.packageName, "READ_ONLY", 0, 0))
        assertFalse(policy.accept(2, policy.packageName, "READ_ONLY", 0, 0))
        assertFalse(policy.accept(1, "com.evil.app", "READ_ONLY", 0, 0))
        assertFalse(policy.accept(1, policy.packageName, "READY_TO_RUN", 0, 0))
        assertFalse(policy.accept(1, policy.packageName, "READ_ONLY", 1, 0))
        assertFalse(policy.accept(1, policy.packageName, "READ_ONLY", 0, 1))
    }

    @Test fun evenTrustedStatusDoesNotGrantRemoteCommands() {
        assertFalse(policy.canExecuteRemotely)
    }
}
