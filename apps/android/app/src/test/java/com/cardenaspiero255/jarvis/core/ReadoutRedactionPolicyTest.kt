package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReadoutRedactionPolicyTest {
    @Test fun everyPrivateReadoutTypeHasFixedNonSensitiveReplacement() {
        for (kind in SensitiveReadout.values()) {
            val result = ReadoutRedactionPolicy.redacted(kind)
            assertFalse(result.isBlank())
            assertFalse(result.contains("83%"))
            assertFalse(result.contains("gamehub instalado"))
            assertFalse(result.contains("25 GB"))
            assertFalse(result.contains("Contraseña"))
            assertFalse(result.contains("LISTO CON RESERVAS"))
        }
    }

    @Test fun redactedStringsDescribeThatOldReadingsAreNotAvailable() {
        assertEquals("Diagnóstico oculto. Consulta de nuevo.",
            ReadoutRedactionPolicy.redacted(SensitiveReadout.CAPABILITIES))
        assertEquals("Lecturas de salud ocultas. Consulta de nuevo.",
            ReadoutRedactionPolicy.redacted(SensitiveReadout.DEVICE_HEALTH))
        assertEquals("Evaluación de juego oculta. Evalúa de nuevo.",
            ReadoutRedactionPolicy.redacted(SensitiveReadout.GAME_READINESS))
    }
}
