package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceHealthFormatterTest {
    @Test fun reportsLocalBatteryChargingThermalAndMemoryReadings() {
        val result = DeviceHealthFormatter.format(DeviceHealthSnapshot(
            batteryPercent = 83, charging = true, thermalStatus = 2,
            availableMemoryBytes = 3_221_225_472L, totalMemoryBytes = 6_442_450_944L,
            lowMemory = false
        ))
        assertTrue(result.contains("Batería: 83% (cargando)"))
        assertTrue(result.contains("Estado térmico: moderado"))
        assertTrue(result.contains("RAM disponible: 3072 MiB de 6144 MiB"))
    }

    @Test fun doesNotInventUnavailableHardwareReadings() {
        val result = DeviceHealthFormatter.format(DeviceHealthSnapshot(
            batteryPercent = null, charging = null, thermalStatus = null,
            availableMemoryBytes = null, totalMemoryBytes = null, lowMemory = null
        ))
        assertTrue(result.contains("Batería: no disponible"))
        assertTrue(result.contains("Estado térmico: no disponible"))
        assertTrue(result.contains("RAM disponible: no disponible"))
        assertFalse(result.contains("0%"))
    }

    @Test fun rejectsImplausibleBatteryAndMemoryValues() {
        val result = DeviceHealthFormatter.format(DeviceHealthSnapshot(
            batteryPercent = 999, charging = false, thermalStatus = 99,
            availableMemoryBytes = 9000, totalMemoryBytes = 500,
            lowMemory = true
        ))
        assertTrue(result.contains("Batería: no disponible"))
        assertTrue(result.contains("Estado térmico: no disponible"))
        assertTrue(result.contains("RAM disponible: no disponible"))
        assertTrue(result.contains("Advertencia: Android reporta poca memoria"))
    }

    @Test fun thermalWarningsOnlyWhenAndroidReportsASevereState() {
        for (state in 3..6) {
            assertTrue(DeviceHealthFormatter.format(DeviceHealthSnapshot(
                60, false, state, null, null, false
            )).contains("Advertencia térmica"))
        }
        for (state in 0..2) {
            assertFalse(DeviceHealthFormatter.format(DeviceHealthSnapshot(
                60, false, state, null, null, false
            )).contains("Advertencia térmica"))
        }
    }

    @Test fun zeroPercentAndZeroAvailableMemoryAreValidMeasurements() {
        val result = DeviceHealthFormatter.format(DeviceHealthSnapshot(
            batteryPercent = 0, charging = false, thermalStatus = 0,
            availableMemoryBytes = 0, totalMemoryBytes = 1_048_576,
            lowMemory = false
        ))
        assertTrue(result.contains("Batería: 0% (sin cargar)"))
        assertTrue(result.contains("RAM disponible: 0 MiB de 1 MiB"))
    }
}
