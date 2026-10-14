package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandsSessionGateTest {
    @Test fun aNewProcessSessionIsDisarmedByDefault() {
        assertFalse(HandsSessionGate().isArmed())
    }

    @Test fun explicitEnableArmsOnlyCurrentSession() {
        val current = HandsSessionGate()
        current.arm()
        assertTrue(current.isArmed())
        assertFalse(HandsSessionGate().isArmed())
    }

    @Test fun emergencyStopTakesEffectImmediately() {
        val gate = HandsSessionGate()
        gate.arm()
        gate.stop()
        assertFalse(gate.isArmed())
    }
}
