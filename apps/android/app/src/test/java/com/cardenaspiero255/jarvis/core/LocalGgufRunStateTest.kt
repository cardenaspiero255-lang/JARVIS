package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocalGgufRunStateTest {
    @Test fun cancellationNeverReleasesTheSlotBeforeTheWorkerExits() {
        val state = LocalGgufRunState()
        assertTrue(state.tryBegin())
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val worker = Thread {
            entered.countDown()
            try {
                finish.await(4, TimeUnit.SECONDS)
            } finally {
                state.finish()
                finished.countDown()
            }
        }
        worker.start()
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            // UI cancellation must ONLY signal the running JNI work, not release
            // its slot before native teardown has really completed.
            assertFalse(state.tryBegin())
            assertTrue(state.isBusy())
        } finally {
            finish.countDown()
            worker.join(4_000L)
        }
        assertTrue(finished.await(2, TimeUnit.SECONDS))
        assertFalse(state.isBusy())
        assertTrue(state.tryBegin())
        state.finish()
    }

    @Test fun lateFinishCannotClearAClosedState() {
        val state = LocalGgufRunState()
        assertTrue(state.tryBegin())
        state.close()
        state.finish()
        assertFalse(state.tryBegin())
        assertFalse(state.isBusy())
    }
}
