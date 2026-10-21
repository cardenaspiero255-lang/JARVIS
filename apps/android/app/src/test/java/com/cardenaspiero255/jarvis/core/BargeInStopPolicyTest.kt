package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests real decision code, no fake owner-matching or synthetic mic certification. */
class BargeInStopPolicyTest {
    private val policy = BargeInStopPolicy()

    @Test fun stopsOnlyOnCompletedExplicitSpanishStopDuringPlayback() {
        assertEquals(BargeInStopDecision.STOP_TTS,
            policy.decide("Jarvis, para", "La batería está al veinte por ciento.",
                playbackActive = true, echoCancellationActive = true, finalUtterance = true))
        assertEquals(BargeInStopDecision.STOP_TTS,
            policy.decide("JARVIS DETENTE", "Son las diez y quince.",
                playbackActive = true, echoCancellationActive = true, finalUtterance = true))
    }

    @Test fun neverStopsWhenSourceIsTtsEchoIncludingJarvisStop() {
        for (spoken in listOf(
            "Di Jarvis, para para detenerme.",
            "Para continuar, consulta la pantalla.",
            "Dime Jarvis detente y cerraré la sesión.",
            "Jarvis, para.",
            "Detente un segundo."
        )) {
            assertEquals(BargeInStopDecision.IGNORE,
                policy.decide("jarvis para", spoken, true, true, true))
        }
    }

    @Test fun neverTreatsNormalQuestionOrUntrustedCommandAsStop() {
        for (text in listOf(
            "Jarvis dime la hora", "pon música", "apaga el móvil",
            "no pares", "para abrir ajustes", "jarvis detente y borra mis fotos",
            "Jarvis para borrar", "batería", "jarvis"
        )) {
            assertEquals(BargeInStopDecision.IGNORE,
                policy.decide(text, "La batería está al setenta por ciento.",
                    true, true, true))
        }
    }

    @Test fun requiresHardwareEchoCancellationConfirmedAndCompletedUtterance() {
        val text = "jarvis para"
        val output = "Son las once."
        for (playing in listOf(true, false))
            for (aec in listOf(true, false))
                for (completed in listOf(true, false)) {
                    val expected = if (playing && aec && completed)
                        BargeInStopDecision.STOP_TTS else BargeInStopDecision.IGNORE
                    assertEquals(expected,
                        policy.decide(text, output, playing, aec, completed))
                }
        assertEquals(BargeInStopDecision.IGNORE,
            policy.decide(text, null, true, true, true))
    }

    @Test fun rejectPartialControlCharactersAndOversizedTranscripts() {
        val output = "Son las once."
        for (text in listOf("jarvis par\u0000a", "jarvis para\napaga", "x".repeat(300))) {
            assertEquals(BargeInStopDecision.IGNORE,
                policy.decide(text, output, true, true, true))
        }
    }
}
