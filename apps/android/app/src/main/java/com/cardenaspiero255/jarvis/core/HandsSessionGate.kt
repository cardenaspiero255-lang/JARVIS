package com.cardenaspiero255.jarvis.core

/**
 * Ephemeral, default-deny execution lease. A process restart drops consent:
 * Android's persistent service grant is not by itself authorisation to act.
 */
class HandsSessionGate {
    @Volatile private var armed: Boolean = false

    fun isArmed(): Boolean = armed
    fun arm() { armed = true }
    fun stop() { armed = false }
}
