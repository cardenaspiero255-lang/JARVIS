package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class ShizukuFixedProbePolicyTest {
    @Test fun onlyFixedReadOnlyOperationsAreAvailable() {
        assertEquals(7, ShizukuFixedProbe.values().size)
        assertEquals(listOf("/system/bin/getprop", "ro.build.version.release"),
            ShizukuFixedProbe.ANDROID_RELEASE.command)
        assertEquals(listOf("/system/bin/pm", "path", "android"),
            ShizukuFixedProbe.ANDROID_SYSTEM_PACKAGE.command)
        assertNull(ShizukuFixedProbe.fromCode(-1))
        assertNull(ShizukuFixedProbe.fromCode(999))
    }

    @Test fun shellIdentityIsRequiredAndRootIsNotAssumed() {
        assertTrue(ShizukuFixedProbePolicy.isAllowedUid(2000))
        assertFalse(ShizukuFixedProbePolicy.isAllowedUid(0))
        assertFalse(ShizukuFixedProbePolicy.isAllowedUid(10234))
    }

    @Test fun responsesAreBoundedAndParsedByExactProbe() {
        assertTrue(ShizukuFixedProbePolicy.isValid(ShizukuFixedProbe.ANDROID_RELEASE, "14"))
        assertTrue(ShizukuFixedProbePolicy.isValid(
            ShizukuFixedProbe.ANDROID_SYSTEM_PACKAGE,
            "package:/system/framework/framework-res.apk"))
        assertFalse(ShizukuFixedProbePolicy.isValid(ShizukuFixedProbe.ANDROID_RELEASE,
            "uid=2000(shell)"))
        assertFalse(ShizukuFixedProbePolicy.isValid(ShizukuFixedProbe.ANDROID_RELEASE,
            "14\nextra output"))
        assertFalse(ShizukuFixedProbePolicy.isValid(ShizukuFixedProbe.ANDROID_RELEASE,
            "9".repeat(512)))
        assertFalse(ShizukuFixedProbePolicy.isValid(ShizukuFixedProbe.ANDROID_SYSTEM_PACKAGE,
            "package:/data/data/sensitive"))
    }

    @Test fun requireBothChecksBeforeReportingVerified() {
        assertFalse(ShizukuFixedProbePolicy.complete(2000, "14", ""))
        assertFalse(ShizukuFixedProbePolicy.complete(0, "14", "package:/system/framework/framework-res.apk"))
        assertTrue(ShizukuFixedProbePolicy.complete(2000, "14",
            "package:/system/framework/framework-res.apk"))
    }
}
