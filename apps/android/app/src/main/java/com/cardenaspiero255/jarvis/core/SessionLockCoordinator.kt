package com.cardenaspiero255.jarvis.core

/** Session-only privacy lock. No re-arming, disk deletion or remote actions. */
enum class SessionLockChannel {
    VOICE_REQUEST,
    SPOKEN_REPLY,
    IMAGE_OCR,
    HANDS,
    PRIVATE_MEMORY,
    LOCAL_DIAGNOSTICS,
    SCREEN_CAPTURE,
    LOCAL_CHAT
}

data class SessionLockReport(val failed: Set<SessionLockChannel>) {
    val complete: Boolean get() = failed.isEmpty()
}

/**
 * Attempt every shutdown, even if one module fails.
 * Missing handlers and exceptions are explicitly reported as incomplete.
 */
class SessionLockCoordinator {
    fun lock(actions: Map<SessionLockChannel, () -> Unit>): SessionLockReport {
        val failures = mutableSetOf<SessionLockChannel>()
        for (channel in SessionLockChannel.values()) {
            val action = actions[channel]
            if (action == null) {
                failures.add(channel)
                continue
            }
            try {
                action()
            } catch (_: Exception) {
                failures.add(channel)
            }
        }
        return SessionLockReport(failures)
    }
}
