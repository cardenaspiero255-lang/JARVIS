package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameReadinessAdvisorTest {
    private fun snapshot(
        battery: Int? = 75, charging: Boolean? = false, thermal: Int? = 0,
        ram: Long? = 4_000_000_000L, total: Long? = 8_000_000_000L,
        lowMemory: Boolean? = false
    ) = DeviceHealthSnapshot(battery, charging, thermal, ram, total, lowMemory)

    @Test fun ordinaryReadingsAreReadyButNeverCertified() {
        val result = GameReadinessAdvisor.assess(snapshot(), GamingPreference.BALANCED)
        assertEquals(GameReadinessLevel.READY, result.level)
        val text = GameReadinessAdvisor.format(result)
        assertTrue(text.contains("LISTO CON RESERVAS"))
        assertTrue(text.contains("Sin certificación"))
        assertFalse(text.contains("FPS garantizados"))
    }

    @Test fun criticalThermalStatusAlwaysOutranksBatteryAndProfile() {
        for (status in 4..6) {
            for (mode in GamingPreference.values()) {
                val report = GameReadinessAdvisor.assess(
                    snapshot(battery = 90, thermal = status), mode)
                assertEquals(GameReadinessLevel.POSTPONE, report.level)
                assertTrue(GameReadinessAdvisor.format(report).contains("posponer"))
            }
        }
    }

    @Test fun highThermalOrAndroidLowMemoryWarnsWithoutChangingAnything() {
        val hot = GameReadinessAdvisor.assess(snapshot(thermal = 3),
            GamingPreference.COMPETITIVE)
        val low = GameReadinessAdvisor.assess(snapshot(lowMemory = true),
            GamingPreference.VISUAL_QUALITY)
        assertEquals(GameReadinessLevel.CAUTION, hot.level)
        assertEquals(GameReadinessLevel.CAUTION, low.level)
        assertTrue(GameReadinessAdvisor.format(hot).contains("estabilidad"))
        assertTrue(GameReadinessAdvisor.format(low).contains("memoria"))
    }

    @Test fun batteryUnderFifteenAndNotChargingRequiresCaution() {
        val low = GameReadinessAdvisor.assess(snapshot(battery = 10, charging = false),
            GamingPreference.BALANCED)
        assertEquals(GameReadinessLevel.CAUTION, low.level)
        assertTrue(GameReadinessAdvisor.format(low).contains("batería"))
        assertEquals(GameReadinessLevel.READY,
            GameReadinessAdvisor.assess(snapshot(battery = 10, charging = true),
                GamingPreference.BALANCED).level)
    }

    @Test fun missingOrImplausibleMeasurementsNeverClaimReadiness() {
        val missing = GameReadinessAdvisor.assess(
            snapshot(battery = null, thermal = null, ram = null, total = null,
                lowMemory = null), GamingPreference.BALANCED)
        assertEquals(GameReadinessLevel.UNKNOWN, missing.level)
        assertTrue(GameReadinessAdvisor.format(missing).contains("insuficientes"))
        val corrupt = GameReadinessAdvisor.assess(
            snapshot(battery = 900, thermal = 100, ram = 9000L, total = 500L),
            GamingPreference.BALANCED)
        assertEquals(GameReadinessLevel.UNKNOWN, corrupt.level)
    }

    @Test fun dangerWinsEvenIfSomeSignalsUnknown() {
        assertEquals(GameReadinessLevel.POSTPONE,
            GameReadinessAdvisor.assess(snapshot(battery = null, thermal = 5,
                ram = null, total = null), GamingPreference.COMPETITIVE).level)
    }

    @Test fun zeroBatteryNotChargingIsConservativelyAWarning() {
        assertEquals(GameReadinessLevel.CAUTION,
            GameReadinessAdvisor.assess(snapshot(battery = 0, charging = false),
                GamingPreference.VISUAL_QUALITY).level)
    }

    @Test fun noProfileClaimsToEnableHardwareOrOpenApplications() {
        for (mode in GamingPreference.values()) {
            val text = GameReadinessAdvisor.format(
                GameReadinessAdvisor.assess(snapshot(thermal = 2), mode))
            assertFalse(text.contains("optimización aplicada"))
            assertFalse(text.contains("procesador acelerado"))
            assertFalse(text.contains("juego iniciado"))
        }
    }
}
