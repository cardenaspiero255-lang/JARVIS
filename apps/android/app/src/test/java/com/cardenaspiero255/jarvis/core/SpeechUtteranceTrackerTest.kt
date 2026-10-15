package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechUtteranceTrackerTest {
    @Test fun staleCallbacksAfterStopNeverReportProgress() {
        val tracker = SpeechUtteranceTracker()
        val first = tracker.begin()!!
        assertTrue(tracker.started(first))
        tracker.cancel()
        assertFalse(tracker.done(first))
        assertFalse(tracker.started(first))
        val second = tracker.begin()!!
        assertFalse(tracker.failed(first))
        assertTrue(tracker.started(second))
        assertTrue(tracker.done(second))
        assertFalse(tracker.done(second))
    }

    @Test fun queuedUtteranceCanTimeoutWithoutClaimingSound() {
        val tracker = SpeechUtteranceTracker()
        val ticket = tracker.begin()!!
        assertTrue(tracker.timedOut(ticket))
        assertFalse(tracker.started(ticket))
        val next = tracker.begin()!!
        assertTrue(tracker.started(next))
        assertFalse(tracker.timedOut(next))
        assertTrue(tracker.failed(next))
    }

    @Test fun uniqueTicketPerExplicitRequest() {
        val tracker = SpeechUtteranceTracker()
        val a = tracker.begin()!!
        val b = tracker.begin()!!
        assertFalse(a == b)
        assertFalse(tracker.started(a))
        assertTrue(tracker.started(b))
    }
}
