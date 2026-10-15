package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameHubPreflightGateTest {
    @Test fun readyStageCanBeConsumedOnlyOnce() {
        val gate = GameHubPreflightGate()
        assertTrue(gate.stageByTap(GameReadinessLevel.READY, GamingPreference.BALANCED, 100L))
        assertTrue(gate.consumeByTap(GamingPreference.BALANCED, 101L))
        assertFalse(gate.consumeByTap(GamingPreference.BALANCED, 102L))
    }

    @Test fun cautionNeedsSeparateExplicitConfirmationToo() {
        val gate = GameHubPreflightGate()
        assertTrue(gate.stageByTap(GameReadinessLevel.CAUTION, GamingPreference.COMPETITIVE, 100L))
        assertTrue(gate.consumeByTap(GamingPreference.COMPETITIVE, 120L))
    }

    @Test fun severeAndUnknownHealthNeverStageFastLaunch() {
        val gate = GameHubPreflightGate()
        assertFalse(gate.stageByTap(GameReadinessLevel.POSTPONE, GamingPreference.BALANCED, 0L))
        assertFalse(gate.consumeByTap(GamingPreference.BALANCED, 1L))
        assertFalse(gate.stageByTap(GameReadinessLevel.UNKNOWN, GamingPreference.BALANCED, 2L))
        assertFalse(gate.consumeByTap(GamingPreference.BALANCED, 3L))
    }

    @Test fun expiryRollbackAndClockOverflowFailClosed() {
        val gate = GameHubPreflightGate()
        assertFalse(gate.stageByTap(GameReadinessLevel.READY, GamingPreference.BALANCED, -1L))
        assertFalse(gate.stageByTap(GameReadinessLevel.READY, GamingPreference.BALANCED, Long.MAX_VALUE))
        assertTrue(gate.stageByTap(GameReadinessLevel.READY, GamingPreference.BALANCED, 1_000L))
        assertFalse(gate.consumeByTap(GamingPreference.BALANCED, 999L))
        assertFalse(gate.consumeByTap(GamingPreference.BALANCED, 1_001L))
        assertTrue(gate.stageByTap(GameReadinessLevel.READY, GamingPreference.BALANCED, 2_000L))
        assertFalse(gate.consumeByTap(GamingPreference.BALANCED, 32_000L))
        assertFalse(gate.consumeByTap(GamingPreference.BALANCED, 32_001L))
    }

    @Test fun profileChangeInvalidatesExistingApproval() {
        val gate = GameHubPreflightGate()
        assertTrue(gate.stageByTap(GameReadinessLevel.READY, GamingPreference.BALANCED, 100L))
        assertFalse(gate.consumeByTap(GamingPreference.VISUAL_QUALITY, 101L))
        assertFalse(gate.consumeByTap(GamingPreference.BALANCED, 102L))
    }

    @Test fun lockAndBackgroundCancelPendingHandoff() {
        val gate = GameHubPreflightGate()
        assertTrue(gate.stageByTap(GameReadinessLevel.READY, GamingPreference.BALANCED, 100L))
        gate.cancel()
        assertFalse(gate.consumeByTap(GamingPreference.BALANCED, 101L))
    }

    @Test fun duplicatePreparationCannotExtendExpiry() {
        val gate = GameHubPreflightGate()
        assertTrue(gate.stageByTap(GameReadinessLevel.READY, GamingPreference.BALANCED, 100L))
        assertFalse(gate.stageByTap(GameReadinessLevel.CAUTION, GamingPreference.BALANCED, 20_000L))
        assertFalse(gate.consumeByTap(GamingPreference.BALANCED, 30_100L))
    }
}
