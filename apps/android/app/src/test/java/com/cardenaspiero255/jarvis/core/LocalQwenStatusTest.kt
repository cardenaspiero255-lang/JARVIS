package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class LocalQwenStatusTest {
    @Test fun absentModelIsNotPresentedAsInstalled() {
        assertTrue(LocalQwenStatus.describe(LocalQwenStatus.Code.ABSENT).contains("NO instalado"))
    }

    @Test fun damagedModelIsNotPresentedAsUsable() {
        val status = LocalQwenStatus.describe(LocalQwenStatus.Code.SHA_MISMATCH)
        assertTrue(status.contains("SHA-256"))
        assertFalse(status.contains("listo para generar"))
    }

    @Test fun verifiedButLowMemoryIsNotPresentedAsReady() {
        val status = LocalQwenStatus.describe(LocalQwenStatus.Code.LOW_MEMORY)
        assertTrue(status.contains("verificado"))
        assertTrue(status.contains("memoria"))
        assertFalse(status.contains("listo para generar"))
    }

    @Test fun readyRequiresVerifiedAndRunnableStatus() {
        val status = LocalQwenStatus.describe(LocalQwenStatus.Code.READY)
        assertTrue(status.contains("SHA-256"))
        assertTrue(status.contains("listo para generar"))
    }

    @Test fun interruptedDownloadDoesNotClaimInstallation() {
        assertFalse(LocalQwenStatus.describe(LocalQwenStatus.Code.INTERRUPTED).contains("listo para generar"))
    }
}
