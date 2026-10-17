package com.cardenaspiero255.jarvis.core

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Bounded aggregate acoustic signal diagnostics. PCM samples are consumed
 * immediately; no waveform, transcript, speakerprint or audio file is stored.
 */
enum class AudioSignalCategory { NO_SAMPLES, SILENCE_OR_MUTED, SIGNAL_PRESENT }

data class AudioSignalReading(
    val samples: Long,
    val peak: Int,
    val rms: Int,
    val category: AudioSignalCategory
) {
    fun diagnostic(): String = when (category) {
        AudioSignalCategory.NO_SAMPLES ->
            "MIC_NO_SAMPLES: no se recibieron muestras del micrófono."
        AudioSignalCategory.SILENCE_OR_MUTED ->
            "MIC_CAPTURED_SILENCE: Muestra capturada, señal muy baja o silenciada. " +
                "Comprueba volumen de voz, distancia e indicador de micrófono."
        AudioSignalCategory.SIGNAL_PRESENT ->
            "MIC_SIGNAL_PRESENT: Muestra capturada; micrófono recibe sonido. " +
                "Esto NO demuestra que el reconocimiento de voz funcione."
    } + " Pico $peak · RMS $rms · muestras $samples."
}

class AudioInputMeter {
    private var received = 0L
    private var sumSquared = 0.0
    private var maxAmplitude = 0

    fun observe(pcm: ShortArray, validSamples: Int) {
        val safeSize = validSamples.coerceIn(0, pcm.size)
        for (i in 0 until safeSize) {
            val sample = pcm[i].toInt()
            maxAmplitude = maxOf(maxAmplitude, abs(sample))
            sumSquared += sample.toDouble() * sample.toDouble()
            received++
        }
    }

    fun reading(): AudioSignalReading {
        val rms = if (received > 0L) sqrt(sumSquared / received).toInt() else 0
        val category = when {
            received == 0L -> AudioSignalCategory.NO_SAMPLES
            rms >= 150 || maxAmplitude >= 900 -> AudioSignalCategory.SIGNAL_PRESENT
            else -> AudioSignalCategory.SILENCE_OR_MUTED
        }
        return AudioSignalReading(received, maxAmplitude, rms, category)
    }
}
