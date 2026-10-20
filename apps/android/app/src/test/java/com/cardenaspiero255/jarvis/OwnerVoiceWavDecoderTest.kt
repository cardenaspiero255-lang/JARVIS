package com.cardenaspiero255.jarvis

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.sin

class OwnerVoiceWavDecoderTest {
    private fun ByteArrayOutputStream.le(value: Int, bytes: Int) {
        repeat(bytes) { write((value ushr (8 * it)) and 255) }
    }
    private fun wav(samples: Int = 40000, channels: Int = 1, rate: Int = 16000,
                    bits: Int = 16, amplitude: Int = 1800): ByteArray {
        val audio = ByteArrayOutputStream()
        for (i in 0 until samples) {
            val sample = (amplitude * sin(i * 2 * Math.PI * 220.0 / 16000.0)).toInt()
            audio.le(sample, 2)
        }
        val output = ByteArrayOutputStream()
        output.write("RIFF".toByteArray())
        output.le(audio.size() + 36, 4)
        output.write("WAVEfmt ".toByteArray())
        output.le(16, 4)
        output.le(1, 2)
        output.le(channels, 2)
        output.le(rate, 4)
        output.le(rate * channels * bits / 8, 4)
        output.le(channels * bits / 8, 2)
        output.le(bits, 2)
        output.write("data".toByteArray())
        output.le(audio.size(), 4)
        output.write(audio.toByteArray())
        return output.toByteArray()
    }

    @Test fun decodesPcm16MonoAt16kWithoutTranscoding() {
        val original = wav()
        val decoded = OwnerVoiceWavDecoder.decode(ByteArrayInputStream(original))
        assertEquals(40000, decoded.samples.size)
        assertTrue(decoded.samples.any { kotlin.math.abs(it.toInt()) > 700 })
        assertEquals(64, decoded.sha256.length)
        decoded.samples.fill(0)
    }

    @Test fun distinctWaveFilesProduceDistinctSha() {
        val first = OwnerVoiceWavDecoder.decode(ByteArrayInputStream(wav()))
        val second = OwnerVoiceWavDecoder.decode(ByteArrayInputStream(wav(amplitude = 2500)))
        assertNotEquals(first.sha256, second.sha256)
        first.samples.fill(0)
        second.samples.fill(0)
    }

    @Test fun rejectsInvalidRateChannelsBitsAndShortSamples() {
        for (raw in listOf(wav(rate = 8000), wav(channels = 2),
                wav(bits = 8), wav(samples = 1000))) {
            try {
                OwnerVoiceWavDecoder.decode(ByteArrayInputStream(raw))
                fail("Invalid WAV was accepted")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun rejectsTruncatedAndOversizedInputs() {
        try {
            OwnerVoiceWavDecoder.decode(ByteArrayInputStream(wav().copyOfRange(0, 18000)))
            fail("Truncated WAV accepted")
        } catch (_: IllegalArgumentException) {}
        try {
            OwnerVoiceWavDecoder.decode(ByteArrayInputStream(ByteArray(500001)))
            fail("Oversized WAV accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun rejectsCancellationAndSilence() {
        try {
            OwnerVoiceWavDecoder.decode(ByteArrayInputStream(wav(amplitude = 0)))
            fail("Silence accepted")
        } catch (_: IllegalArgumentException) {}
        try {
            OwnerVoiceWavDecoder.decode(ByteArrayInputStream(wav()), isActive = { false })
            fail("Revoked import accepted")
        } catch (_: IllegalStateException) {}
    }

    @Test fun ignoresNonAudioChunksButRejectsMalformedChunkSize() {
        val original = wav()
        val list = ByteArrayOutputStream()
        list.write("LIST".toByteArray())
        list.le(3,4)
        list.write(byteArrayOf(42, 43, 44, 0))
        val payload = original.copyOfRange(0, 36) + list.toByteArray() +
            original.copyOfRange(36, original.size)
        val withChunk = payload.copyOf().apply {
            val riffSize = size - 8
            for (i in 0 until 4) this[4+i] = (riffSize ushr (i*8)).toByte()
        }
        val decoded = OwnerVoiceWavDecoder.decode(ByteArrayInputStream(withChunk))
        assertEquals(40000, decoded.samples.size)
        decoded.samples.fill(0)
        val broken = original.copyOf()
        broken[40] = 0xff.toByte()
        broken[41] = 0xff.toByte()
        broken[42] = 0xff.toByte()
        broken[43] = 0x7f.toByte()
        try {
            OwnerVoiceWavDecoder.decode(ByteArrayInputStream(broken))
            fail("Malicious chunk size accepted")
        } catch (_: IllegalArgumentException) {}
    }
}
