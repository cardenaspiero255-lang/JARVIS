package com.cardenaspiero255.jarvis.core

/**
 * Fatal AudioRecord failures always win over an ordinary session timeout or
 * lack of wake words. Never flush a decoder after the capture source failed.
 * This guards the actual terminal callback, not only the raw error classifier.
 */
object NativeAudioExitPolicy {
    fun resolveFailure(readFailure: String?, fallbackFailure: String?): String? =
        readFailure ?: fallbackFailure

    fun mayDeliverRecognizedSpeech(readFailure: String?): Boolean = readFailure == null
}
