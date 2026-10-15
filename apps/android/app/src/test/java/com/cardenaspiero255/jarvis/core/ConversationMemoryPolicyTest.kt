package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMemoryPolicyTest {
    @Test fun memoryCannotWriteOrReadUntilExplicitUserTap() {
        val gate = ConversationMemoryPolicy()
        assertFalse(gate.canReadOrWrite())
        assertFalse(gate.enable(explicitUserTap = false))
        assertFalse(gate.canReadOrWrite())
        assertTrue(gate.enable(explicitUserTap = true))
        assertTrue(gate.canReadOrWrite())
    }

    @Test fun stoppingOrRestartingTheAppRequiresNewConsent() {
        val gate = ConversationMemoryPolicy()
        gate.enable(explicitUserTap = true)
        gate.stop()
        assertFalse(gate.canReadOrWrite())
        assertFalse(ConversationMemoryPolicy().canReadOrWrite())
    }
}
