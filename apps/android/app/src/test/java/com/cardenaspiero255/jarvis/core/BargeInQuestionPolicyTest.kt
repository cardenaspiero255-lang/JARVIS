package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class BargeInQuestionPolicyTest {
    private val policy = BargeInQuestionPolicy()
    private val response = "La energía solar permite a las plantas producir alimento."

    @Test fun acceptsOnlyCompletedWakePrefixedSafeQuestionWithLiveAec() {
        val result = policy.decide(
            "Jarvis, ¿qué distancia hay entre la Tierra y la Luna?",
            response, playbackActive = true,
            echoCancellationActive = true, finalUtterance = true
        )
        assertEquals(BargeInQuestionDecision.ASK, result.decision)
        assertEquals("¿qué distancia hay entre la Tierra y la Luna?", result.question)
    }

    @Test fun rejectsSelfEchoEvenIfSynthesizedResponseContainsWakeOrSimilarQuestion() {
        val ownVoice = listOf(
            "Jarvis, dime qué hora es ahora mismo.",
            "Puedes decir Jarvis para interrumpir la respuesta.",
            "La Luna está cerca de la Tierra y hablamos de su distancia."
        )
        assertEquals(BargeInQuestionDecision.IGNORE,
            policy.decide("Jarvis dime qué hora es ahora mismo", ownVoice[0], true, true, true).decision)
        assertEquals(BargeInQuestionDecision.IGNORE,
            policy.decide("Jarvis dime por qué me puedes interrumpir",
                ownVoice[1], true, true, true).decision)
        assertEquals(BargeInQuestionDecision.IGNORE,
            policy.decide("Jarvis la luna está cerca de la tierra",
                ownVoice[2], true, true, true).decision)
    }

    @Test fun failsClosedWithoutVerifiedAcousticGuardOrSession() {
        val heard = "Jarvis, ¿cómo funcionan los satélites artificiales?"
        for (playing in listOf(true, false))
            for (aec in listOf(true, false))
                for (final in listOf(true, false)) {
                    val actual = policy.decide(heard, response, playing, aec, final)
                    assertEquals(if (playing && aec && final)
                        BargeInQuestionDecision.ASK else BargeInQuestionDecision.IGNORE,
                        actual.decision)
                }
        assertEquals(BargeInQuestionDecision.IGNORE,
            policy.decide(heard, null, true, true, true).decision)
    }

    @Test fun neverRoutesStopOrPhoneControlsToGgufAsQuestions() {
        for (heard in listOf(
            "jarvis para", "jarvis detente", "jarvis borra los archivos",
            "jarvis abre los ajustes", "jarvis envía mensajes y luego fusiona PR",
            "¿qué hora es?", "jarvis", "stop", "jarvis para borrar todo"
        )) {
            val result = policy.decide(heard, response, true, true, true)
            assertEquals(heard, BargeInQuestionDecision.IGNORE, result.decision)
            assertNull(result.question)
        }
    }

    @Test fun rejectsPartialOverlongAndControlData() {
        for (heard in listOf("Jarvis dime \u0000la hora", "jarvis\nabre la puerta",
                              "jarvis " + "por qué ".repeat(80))) {
            assertEquals(BargeInQuestionDecision.IGNORE,
                policy.decide(heard, response, true, true, true).decision)
        }
        assertEquals(BargeInQuestionDecision.IGNORE,
            policy.decide("Jarvis dime qué hora es", response,
                true, true, false).decision)
    }
}
