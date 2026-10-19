package com.cardenaspiero255.jarvis.core

import kotlin.math.abs

/** Local enrollment is manual and bounded, never a hidden background hotword. */
class OwnerVoiceCapturePolicy {
    val requiredSamples: Int = 3
    val captureSamples: Int = 40_000 // 2.5 seconds at 16 kHz.

    fun canCapture(
        consented: Boolean,
        permissionGranted: Boolean,
        activityVisible: Boolean,
        busy: Boolean
    ): Boolean = consented && permissionGranted && activityVisible && !busy

    fun validSample(pcm: ShortArray): Boolean {
        if (pcm.size !in 24_000..160_000) return false
        // Silence/near-silence cannot be enrolled as a person's voice.
        var sum = 0.0
        var nonQuiet = 0
        for (sample in pcm) {
            val amplitude = abs(sample.toInt())
            sum += amplitude.toDouble() * amplitude.toDouble()
            if (amplitude >= 700) nonQuiet++
        }
        val rms = kotlin.math.sqrt(sum / pcm.size)
        return rms >= 350.0 && nonQuiet >= pcm.size / 8
    }
}
