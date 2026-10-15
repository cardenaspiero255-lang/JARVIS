package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenReplyPolicyTest {
    private val policy = SpokenReplyPolicy()

    @Test fun requiresFreshExplicitTapAndInitializedEngine() {
        assertFalse(policy.maySpeak(SpokenMessage.LOCAL_STATUS, explicitTap = false,
            ready = true, stopped = false))
        assertFalse(policy.maySpeak(SpokenMessage.LOCAL_STATUS, explicitTap = true,
            ready = false, stopped = false))
        assertTrue(policy.maySpeak(SpokenMessage.LOCAL_STATUS, explicitTap = true,
            ready = true, stopped = false))
    }

    @Test fun emergencyStopRejectsSpeechEvenWhenInitialized() {
        assertFalse(policy.maySpeak(SpokenMessage.LOCAL_STATUS, explicitTap = true,
            ready = true, stopped = true))
    }

    @Test fun messagesAreFixedSpanishTemplatesNotArbitraryInput() {
        assertEquals("JARVIS está disponible en modo observador. Las funciones de control remoto están desactivadas.",
            policy.safeText(SpokenMessage.LOCAL_STATUS))
        assertEquals("La escucha continua está desactivada. Para hablar, pulsa el botón de voz.",
            policy.safeText(SpokenMessage.PRIVACY))
        assertTrue(policy.safeText(SpokenMessage.LOCAL_STATUS).length <= 160)
    }

    @Test fun defaultIsStoppedAndRequiresExplicitResume() {
        val state = SpeechSessionState()
        assertTrue(state.stopped)
        state.resume()
        assertFalse(state.stopped)
        state.stop()
        assertTrue(state.stopped)
        assertTrue(SpeechSessionState().stopped)
    }
}
