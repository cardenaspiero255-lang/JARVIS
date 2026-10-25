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

    @Test fun timeoutRejectsLateAudioCallbacksWithoutCancelingNewSpeech() {
        val tracker = SpeechUtteranceTracker()
        val timedOut = tracker.begin()!!
        assertTrue(tracker.timedOut(timedOut))
        val current = tracker.begin()!!
        assertFalse(tracker.started(timedOut))
        assertFalse(tracker.done(timedOut))
        assertFalse(tracker.failed(timedOut))
        assertTrue(tracker.started(current))
        assertTrue(tracker.done(current))
    }

    @Test fun uniqueTicketPerExplicitRequest() {
        val tracker = SpeechUtteranceTracker()
        val a = tracker.begin()!!
        val b = tracker.begin()!!
        assertFalse(a == b)
        assertFalse(tracker.started(a))
        assertTrue(tracker.started(b))
    }

    @Test fun tenSecondStartDeadlineCannotEndSpeechThatAlreadyStarted() {
        val tracker = SpeechUtteranceTracker()
        val ticket = tracker.begin()!!
        assertTrue(tracker.started(ticket))
        // Simulate the old 10-second task firing during a longer utterance.
        // No sleeps or Android engine: the phase, not elapsed time, is decisive.
        assertFalse(tracker.timedOut(ticket))
        assertFalse(tracker.started(ticket)) // duplicate onStart
        assertTrue(tracker.done(ticket))
        assertFalse(tracker.done(ticket)) // duplicate completion cannot resume ASR
        assertFalse(tracker.failed(ticket))
    }

    @Test fun missingCompletionHasItsOwnTerminalDeadline() {
        val tracker = SpeechUtteranceTracker()
        val ticket = tracker.begin()!!
        assertFalse(tracker.playbackTimedOut(ticket)) // not started yet
        assertTrue(tracker.started(ticket))
        assertTrue(tracker.playbackTimedOut(ticket))
        assertFalse(tracker.done(ticket))
        assertFalse(tracker.started(ticket))
        assertFalse(tracker.playbackTimedOut(ticket))
    }

    @Test fun stopDuringPlaybackRejectsEveryLateCallbackAndDeadline() {
        val tracker = SpeechUtteranceTracker()
        val old = tracker.begin()!!
        assertTrue(tracker.started(old))
        tracker.cancel() // STOP, before service destruction
        assertFalse(tracker.started(old))
        assertFalse(tracker.done(old))
        assertFalse(tracker.failed(old))
        assertFalse(tracker.timedOut(old))
        assertFalse(tracker.playbackTimedOut(old))
        val next = tracker.begin()!!
        assertFalse(tracker.playbackTimedOut(old))
        assertTrue(tracker.started(next))
        assertTrue(tracker.done(next))
    }

    @Test fun failureBeforeOrAfterStartIsConsumedOnlyOnce() {
        for (started in listOf(false, true)) {
            val tracker = SpeechUtteranceTracker()
            val ticket = tracker.begin()!!
            if (started) assertTrue(tracker.started(ticket))
            assertTrue(tracker.failed(ticket))
            assertFalse(tracker.failed(ticket))
            assertFalse(tracker.done(ticket))
            assertFalse(tracker.started(ticket))
            assertFalse(tracker.playbackTimedOut(ticket))
        }
    }
}
