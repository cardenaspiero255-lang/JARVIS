package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Test

class OmegaHandsStatusTest {
    @Test fun showsActualPermissionVersusEmergencyStop() {
        assertEquals("Accesibilidad no conectada", HandsStatus.present(false, true))
        assertEquals("HANDS detenido", HandsStatus.present(true, true))
        assertEquals("HANDS habilitado: Inicio y Atrás por pulsación", HandsStatus.present(true, false))
    }

    @Test fun pauseOverridesConnection() {
        assertEquals("HANDS detenido", HandsStatus.present(true, true))
    }
}
