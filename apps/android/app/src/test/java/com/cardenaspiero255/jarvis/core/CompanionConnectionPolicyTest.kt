package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionConnectionPolicyTest {
    private val policy = CompanionConnectionPolicy()

    @Test fun shizukuNeedsTheRunningServiceAndSeparateUserPermission() {
        assertEquals(CompanionState.NOT_INSTALLED,
            policy.state(CompanionKind.SHIZUKU, CompanionEvidence(installed = false)))
        assertEquals(CompanionState.NEEDS_START,
            policy.state(CompanionKind.SHIZUKU, CompanionEvidence(installed = true)))
        assertEquals(CompanionState.NEEDS_PERMISSION,
            policy.state(CompanionKind.SHIZUKU, CompanionEvidence(
                installed = true, transportReady = true)))
        assertEquals(CompanionState.READY,
            policy.state(CompanionKind.SHIZUKU, CompanionEvidence(
                installed = true, transportReady = true, authorized = true)))
    }

    @Test fun installedDhizukuOwnDroidAndTermuxAreNotMistakenForAuthorizedBridges() {
        listOf(CompanionKind.OWNDROID, CompanionKind.TERMUX)
            .forEach { kind ->
                assertEquals(CompanionState.NOT_INSTALLED,
                    policy.state(kind, CompanionEvidence(installed = false)))
                assertEquals(CompanionState.NEEDS_SETUP,
                    policy.state(kind, CompanionEvidence(
                        installed = true, transportReady = true, authorized = true)))
            }
    }

    @Test fun dhizukuMustInitializeAndBeApprovedBeforeShowingReady() {
        assertEquals(CompanionState.NOT_INSTALLED,
            policy.state(CompanionKind.DHIZUKU, CompanionEvidence(false)))
        assertEquals(CompanionState.NEEDS_SETUP,
            policy.state(CompanionKind.DHIZUKU, CompanionEvidence(true)))
        assertEquals(CompanionState.NEEDS_PERMISSION,
            policy.state(CompanionKind.DHIZUKU, CompanionEvidence(
                installed = true, transportReady = true)))
        assertEquals(CompanionState.READY,
            policy.state(CompanionKind.DHIZUKU, CompanionEvidence(
                installed = true, transportReady = true, authorized = true)))
    }

    @Test fun companionLaunchNeedsTheOwnerToTap() {
        assertFalse(policy.canOpenCompanion(installed = true, explicitTap = false))
        assertFalse(policy.canOpenCompanion(installed = false, explicitTap = true))
        assertTrue(policy.canOpenCompanion(installed = true, explicitTap = true))
    }

    @Test fun noDangerousOperationsAreImplicitlyEnabledByDetection() {
        assertFalse(policy.mayExecutePrivilegedAction())
    }
}
