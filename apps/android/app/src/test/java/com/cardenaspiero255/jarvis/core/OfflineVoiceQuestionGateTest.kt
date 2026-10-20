package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class OfflineVoiceQuestionGateTest {
    @Test fun recognizesNaturalSpanishQuestionOnlyAfterJarvis() {
        val gate = OfflineVoiceQuestionGate()
        val captured = gate.observe("Jarvis, ¿qué es un procesador?", 1_000L)
        assertEquals(OfflineVoiceDecision.QUESTION, captured.decision)
        assertEquals("¿qué es un procesador?", captured.question)
        assertNull(gate.observe("qué es un procesador", 2_000L).question)
    }

    @Test fun acceptsTwoUtterancesWithinEightSeconds() {
        val gate = OfflineVoiceQuestionGate()
        assertEquals(OfflineVoiceDecision.WAITING, gate.observe("Jarvis", 1_000L).decision)
        assertEquals(OfflineVoiceDecision.QUESTION, gate.observe("cuéntame sobre la luna", 6_000L).decision)
        assertEquals(OfflineVoiceDecision.IGNORED, gate.observe("háblame de Marte", 7_000L).decision)
    }

    @Test fun expiryAndClockRollbackCannotReviveConsent() {
        val gate = OfflineVoiceQuestionGate()
        assertEquals(OfflineVoiceDecision.WAITING, gate.observe("Jarvis", 1_000L).decision)
        assertEquals(OfflineVoiceDecision.IGNORED, gate.observe("qué hora es", 9_000L).decision)
        assertEquals(OfflineVoiceDecision.IGNORED, gate.observe("qué hora es", 500L).decision)
        assertEquals(OfflineVoiceDecision.QUESTION, gate.observe("Jarvis explica el sol", 9_100L).decision)
    }

    @Test fun stopIsNeverTreatedAsModelPrompt() {
        val gate = OfflineVoiceQuestionGate()
        assertEquals(OfflineVoiceDecision.STOP, gate.observe("Jarvis, detente", 0L).decision)
        assertEquals(OfflineVoiceDecision.WAITING, gate.observe("Jarvis", 1_000L).decision)
        assertEquals(OfflineVoiceDecision.STOP, gate.observe("para", 2_000L).decision)
        assertEquals(OfflineVoiceDecision.IGNORED, gate.observe("qué es...", 3_000L).decision)
    }

    @Test fun refusesNegationsAndDeviceCommandsRatherThanRunTools() {
        val phrases = listOf(
            "no Jarvis dime la hora", "Jarvis no pares", "Jarvis abre ajustes",
            "Jarvis instala un apk", "Jarvis activa Shizuku",
            "Jarvis borra archivos", "Jarvis dime algo y ejecuta shell",
            "Jarvis desactiva x4", "Jarvis fusiona los PR",
            "Jarvis para y elimina todo"
        )
        for (phrase in phrases) assertEquals(
            "Unsafe input: $phrase",
            OfflineVoiceDecision.IGNORED,
            OfflineVoiceQuestionGate().observe(phrase, 10L).decision
        )
    }

    @Test fun rejectsOutOfBoundsOrNonFiniteTime() {
        val gate = OfflineVoiceQuestionGate()
        assertEquals(OfflineVoiceDecision.IGNORED, gate.observe("Jarvis", -1L).decision)
        assertEquals(OfflineVoiceDecision.IGNORED, gate.observe("Jarvis " + "hola".repeat(300), 1L).decision)
        assertEquals(OfflineVoiceDecision.IGNORED, gate.observe("Jarvis \u0001hola", 2L).decision)
    }

    @Test fun generatedAnswerIsShortPlainTextWithNoExecutableAuthority() {
        assertEquals("Los planetas giran alrededor del Sol.", OfflineVoiceAnswerPolicy.forTts(
            "Los planetas giran alrededor del Sol."
        ))
        assertNull(OfflineVoiceAnswerPolicy.forTts("Modelo GGUF no instalado, incompatible o alterado."))
        assertNull(OfflineVoiceAnswerPolicy.forTts("\u0000\u0003"))
        assertTrue(OfflineVoiceAnswerPolicy.forTts("palabra ".repeat(180))!!.length <= 420)
        assertFalse(OfflineVoiceAnswerPolicy.mayExecuteDeviceActions)
    }
}
