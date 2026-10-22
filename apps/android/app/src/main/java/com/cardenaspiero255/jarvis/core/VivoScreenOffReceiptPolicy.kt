package com.cardenaspiero255.jarvis.core

/** Increment only positively delivered, screen-off AudioRecord sample counts.
 * No samples, transcripts or microphone identities are stored.
 */
object VivoScreenOffReceiptPolicy {
    fun totalAfterProgress(existing: Long, samples: Long): Long =
        existing.coerceAtLeast(0L).let { now ->
            if (samples <= 0L) now else
                if (Long.MAX_VALUE - now < samples) Long.MAX_VALUE else now + samples
        }
}
