package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

/** Hardware capture path must remain testable when Android offline ASR is unavailable. */
class AudioInputMeterTest {
    @Test fun silenceIsNotMisreportedAsBrokenMic() {
        val meter = AudioInputMeter()
        meter.observe(ShortArray(160), 160)
        val reading = meter.reading()
        assertEquals(160L, reading.samples)
        assertEquals(0, reading.peak)
        assertEquals(AudioSignalCategory.SILENCE_OR_MUTED, reading.category)
    }

    @Test fun actualSampleEnergyIsMeasuredWithoutKeepingWaveform() {
        val meter = AudioInputMeter()
        meter.observe(shortArrayOf(0, 1200, -1200, 500, -500), 5)
        val reading = meter.reading()
        assertEquals(AudioSignalCategory.SIGNAL_PRESENT, reading.category)
        assertEquals(1200, reading.peak)
        assertTrue(reading.rms > 500)
        assertEquals(5L, reading.samples)
    }

    @Test fun distinguishesCaptureFailureFromSilence() {
        assertEquals(AudioSignalCategory.NO_SAMPLES, AudioInputMeter().reading().category)
        val meter = AudioInputMeter()
        meter.observe(shortArrayOf(100, 100, 100), 0)
        assertEquals(AudioSignalCategory.NO_SAMPLES, meter.reading().category)
    }

    @Test fun negativeFullScaleCannotOverflowPeakOrSquares() {
        val meter = AudioInputMeter()
        meter.observe(shortArrayOf(Short.MIN_VALUE, Short.MAX_VALUE), 2)
        val reading = meter.reading()
        assertEquals(32768, reading.peak)
        assertTrue(reading.rms > 32000)
    }

    @Test fun cannotReadBeyondValidSamplesOrPersistRawAudio() {
        val meter = AudioInputMeter()
        meter.observe(shortArrayOf(400, 800, 9000), 2)
        assertEquals(2L, meter.reading().samples)
        assertEquals(800, meter.reading().peak)
        assertTrue(meter.reading().diagnostic().contains("Muestra capturada"))
        assertFalse(meter.reading().diagnostic().contains("9000"))
    }
}
