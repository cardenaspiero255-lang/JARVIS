package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class LocalLlmPolicyTest {
    @Test fun pinOfficialModelAndHash() {
        assertEquals(64, LocalLlmPolicy.SHA256.length)
        assertEquals("74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
            LocalLlmPolicy.SHA256)
    }

    @Test fun rejectOversizedInputsAndNonGGUF() {
        assertFalse(LocalLlmPolicy.isEligibleLength(0))
        assertFalse(LocalLlmPolicy.isEligibleLength(LocalLlmPolicy.MAX_BYTES + 1))
        assertTrue(LocalLlmPolicy.isEligibleLength(491_000_000))
        assertFalse(LocalLlmPolicy.isValidPrompt(" ".repeat(4)))
        assertFalse(LocalLlmPolicy.isValidPrompt("x".repeat(281)))
        assertTrue(LocalLlmPolicy.isValidPrompt("Explica la gravedad"))
    }

    @Test fun keepsDeterministicCommandsAndArithmeticAheadOfGeneration() {
        assertFalse(LocalLlmPolicy.shouldUseGenerative("repite"))
        assertFalse(LocalLlmPolicy.shouldUseGenerative("estado"))
        assertFalse(LocalLlmPolicy.shouldUseGenerative("1 millón x 500"))
        assertFalse(LocalLlmPolicy.shouldUseGenerative("¿cuánto es 5 por 7?"))
        assertTrue(LocalLlmPolicy.shouldUseGenerative("Compara Red Magic 11S Pro y 12 Pro"))
    }

    @Test fun safeAnswerExcludesControlCharacters() {
        assertEquals("Hola mundo", LocalLlmPolicy.cleanAnswer("Hola\u0000 mundo"))
        assertNull(LocalLlmPolicy.cleanAnswer("\n\u0000"))
    }
}
