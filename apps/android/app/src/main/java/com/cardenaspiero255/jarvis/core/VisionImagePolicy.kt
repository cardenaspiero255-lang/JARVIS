package com.cardenaspiero255.jarvis.core

/**
 * On-device OCR input boundary. An image is user-provided DATA, never an
 * instruction to another app, shell, assistant or GitHub provider.
 */
class VisionImagePolicy {
    val canControlOtherApps: Boolean get() = false

    fun acceptDimensions(width: Int, height: Int): Boolean =
        width in 1..12000 && height in 1..12000 &&
            width.toLong() * height.toLong() <= 24_000_000L

    /** Power-of-two bitmap sampling bounds decoded images to 2048 per axis. */
    fun sampleSize(width: Int, height: Int): Int {
        var size = 1
        val maxDimension = maxOf(width, height)
        while (maxDimension / size > 2048 && size < 64) size *= 2
        return size
    }

    fun renderPreview(raw: String): String {
        val normalized = raw.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return "No se detectó texto en la imagen."
        return normalized.take(320)
    }
}
