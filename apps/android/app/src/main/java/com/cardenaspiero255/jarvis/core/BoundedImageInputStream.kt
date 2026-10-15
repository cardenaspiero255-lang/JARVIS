package com.cardenaspiero255.jarvis.core

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Counts every byte consumed from an untrusted image provider, including skips
 * and rereads after reset. A decoder cannot bypass the per-pass 16 MiB cap.
 *
 * A read exactly at the cap fails closed if the decoder asks for more data,
 * even to discover EOF. Each image-decoding pass opens a fresh capped stream.
 */
internal class BoundedImageInputStream(
    source: InputStream,
    private val maxBytes: Long = MAX_INPUT_BYTES
) : FilterInputStream(source) {
    private var consumed = 0L

    init {
        require(maxBytes in 1..MAX_INPUT_BYTES) { "Invalid bounded image size" }
    }

    override fun read(): Int {
        ensureBudget()
        val value = super.read()
        if (value >= 0) consumed++
        return value
    }

    override fun read(buffer: ByteArray, off: Int, len: Int): Int {
        if (off < 0 || len < 0 || off > buffer.size - len) throw IndexOutOfBoundsException()
        if (len == 0) return 0
        ensureBudget()
        val allowed = minOf(len.toLong(), maxBytes - consumed).toInt()
        val actual = super.read(buffer, off, allowed)
        if (actual > allowed || actual < -1) throw IOException("invalid image provider read")
        if (actual > 0) consumed += actual
        return actual
    }

    override fun skip(n: Long): Long {
        if (n <= 0L) return 0L
        ensureBudget()
        val allowed = minOf(n, maxBytes - consumed)
        val actual = super.skip(allowed)
        if (actual < 0L || actual > allowed) throw IOException("invalid image provider skip")
        consumed += actual
        return actual
    }

    // FilterInputStream delegates mark/reset to the source. Intentionally do
    // not restore consumed on reset: rereading costs budget as well.
    private fun ensureBudget() {
        if (consumed >= maxBytes) throw IOException("image input exceeds byte limit")
    }

    companion object {
        const val MAX_INPUT_BYTES = 16L * 1024L * 1024L
    }
}
