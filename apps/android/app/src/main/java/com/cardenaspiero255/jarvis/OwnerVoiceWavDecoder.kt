package com.cardenaspiero255.jarvis

import com.cardenaspiero255.jarvis.core.OwnerVoiceCapturePolicy
import java.io.InputStream
import java.security.MessageDigest

/** Pure, bounded PCM16 WAV import. Never stores PCM, URI or speaker data. */
data class DecodedVoiceWav(val samples: ShortArray, val sha256: String)

object OwnerVoiceWavDecoder {
    private const val MAX_BYTES = 500_000
    private const val MIN_SAMPLES = 24_000
    private const val MAX_SAMPLES = 160_000

    private fun u16(raw: ByteArray, at: Int): Int =
        (raw[at].toInt() and 255) or ((raw[at + 1].toInt() and 255) shl 8)

    private fun u32(raw: ByteArray, at: Int): Long =
        (0..3).sumOf { i -> (raw[at + i].toLong() and 255L) shl (8 * i) }

    private fun label(raw: ByteArray, at: Int, value: String): Boolean =
        value.indices.all { raw[at + it].toInt() == value[it].code }

    /**
     * Reads the selected ContentResolver InputStream only once, with an exact
     * memory bound and cancellation. WAV is native PCM16, never re-encoded.
     * Throws on untrusted headers, altered sizes, duplicates (at caller),
     * short, silent, or unusable audio.
     */
    fun decode(input: InputStream, isActive: () -> Boolean = { true }): DecodedVoiceWav {
        val bytes = ByteArray(MAX_BYTES + 1)
        var used = 0
        try {
            input.use { stream ->
                while (true) {
                    check(isActive() && !Thread.currentThread().isInterrupted) {
                        "Importación cancelada"
                    }
                    if (used == bytes.size) {
                        require(stream.read() == -1) { "WAV excede 500 KB" }
                        break
                    }
                    val count = stream.read(bytes, used, bytes.size - used)
                    if (count == -1) break
                    require(count > 0) { "WAV sin progreso" }
                    used += count
                    require(used <= MAX_BYTES) { "WAV excede 500 KB" }
                }
            }
            require(used >= 44) { "WAV incompleto" }
            val raw = bytes.copyOf(used)
            try {
                require(label(raw, 0, "RIFF") && label(raw, 8, "WAVE") &&
                        u32(raw, 4) == (used - 8).toLong()) {
                    "Cabecera RIFF/WAVE inválida"
                }
                var cursor = 12
                var fmtFound = false
                var pcmDataAt = -1
                var pcmBytes = 0
                while (cursor < raw.size) {
                    require(cursor + 8 <= raw.size) { "WAV con bloque incompleto" }
                    val size = u32(raw, cursor + 4)
                    val payloadAt = cursor + 8
                    val end = payloadAt.toLong() + size
                    require(size <= MAX_BYTES && end <= raw.size.toLong()) {
                        "WAV con longitud de bloque inválida"
                    }
                    if (label(raw, cursor, "fmt ")) {
                        require(!fmtFound && size >= 16L && size <= 64L) {
                            "Formato WAV duplicado o inválido"
                        }
                        require(u16(raw, payloadAt) == 1 &&
                                u16(raw, payloadAt + 2) == 1 &&
                                u32(raw, payloadAt + 4) == 16_000L &&
                                u32(raw, payloadAt + 8) == 32_000L &&
                                u16(raw, payloadAt + 12) == 2 &&
                                u16(raw, payloadAt + 14) == 16) {
                            "Se requiere mono PCM16 a 16 kHz"
                        }
                        fmtFound = true
                    } else if (label(raw, cursor, "data")) {
                        require(pcmDataAt < 0 && size % 2L == 0L) {
                            "Datos WAV duplicados o desalineados"
                        }
                        pcmDataAt = payloadAt
                        pcmBytes = size.toInt()
                    }
                    cursor = (end + (size and 1L)).toInt()
                    require(cursor <= raw.size) { "WAV con padding inválido" }
                }
                require(fmtFound && pcmDataAt >= 0 &&
                        pcmBytes / 2 in MIN_SAMPLES..MAX_SAMPLES) {
                    "WAV no contiene audio de 1,5–10 segundos"
                }
                val digest = MessageDigest.getInstance("SHA-256").digest(raw)
                    .joinToString("") { "%02x".format(it) }
                val pcm = ShortArray(pcmBytes / 2) { i ->
                    val offset = pcmDataAt + i * 2
                    (u16(raw, offset)).toShort()
                }
                if (!OwnerVoiceCapturePolicy().validSample(pcm)) {
                    pcm.fill(0)
                    throw IllegalArgumentException("Audio silencioso o insuficiente")
                }
                if (!isActive() || Thread.currentThread().isInterrupted) {
                    pcm.fill(0)
                    throw IllegalStateException("Importación cancelada")
                }
                return DecodedVoiceWav(pcm, digest)
            } finally {
                raw.fill(0)
            }
        } finally {
            bytes.fill(0)
        }
    }
}
