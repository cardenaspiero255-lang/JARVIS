package com.cardenaspiero255.jarvis.core

/**
 * Android AudioRecord.READ_BLOCKING may return zero without producing PCM.
 * Bound consecutive zero reads so a broken OEM capture does not hot-spin.
 * Positive reads reset the budget; negative reads belong to the fatal-error path.
 * Silence is delivered as actual PCM samples (> 0), not zero-length reads.
 */
class NativeAudioZeroReadGuard {
    private var consecutiveZeros = 0

    fun observe(readCount: Int): Boolean {
        require(readCount >= 0) { "Negative AudioRecord reads are fatal errors" }
        if (readCount > 0) {
            consecutiveZeros = 0
            return false
        }
        if (consecutiveZeros < MAX_CONSECUTIVE_ZERO_READS) consecutiveZeros++
        return consecutiveZeros >= MAX_CONSECUTIVE_ZERO_READS
    }

    companion object {
        const val MAX_CONSECUTIVE_ZERO_READS = 50
        const val BACKOFF_MS = 20L
    }
}
