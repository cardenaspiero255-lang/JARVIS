package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInspectionFormatterTest {
    @Test fun formatsKnownValuesWithoutClaimingOptimization() {
        val msg = DeviceInspectionFormatter.describe(DeviceInspection(
            "Vivo V25 Pro", 34, 66, 2048, false, 1f))
        assertTrue(msg.contains("Vivo V25 Pro"))
        assertTrue(msg.contains("66%"))
        assertTrue(msg.contains("2048 MB"))
        assertTrue(msg.contains("1.0x"))
    }

    @Test fun unavailableSettingsRemainUnknown() {
        val msg = DeviceInspectionFormatter.describe(DeviceInspection("X", 34,
            null, null, null, null))
        assertTrue(msg.contains("no accesible"))
        assertTrue(msg.contains("no disponible"))
    }
}
