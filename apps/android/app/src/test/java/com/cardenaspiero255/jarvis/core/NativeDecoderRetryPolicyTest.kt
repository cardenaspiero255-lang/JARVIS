package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/** A native decoder failure may trigger one and only one local cache re-extraction. */
class NativeDecoderRetryPolicyTest {
    @Test fun singleRetryOnNativeIoFailureWhenSessionIsActive() {
        val gate = NativeDecoderRetryPolicy()
        assertTrue(gate.shouldRebuild(IOException("model invalid"), isActive = true))
        assertFalse(gate.shouldRebuild(IOException("model still invalid"), isActive = true))
    }

    @Test fun noRetryWhenCancelledOrErrorUnrelatedToNativeDecoder() {
        val gate = NativeDecoderRetryPolicy()
        assertFalse(gate.shouldRebuild(IOException("model invalid"), isActive = false))
        assertFalse(gate.shouldRebuild(IllegalStateException("mic"), isActive = true))
        // An irrelevant error should not consume the single budget.
        assertTrue(gate.shouldRebuild(IOException("model invalid"), isActive = true))
    }

    @Test fun initialBudgetDoesNotAuthorizeExternalFallback() {
        val gate = NativeDecoderRetryPolicy()
        assertFalse(gate.shouldRebuild(SecurityException("microphone denied"), isActive = true))
        assertFalse(gate.shouldRebuild(UnsatisfiedLinkError("abi missing"), isActive = true))
    }
}
