package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameReadinessSpeechPolicyTest {
    private val policy = GameReadinessSpeechPolicy()

    private fun report(level: GameReadinessLevel) = GameReadinessReport(
        level, GamingPreference.BALANCED,
        listOf("DO NOT SPEAK arbitrary text from observations")
    )

    @Test fun readoutMapsTrustedGameLevelToShortSpokenRecommendation() {
        for (level in GameReadinessLevel.values()) {
            val spoken = policy.textForSpeech(report(level),
                explicitTap = true, ready = true, stopped = false)
            assertTrue(!spoken.isNullOrBlank())
            assertTrue(spoken!!.length <= 280)
            assertTrue(!spoken.contains("DO NOT SPEAK"))
        }
        assertTrue(policy.textForSpeech(report(GameReadinessLevel.POSTPONE),
            true, true, false)!!.contains("enfriar"))
    }

    @Test fun noAudioWithoutAnAuthorizedTapOrReadyDevice() {
        val ready = report(GameReadinessLevel.READY)
        assertNull(policy.textForSpeech(ready, false, true, false))
        assertNull(policy.textForSpeech(ready, true, false, false))
        assertNull(policy.textForSpeech(ready, true, true, true))
    }
}
