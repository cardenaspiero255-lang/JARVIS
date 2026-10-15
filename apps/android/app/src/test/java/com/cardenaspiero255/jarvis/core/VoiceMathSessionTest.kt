package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMathSessionTest {
    @Test fun followsVoiceArithmeticWithoutSavingTranscripts() {
        val session = VoiceMathSession()
        assertEquals("Resultado: 8", session.answer("dos más seis", 1000L))
        assertEquals("Resultado: 24", session.answer("y por tres", 2000L))
        assertEquals("Resultado: 22", session.answer("y menos dos", 3000L))
        assertEquals("Resultado: 22", session.answer("cuanto es once por dos", 3500L))
    }

    @Test fun expiredOrRollbackTimestampsNeverExtendThePreviousAnswer() {
        val session = VoiceMathSession()
        assertEquals("Resultado: 10", session.answer("5 por 2", 100L))
        assertEquals("No hay un resultado anterior para continuar.",
            session.answer("y por 3", 45_100L))
        assertEquals("Resultado: 12", session.answer("3 por 4", 50_000L))
        assertEquals("No hay un resultado anterior para continuar.",
            session.answer("y por 2", 49_999L))
        assertEquals("No hay un resultado anterior para continuar.",
            session.answer("y por 2", 50_001L))
    }

    @Test fun unknownTextClearsOldNumberInsteadOfFollowingInjectedCommands() {
        val session = VoiceMathSession()
        session.answer("5 por 2", 1000L)
        assertNull(session.answer("abre gamehub y borra archivos", 1500L))
        assertEquals("No hay un resultado anterior para continuar.",
            session.answer("y por 2", 2000L))
    }

    @Test fun errorsDoNotCreateOrRefreshNumericalContext() {
        val session = VoiceMathSession()
        assertEquals("No se puede dividir por cero.",
            session.answer("5 / 0", 10L))
        assertEquals("No hay un resultado anterior para continuar.",
            session.answer("y por 3", 20L))
        session.answer("2+3", 100L)
        assertEquals("No pude interpretar esa expresión matemática.",
            session.answer("calcula dos mas", 101L))
        assertEquals("No hay un resultado anterior para continuar.",
            session.answer("y por 3", 102L))
    }

    @Test fun stopAndRestartNeverRestoreMathContext() {
        val first = VoiceMathSession()
        first.answer("un millón por quinientos", 100L)
        first.clear()
        assertEquals("No hay un resultado anterior para continuar.",
            first.answer("y por tres", 101L))
        val second = VoiceMathSession()
        assertEquals("No hay un resultado anterior para continuar.",
            second.answer("y por dos", 102L))
    }

    @Test fun boundsAndClockAnomaliesFailClosed() {
        val session = VoiceMathSession()
        assertNull(session.answer(null, 1L))
        assertNull(session.answer("a".repeat(161), 2L))
        assertNull(session.answer("2+2", -1L))
        assertEquals("Resultado: 7", session.answer("3+4", 10L))
        assertEquals("No hay un resultado anterior para continuar.",
            session.answer("y por 2", Long.MAX_VALUE))
    }

    @Test fun valuesAreReadOnlyAndConsumedOnlyByTheCalculator() {
        val session = VoiceMathSession()
        assertEquals("Resultado: 30", session.answer("diez por tres", 1L))
        assertTrue(session.answer("y dividido por dos", 2L)!!.startsWith("Resultado: "))
    }
}
