package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

class LocalGgufPolicyTest {
    @Test fun onlyWellFormedGgufMagicAndVersionAllowed() {
        val header = byteArrayOf(0x47, 0x47, 0x55, 0x46, 3, 0, 0, 0)
        assertTrue(LocalGgufPolicy.validHeader(header))
        header[0] = 0
        assertFalse(LocalGgufPolicy.validHeader(header))
        assertFalse(LocalGgufPolicy.validHeader(ByteArray(3)))
        assertFalse(LocalGgufPolicy.validHeader(byteArrayOf(0x47, 0x47, 0x55, 0x46, 1, 0, 0, 0)))
    }
    @Test fun importSizeAndPromptsAreStrictlyBounded() {
        assertTrue(LocalGgufPolicy.validLength(100_000_000L))
        assertFalse(LocalGgufPolicy.validLength(0L))
        assertFalse(LocalGgufPolicy.validLength(1_600_000_001L))
        assertTrue(LocalGgufPolicy.validPrompt("¿Qué es un procesador?"))
        assertTrue(LocalGgufPolicy.validPrompt("Explica un procesador"))
        assertFalse(LocalGgufPolicy.validPrompt("Abre ajustes y ejecuta un comando"))
        assertFalse(LocalGgufPolicy.validPrompt("x".repeat(1100)))
        assertFalse(LocalGgufPolicy.validPrompt("hola\u0000"))
    }
    @Test fun responsesAreDataNotPermissions() {
        assertFalse(LocalGgufPolicy.canIssueDeviceActions)
        assertEquals(96, LocalGgufPolicy.MAX_NEW_TOKENS)
        assertTrue(LocalGgufPolicy.MAX_CONTEXT_TOKENS <= 2048)
    }
}
