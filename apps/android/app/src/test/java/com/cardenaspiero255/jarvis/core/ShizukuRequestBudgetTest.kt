package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

/** Deterministic deadline regressions: no Android device or sleeps required. */
class ShizukuRequestBudgetTest {
    @Test fun basicAndAdvancedRequestsHaveDifferentBudgets() {
        assertEquals(2, ShizukuRequestBudget.probeCount(false))
        assertEquals(7, ShizukuRequestBudget.probeCount(true))
        assertEquals(17_000L, ShizukuRequestBudget.timeoutMs(false))
        assertEquals(42_000L, ShizukuRequestBudget.timeoutMs(true))
        assertTrue(ShizukuRequestBudget.timeoutMs(true) >
            ShizukuRequestBudget.timeoutMs(false))
    }

    @Test fun aSlowButBoundedSevenProbeSequenceCanFinish() {
        val eachCommandMs = List(7) { 4_000L }
        val totalMs = 6_000L + eachCommandMs.sum()
        assertTrue(ShizukuRequestBudget.withinDeadline(true, totalMs))
        assertFalse(ShizukuRequestBudget.withinDeadline(false, totalMs))
    }

    @Test fun slowConnectionAndBinderOverheadRemainCovered() {
        val eachProbeAndIpcMs = List(7) { 5_000L }
        val totalMs = 7_000L + eachProbeAndIpcMs.sum()
        assertTrue(ShizukuRequestBudget.withinDeadline(true, totalMs))
        assertFalse(ShizukuRequestBudget.withinDeadline(true, totalMs + 1L))
    }

    @Test fun negativeAndOverflowedElapsedTimeFailClosed() {
        assertFalse(ShizukuRequestBudget.withinDeadline(true, -1L))
        assertFalse(ShizukuRequestBudget.withinDeadline(false, Long.MAX_VALUE))
    }

    @Test fun newProbeMustNotSilentlyOutgrowTimeBudget() {
        assertEquals(ShizukuFixedProbe.values().size,
            ShizukuRequestBudget.probeCount(true))
        assertEquals(ShizukuRequestBudget.probeCount(true) *
            ShizukuRequestBudget.PER_PROBE_ALLOWANCE_MS +
            ShizukuRequestBudget.STARTUP_ALLOWANCE_MS,
            ShizukuRequestBudget.timeoutMs(true))
    }
}
