package com.cardenaspiero255.jarvis.core

/** A negative AudioRecord.read() is failure, never proof of an OEM-specific cause. */
object NativeAudioReadFailurePolicy {
    fun classify(readResult: Int, permissionGranted: Boolean): String {
        require(readResult < 0) { "Only negative AudioRecord error codes are failures" }
        return if (permissionGranted) "ASR_NATIVE_AUDIO_READ_ERROR"
            else "ASR_NATIVE_MIC_PERMISSION_DENIED_AT_READ"
    }
}
