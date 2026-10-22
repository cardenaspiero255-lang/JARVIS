package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeAudioReadFailurePolicyTest {
    @Test fun negativeReadAfterPermissionRevocationNeverReturnsSuccess() {
        assertEquals("ASR_NATIVE_MIC_PERMISSION_DENIED_AT_READ",
            NativeAudioReadFailurePolicy.classify(-3, permissionGranted = false))
        assertEquals("ASR_NATIVE_MIC_PERMISSION_DENIED_AT_READ",
            NativeAudioReadFailurePolicy.classify(-6, permissionGranted = false))
    }

    @Test fun negativeReadWithPermissionStillPresentIsHardwareErrorNotRevocationProof() {
        assertEquals("ASR_NATIVE_AUDIO_READ_ERROR",
            NativeAudioReadFailurePolicy.classify(-3, permissionGranted = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun positiveFramesMayNotBeMisclassifiedAsReadErrors() {
        NativeAudioReadFailurePolicy.classify(2048, permissionGranted = false)
    }
}
