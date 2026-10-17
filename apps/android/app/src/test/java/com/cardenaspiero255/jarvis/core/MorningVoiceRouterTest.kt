package com.cardenaspiero255.jarvis.core

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

/** RED first: one explicit voice turn may request time and an external handoff. */
class MorningVoiceRouterTest {
    private val router = MorningVoiceRouter()

    @Test fun understandsCompoundSpanishRequestWithoutQwen() {
        assertEquals(
            MorningVoiceIntent.TIME_AND_GAMEHUB_REQUEST,
            router.parse("Jarvis dime la hora y dile a ChatGPT que siga trabajando en GameHub Ultra")
        )
        assertEquals(
            MorningVoiceIntent.TIME_AND_GAMEHUB_REQUEST,
            router.parse("Jarvis, dime la hora y dile a chat gpt que siga trabajando en gamehub ultra")
        )
    }

    @Test fun understandsTimeOnlyWithoutInternet() {
        assertEquals(MorningVoiceIntent.TIME_ONLY, router.parse("Jarvis, ¿qué hora es?"))
        assertEquals(MorningVoiceIntent.TIME_ONLY, router.parse("dime la hora"))
    }

    @Test fun acceptsAStandaloneGameHubHandoffAsRequestNotExecution() {
        assertEquals(
            MorningVoiceIntent.GAMEHUB_REQUEST,
            router.parse("Jarvis dile a ChatGPT que siga trabajando en GameHub Ultra")
        )
    }

    @Test fun rejectsNegationsHypotheticalsAndSideEffects() {
        for (text in listOf(
            "Jarvis no le digas a ChatGPT que siga trabajando en GameHub Ultra",
            "Jarvis dime la hora pero no se lo digas a ChatGPT",
            "Jarvis dime la hora y borra GameHub Ultra",
            "¿Qué pasaría si le dijera a ChatGPT que siga trabajando en GameHub Ultra?",
            "Jarvis dime la hora y dile a ChatGPT que fusione todo sin revisar",
            "Jarvis dile a ChatGPT que siga trabajando en Ultra Sentinel",
            "Jarvis dime la hora y dile a ChatGPT que siga trabajando en GameHub Ultra y borra repositorios"
        )) assertNull(text, router.parse(text))
    }

    @Test fun rejectsPartialMatchesAndOversizedPrompts() {
        assertNull(router.parse("GameHub Ultra"))
        assertNull(router.parse("Jarvis dime la hora de borrar"))
        assertNull(router.parse("Jarvis dime la hora" + " x".repeat(150)))
    }

    @Test fun saysActualLocalTimeFromInjectedClock() {
        val clock = Clock.fixed(
            Instant.parse("2026-10-17T11:30:00Z"), ZoneOffset.ofHours(-3)
        )
        assertEquals("Son las 8:30.", LocalDeviceClock.speak(clock))
    }

    @Test fun neverClaimsUnauthenticatedChatGPTRequestWasSent() {
        val reply = MorningVoiceResponse.forIntent(
            MorningVoiceIntent.TIME_AND_GAMEHUB_REQUEST,
            "Son las 8:30."
        )
        assertTrue(reply.contains("GameHub Ultra"))
        assertTrue(reply.contains("no la envié"))
        assertFalse(reply.contains("ya está trabajando"))
        assertFalse(reply.contains("hecho"))
    }
}
