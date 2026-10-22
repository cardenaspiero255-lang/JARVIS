package com.cardenaspiero255.jarvis.core

/**
 * A queued wake can run after STOP but before Android calls onDestroy().
 * Ending the session is an independent, synchronous invalidation condition.
 */
object NativeWakeDeliveryGate {
    fun allow(
        running: Boolean,
        closed: Boolean,
        endedNormally: Boolean,
        nativeModeRequested: Boolean,
        sessionActive: Boolean,
        consumed: Boolean,
        extendedListening: Boolean
    ): Boolean = running && !closed && !endedNormally && nativeModeRequested &&
        sessionActive && (extendedListening || !consumed)
}
