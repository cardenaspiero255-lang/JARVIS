package com.cardenaspiero255.jarvis.core

/**
 * Foreground-only eligibility for external Android recognizer results.
 * Never processes delayed callbacks while its owner Activity is stopped.
 */
class VoiceForegroundGate {
    @Volatile private var activityStarted: Boolean = false

    fun onStart() { activityStarted = true }
    fun onStop() { activityStarted = false }

    fun canAcceptRecognitionResult(): Boolean = activityStarted
}
