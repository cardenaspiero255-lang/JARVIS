package com.cardenaspiero255.jarvis.core

/**
 * Minimal lockscreen listening evidence. Counts accepted microphone samples,
 * never stores PCM, embeddings, words, microphone identity or transcripts.
 *
 * Observations are snapshots: Android may later interrupt the microphone.
 * Two distinct screen-off progress callbacks and >=1s PCM are required.
 */
enum class LockScreenAudioState {
    NO_SCREEN_OFF_AUDIO, INSUFFICIENT_EVIDENCE, SCREEN_OFF_AUDIO_OBSERVED
}

data class LockScreenAudioSnapshot(
    val state: LockScreenAudioState,
    val screenOffSamples: Long,
    val screenOffObservations: Int,
    val lastScreenOffElapsedMs: Long?
)

class LockScreenAudioEvidence {
    private var previousElapsedMs = -1L
    private var samplesOff = 0L
    private var observationsOff = 0
    private var lastOff: Long? = null

    @Synchronized
    fun observe(nowElapsedMs: Long, pcmSamples: Long, interactive: Boolean) {
        // Monotonic elapsedRealtime, not a wall clock. Never count duplicate data.
        if (nowElapsedMs < 0L || nowElapsedMs <= previousElapsedMs ||
            pcmSamples <= 0L || pcmSamples > 160_000L) return
        previousElapsedMs = nowElapsedMs
        if (!interactive) {
            samplesOff += pcmSamples
            observationsOff++
            lastOff = nowElapsedMs
        }
    }

    @Synchronized
    fun snapshot(): LockScreenAudioSnapshot {
        val state = when {
            observationsOff == 0 -> LockScreenAudioState.NO_SCREEN_OFF_AUDIO
            observationsOff >= 2 && samplesOff >= 16_000L ->
                LockScreenAudioState.SCREEN_OFF_AUDIO_OBSERVED
            else -> LockScreenAudioState.INSUFFICIENT_EVIDENCE
        }
        return LockScreenAudioSnapshot(state, samplesOff, observationsOff, lastOff)
    }

    @Synchronized
    fun reset() {
        previousElapsedMs = -1L
        samplesOff = 0L
        observationsOff = 0
        lastOff = null
    }
}
