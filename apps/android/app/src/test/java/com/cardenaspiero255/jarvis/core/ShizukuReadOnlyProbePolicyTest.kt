package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuReadOnlyProbePolicyTest {
    @Test fun adbStartedServiceIsReportedAsShellNotRoot() {
        assertEquals(ShizukuReadOnlyStatus.SHELL,
            ShizukuReadOnlyProbePolicy.classify(uid = 2000, apiVersion = 13))
    }

    @Test fun rootServiceIsDistinguishedFromAdb() {
        assertEquals(ShizukuReadOnlyStatus.ROOT,
            ShizukuReadOnlyProbePolicy.classify(uid = 0, apiVersion = 13))
    }

    @Test fun unknownIdentityDoesNotClaimPrivilegedAccess() {
        assertEquals(ShizukuReadOnlyStatus.UNKNOWN_IDENTITY,
            ShizukuReadOnlyProbePolicy.classify(uid = 10000, apiVersion = 13))
    }

    @Test fun invalidRemoteIdentityFailsClosed() {
        assertEquals(ShizukuReadOnlyStatus.FAILED,
            ShizukuReadOnlyProbePolicy.classify(uid = -1, apiVersion = 13))
        assertEquals(ShizukuReadOnlyStatus.FAILED,
            ShizukuReadOnlyProbePolicy.classify(uid = 2000, apiVersion = -1))
    }
}
