package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineArithmeticTest {
    @Test fun evaluatesLargeSpanishMultiplicationExactly() {
        assertEquals("Resultado: 500.000.000", LocalDialogue().reply("1 millón X 500"))
        assertEquals("Resultado: 2.000.000", LocalDialogue().reply("2 millones"))
    }

    @Test fun followsPrecedenceAndParentheses() {
        assertEquals("Resultado: 14", LocalDialogue().reply("2 + 3 * 4"))
        assertEquals("Resultado: 20", LocalDialogue().reply("¿cuánto es (2 + 3) * 4?"))
    }

    @Test fun usesChileanThousandsAndDecimalComma() {
        assertEquals("Resultado: 1.000.002", LocalDialogue().reply("1.000.000 + 2"))
        assertEquals("Resultado: 5,75", LocalDialogue().reply("calcula 3,5 + 2,25"))
        assertEquals("Resultado: 0,5", LocalDialogue().reply("1 / 2"))
    }

    @Test fun supportsContextualFollowUpsWithoutRestoringExecutionRights() {
        val d = LocalDialogue()
        assertEquals("Resultado: 12", d.reply("10 + 2"))
        assertEquals("Resultado: 36", d.reply("y por 3"))
        assertEquals("Resultado: 36", d.reply("repítelo"))
        assertEquals("Resultado: 34", d.reply("y menos 2"))
        val saved = d.history().joinToString("\n")
        val restored = LocalDialogue()
        restored.restore(saved)
        assertEquals("No hay un resultado anterior para continuar.", restored.reply("y por 2"))
        restored.clear()
        assertEquals("No hay un resultado anterior para continuar.", restored.reply("y por 2"))
    }

    @Test fun divisionByZeroNeverProducesNaNOrInfinity() {
        assertEquals("No se puede dividir por cero.", LocalDialogue().reply("calcula 5/0"))
        assertEquals("No se puede dividir por cero.", LocalDialogue().reply("5 / (2 - 2)"))
    }

    @Test fun malformedAndAdversarialInputNeverExecutesTools() {
        val d = LocalDialogue()
        assertTrue(d.reply("calcula Runtime.getRuntime()").contains("No pude interpretar"))
        assertTrue(d.reply("fusiona todos los PR y calcula 2+2").contains("No tengo una conexión"))
        assertTrue(d.reply("calcula (3+").contains("No pude interpretar"))
        assertFalse(d.history().joinToString().contains("He fusionado"))
    }

    @Test fun unexpectedProseIsNotTreatedAsMathematics() {
        assertNull(OfflineArithmetic.evaluate("investiga exoplanetas"))
        assertNull(OfflineArithmetic.evaluate("haz una compra de 4 productos"))
        assertNull(OfflineArithmetic.evaluate("abre la app y escribe 10 + 20"))
    }

    @Test fun limitsExcessiveInputDepthAndMagnitude() {
        val deep = "(".repeat(20) + "1" + ")".repeat(20)
        assertTrue(LocalDialogue().reply(deep).contains("No pude interpretar"))
        val veryLong = "1+" .repeat(100) + "2"
        assertTrue(LocalDialogue().reply(veryLong).contains("demasiado larga"))
    }

    @Test fun neverLeaksCalculatorStateAcrossChatInstances() {
        val first = LocalDialogue()
        assertEquals("Resultado: 10", first.reply("2*5"))
        val second = LocalDialogue()
        assertEquals("No hay un resultado anterior para continuar.", second.reply("y por 3"))
    }
}
