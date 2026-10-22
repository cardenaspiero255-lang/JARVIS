package com.cardenaspiero255.jarvis.core

/** Eight JNI counters, no personal content. Only complete, plausible measurements
 * become receipts. Timing is native CPU-model work, not Vosk or audible TTS.
 */
data class GgufNativePerformance(
    val totalMs: Long,
    val loadMs: Long,
    val tokenizeMs: Long,
    val prefillMs: Long,
    val decodeMs: Long,
    val decodedTokens: Long,
    val cpuThreads: Long,
    val warmCacheHit: Boolean
) {
    companion object {
        fun fromCounters(raw: LongArray): GgufNativePerformance? {
            if (raw.size != 8 || raw.any { it < 0L } ||
                raw[0] <= 0L || raw[5] > 96L ||
                raw[6] !in 1L..8L || raw[7] !in 0L..1L) return null
            return GgufNativePerformance(raw[0], raw[1], raw[2], raw[3],
                raw[4], raw[5], raw[6], raw[7] == 1L)
        }
    }
}
