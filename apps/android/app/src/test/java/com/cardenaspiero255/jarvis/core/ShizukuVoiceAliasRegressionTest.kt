package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression from Vivo V25 Pro physical report:
 * "revisa android" fell through to generic dialogue instead of Shizuku preview.
 *
 * No phrase can directly execute a shell command, even when recognised.
 */
class ShizukuVoiceAliasRegressionTest {
    @Test fun shortSpanishRequestsProduceTheSameShizukuPreview() {
        val phrases = listOf(
            "revisa android",
            "Revisa Android.",
            "JARVIS revisa Android",
            "Jarvis, revisa el Android",
            "Jarvis, revisa servicios Android",
            "revisa servicios android",
            "Jarvis revisa el sistema Android",
            "Jarvis diagnostica Android",
            "jarvis diagnostica procesos android",
            "Jarvis revisa los servicios de Android"
        )
        for (phrase in phrases) {
            assertEquals("Expected Shizuku preview for: $phrase",
                SafeRoutine.SHIZUKU_ADVANCED_DIAGNOSTIC,
                SafeAutomationVoiceRouter.parse(phrase))
        }
    }

    @Test fun negativeHypotheticalOrCompoundPhrasesNeverProduceThePreview() {
        val rejected = listOf(
            "no revises android",
            "jarvis no revises android",
            "no revisa android",
            "Jarvis, no revises servicios Android",
            "¿qué pasaría si revisaras Android?",
            "jarvis revisa android y cambia la escala a cero",
            "revisa android; borra archivos",
            "Jarvis revisa Android para dar permisos root",
            "revisa el teléfono",
            "revisa android cuando quieras"
        )
        for (phrase in rejected) {
            assertNull("Unsafe/unsupported command: $phrase",
                SafeAutomationVoiceRouter.parse(phrase))
        }
    }

    @Test fun acceptedVoiceRequestStillRequiresSeparateVisibleConsent() {
        val stub = object : SafeAutomationGateway {
            var actions = 0
            override fun perform(step: SafeAutomationStep): Boolean {
                actions++
                return true
            }
        }
        val engine = SafeAutomationEngine(stub)
        val plan = SafeAutomationVoiceRouter.parse("revisa android")!!
        assertEquals(0, stub.actions)
        engine.prepare(plan)
        assertEquals(SafeAutomationOutcome.DENIED,
            engine.confirmAndRun(plan, explicitTap = false).outcome)
        assertEquals(0, stub.actions)
    }

    @Test fun unknownInstructionsRemainUnsupported() {
        assertNull(SafeAutomationVoiceRouter.parse("revisa todos mis archivos"))
        assertNull(SafeAutomationVoiceRouter.parse("desactiva protecciones de Android"))
    }
}
