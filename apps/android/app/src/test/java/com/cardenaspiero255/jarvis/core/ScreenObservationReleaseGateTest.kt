package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenObservationReleaseGateTest {
    @Test fun experimentalProjectionIsDisabledUntilPhysicalPrivacyAcceptance() {
        assertFalse(ScreenObservationReleaseGate.ENABLED)
        assertTrue(ScreenObservationReleaseGate.UNAVAILABLE_MESSAGE.contains("desactivada"))
        assertTrue(ScreenObservationReleaseGate.UNAVAILABLE_MESSAGE.contains("imágenes elegidas"))
    }
}
