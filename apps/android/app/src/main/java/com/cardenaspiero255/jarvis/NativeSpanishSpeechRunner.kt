package com.cardenaspiero255.jarvis

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.cardenaspiero255.jarvis.core.AudioInputMeter
import com.cardenaspiero255.jarvis.core.HandsFreePreviewPolicy
import com.cardenaspiero255.jarvis.core.NativeDecoderRetryPolicy
import com.cardenaspiero255.jarvis.core.PreviewWakeTurnGate
import com.cardenaspiero255.jarvis.core.WakeTurnDecision
import com.cardenaspiero255.jarvis.core.PreviewWake
import com.cardenaspiero255.jarvis.core.SpeakerEvidence
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dedicated OFFLINE Spanish recognizer: AudioRecord -> Vosk. Does not use
 * Android SpeechRecognizer, Google services, Qwen, network, or external APIs.
 *
 * Preview only: 60 s and simple public clock request. No speaker verification:
 * the recognized hotword must never authorize sensitive actions.
 */
class NativeSpanishSpeechRunner(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onReady: () -> Unit,
    private val onWake: (PreviewWake) -> Unit,
    private val onFailure: (String) -> Unit
) {
    private val active = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    @Volatile private var recorder: AudioRecord? = null
    // Keep the voice policy unchanged; this preview authorizes ONLY public time.
    // ownerOnly = false until independently verified biometric speaker evidence exists.
    private val turnGate = PreviewWakeTurnGate()

    fun start(): Boolean {
        if (!active.compareAndSet(false, true)) return false
        val thread = Thread({ runRecognition() }, "jarvis-vosk-native-spanish")
        worker = thread
        thread.start()
        return true
    }

    fun stop() {
        active.set(false)
        worker?.interrupt()
        try { recorder?.stop() } catch (_: RuntimeException) {}
    }

    private fun runRecognition() {
        var microphone: AudioRecord? = null
        var model: Model? = null
        var recognizer: Recognizer? = null
        var result: PreviewWake? = null
        var failure: String? = null
        val levels = AudioInputMeter()
        var hadUnmatchedSpeech = false
        try {
            onStatus("ASR_NATIVE_LOADING: cargando modelo español dentro de la APK…")
            val installer = NativeSpanishModelStore(context)
            val modelReady: () -> Boolean = {
                active.get() && !Thread.currentThread().isInterrupted
            }
            val folder = installer.ensureInstalled(
                onStage = { stage -> onStatus(stage) }, isActive = modelReady)
            if (!modelReady()) return
            onStatus("ASR_NATIVE_DECODER_LOADING")
            val retry = NativeDecoderRetryPolicy()
            try {
                model = Model(folder.absolutePath)
                recognizer = Recognizer(model, 16_000.0f)
            } catch (initial: IOException) {
                if (!retry.shouldRebuild(initial, modelReady())) throw initial
                // A cache receipt only certifies initial extraction. The native
                // decoder can still fail due to damaged private files or I/O.
                // Repair exactly once using the bundled, pinned archive.
                onStatus("ASR_NATIVE_DECODER_REBUILD")
                try { recognizer?.close() } catch (_: RuntimeException) {}
                recognizer = null
                try { model?.close() } catch (_: RuntimeException) {}
                model = null
                val fresh = installer.ensureInstalled(
                    onStage = { stage -> onStatus(stage) },
                    forceRefresh = true, isActive = modelReady)
                if (!modelReady()) return
                onStatus("ASR_NATIVE_DECODER_RETRY")
                model = Model(fresh.absolutePath)
                recognizer = Recognizer(model, 16_000.0f)
            }
            if (!modelReady()) return
            onStatus("ASR_NATIVE_AUDIO_OPEN")

            val minimum = AudioRecord.getMinBufferSize(
                16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            check(minimum > 0) { "Invalid PCM buffer size" }
            microphone = AudioRecord(
                MediaRecorder.AudioSource.MIC, 16_000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimum * 2, 4096)
            )
            recorder = microphone
            check(microphone.state == AudioRecord.STATE_INITIALIZED) {
                "AudioRecord not initialized"
            }
            microphone.startRecording()
            check(microphone.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "AudioRecord not capturing"
            }

            onReady()
            onStatus("ASR_NATIVE_READY: micrófono y motor Vosk español activos 60 s.")
            val audio = ShortArray(2048)
            val start = SystemClock.elapsedRealtime()
            var lastStatus = start
            while (active.get() && !Thread.currentThread().isInterrupted &&
                SystemClock.elapsedRealtime() - start < 60_000L) {
                val received = microphone.read(
                    audio, 0, audio.size, AudioRecord.READ_BLOCKING
                )
                when {
                    received > 0 -> {
                        levels.observe(audio, received)
                        if (recognizer.acceptWaveForm(audio, received)) {
                            val text = JSONObject(recognizer.result).optString("text", "")
                            if (text.isNotBlank()) {
                                // Vosk emits completed segments at speech pauses.
                                // Support "Jarvis" then "dime la hora" within 8 s,
                                // but never treat wake or ASR as speaker identity.
                                val decision = turnGate.observe(
                                    text, SystemClock.elapsedRealtime(), SpeakerEvidence.UNAVAILABLE)
                                when (decision) {
                                    WakeTurnDecision.STOP -> {
                                        result = PreviewWake.STOP
                                        break
                                    }
                                    WakeTurnDecision.LOCAL_TIME -> {
                                        result = PreviewWake.LOCAL_TIME
                                        break
                                    }
                                    WakeTurnDecision.WAITING -> onStatus(
                                        "ASR_NATIVE_WAKE_WAITING: Jarvis detectado. " +
                                            "Di «dime la hora» en 8 s.")
                                    WakeTurnDecision.BLOCKED_IDENTITY -> {
                                        failure = "ASR_NATIVE_IDENTITY_BLOCKED: voz no confiable."
                                        break
                                    }
                                    WakeTurnDecision.IGNORED -> hadUnmatchedSpeech = true
                                }
                            }
                        }
                    }
                    received < 0 -> throw IllegalStateException(
                        "AudioRecord returned code $received")
                }
                val now = SystemClock.elapsedRealtime()
                if (turnGate.expiredAt(now)) {
                    result = PreviewWake.WAKE_ONLY
                    break
                }
                if (now - lastStatus >= 10_000L) {
                    val reading = levels.reading()
                    onStatus("ASR_NATIVE_LISTENING: " +
                        "RMS ${reading.rms}; muestras ${reading.samples}. " +
                        "Escuchando «Jarvis, dime la hora».")
                    lastStatus = now
                }
            }
            if (result == null && active.get()) {
                val finalText = JSONObject(recognizer.finalResult).optString("text", "")
                // Flush last segment without granting extra privileges.
                // Pending standalone wake can still be acknowledged.
                val finalDecision = if (finalText.isNotBlank()) turnGate.observe(
                    finalText, SystemClock.elapsedRealtime(), SpeakerEvidence.UNAVAILABLE)
                    else WakeTurnDecision.IGNORED
                if (finalDecision == WakeTurnDecision.STOP)
                    result = PreviewWake.STOP
                else if (finalDecision == WakeTurnDecision.LOCAL_TIME)
                    result = PreviewWake.LOCAL_TIME
                else if (finalDecision == WakeTurnDecision.WAITING || turnGate.waiting())
                    result = PreviewWake.WAKE_ONLY
                else if (finalDecision == WakeTurnDecision.BLOCKED_IDENTITY)
                    failure = "ASR_NATIVE_IDENTITY_BLOCKED: voz no confiable."
            }
            if (result == null && active.get()) {
                val signal = levels.reading()
                failure = if (signal.samples == 0L) {
                    "ASR_NATIVE_NO_MIC_SAMPLES: Android no entregó audio al motor Vosk."
                } else if (hadUnmatchedSpeech) {
                    "ASR_NATIVE_SPEECH_NOT_WAKE: Vosk recibió palabras, pero " +
                        "no reconoció la frase de activación. " +
                        "Prueba cerca del micrófono, con poco ruido."
                } else if (signal.peak < 900) {
                    "ASR_NATIVE_QUIET_AUDIO: micrófono sin suficiente señal de voz."
                } else {
                    "ASR_NATIVE_NO_WAKE: el micrófono captó sonido, pero el " +
                        "modelo español no identificó «Jarvis, dime la hora»."
                }
            }
        } catch (_: SecurityException) {
            failure = "ASR_NATIVE_MIC_PERMISSION: Android denegó acceso al micrófono."
        } catch (e: Exception) {
            if (active.get()) failure = "ASR_NATIVE_ERROR: ${e.javaClass.simpleName}. " +
                "Comprueba el almacenamiento y compatibilidad ARM64."
        } catch (_: UnsatisfiedLinkError) {
            if (active.get()) failure = "ASR_NATIVE_ABI_UNAVAILABLE: " +
                "la biblioteca Vosk no funciona con el procesador de este dispositivo."
        } finally {
            active.set(false)
            recorder = null
            try { microphone?.stop() } catch (_: RuntimeException) {}
            try { microphone?.release() } catch (_: RuntimeException) {}
            try { recognizer?.close() } catch (_: RuntimeException) {}
            try { model?.close() } catch (_: RuntimeException) {}
            worker = null
            // Never carry raw PCM or the ASR transcript into the UI.
            if (result != null) onWake(result)
            else if (failure != null) onFailure(failure)
        }
    }
}
