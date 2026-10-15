package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelGateTest {
    @Test fun rejectsMissingOrUnverifiedModels() {
        assertFalse(LocalModelGate.canRun(false, true, 512, 1024, 4096, false))
        assertFalse(LocalModelGate.canRun(true, false, 512, 1024, 4096, false))
    }

    @Test fun rejectsLowMemoryAndThermalEmergency() {
        assertFalse(LocalModelGate.canRun(true, true, 1024, 2048, 3000, false))
        assertFalse(LocalModelGate.canRun(true, true, 1024, 2048, 4096, true))
    }

    @Test fun permitsVerifiedModelWithMemoryHeadroom() {
        assertTrue(LocalModelGate.canRun(true, true, 512, 1024, 4096, false))
        assertFalse(LocalModelGate.canRun(true, true, 512, 1024, 2560, false))
    }

    @Test fun invalidSizesFailClosed() {
        assertFalse(LocalModelGate.canRun(true, true, -1, 1024, 4096, false))
        assertFalse(LocalModelGate.canRun(true, true, 512, 0, 4096, false))
    }
}
