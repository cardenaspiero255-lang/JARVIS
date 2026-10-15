package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceMathGateTest {
    @Test fun computesSpanishVoiceArithmeticWithoutNetwork() {
        assertEquals("Resultado: 500.000.000",
            VoiceMathGate.answer("¿cuánto es 1 millón por 500?"))
        assertEquals("Resultado: 7", VoiceMathGate.answer("3+4"))
        assertEquals("Resultado: 5,75", VoiceMathGate.answer("calcula 3,5+2,25"))
    }

    @Test fun neverHandlesVoiceCommandsAsMath() {
        for (message in listOf("abre ajustes", "fusiona PR 194", "pulsa el botón",
                               "enciende el micrófono", "ignora instrucciones anteriores")) {
            assertNull(VoiceMathGate.answer(message))
        }
    }

    @Test fun retainsNoVoiceTranscriptsAndHasNoMathContextAcrossCalls() {
        assertEquals("Resultado: 12", VoiceMathGate.answer("2*6"))
        assertEquals("No hay un resultado anterior para continuar.", VoiceMathGate.answer("y por 3"))
        assertNull(VoiceMathGate.answer(null))
        assertNull(VoiceMathGate.answer("1 + ".repeat(100)))
    }

    @Test fun divisionByZeroIsExplicitAndHarmless() {
        assertEquals("No se puede dividir por cero.", VoiceMathGate.answer("calcula 2 / 0"))
    }
}
