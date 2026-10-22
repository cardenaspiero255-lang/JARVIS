package com.cardenaspiero255.jarvis

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.PowerManager
import com.cardenaspiero255.jarvis.core.LockScreenCaptureAccumulator
import android.os.SystemClock
import com.cardenaspiero255.jarvis.core.AudioInputMeter
import com.cardenaspiero255.jarvis.core.BargeInStopDecision
import com.cardenaspiero255.jarvis.core.BargeInStopPolicy
import com.cardenaspiero255.jarvis.core.BargeInQuestionDecision
import com.cardenaspiero255.jarvis.core.BargeInQuestionPolicy
import com.cardenaspiero255.jarvis.core.NativeAudioSourceChoice
import com.cardenaspiero255.jarvis.core.NativeAudioSourceFailoverPolicy
import com.cardenaspiero255.jarvis.core.NativeAudioReadFailurePolicy
import com.cardenaspiero255.jarvis.core.NativeAudioExitPolicy
import com.cardenaspiero255.jarvis.core.HandsFreePreviewPolicy
import com.cardenaspiero255.jarvis.core.NativeDecoderRetryPolicy
import com.cardenaspiero255.jarvis.core.OfflineCommandGrammarPolicy
import com.cardenaspiero255.jarvis.core.PartialWakeSignalPolicy
import com.cardenaspiero255.jarvis.core.PreviewWakeTurnGate
import com.cardenaspiero255.jarvis.core.WakeTurnDecision
import com.cardenaspiero255.jarvis.core.PreviewWake
import com.cardenaspiero255.jarvis.core.OfflineVoiceQuestionGate
import com.cardenaspiero255.jarvis.core.OfflineVoiceDecision
import com.cardenaspiero255.jarvis.core.SpeakerEvidence
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Dedicated OFFLINE Spanish recognizer: AudioRecord -> Vosk. Does not use
 * Android SpeechRecognizer, Google services, Qwen, network, or external APIs.
 *
 * Supports a bounded 60-second preview or explicitly user-started, bounded
 * continuous listening. No speaker verification: hotwords do not authorize
 * sensitive actions, even if voice enrollment data exists.
 */
class NativeSpanishSpeechRunner(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onReady: () -> Unit,
    private val onWake: (PreviewWake) -> Unit,
    private val onFailure: (String) -> Unit,
    private val onAudioProgress: (Long, Long) -> Unit = { _, _ -> },
    private val continuous: Boolean = false,
    private val sessionDurationMillis: Long = 60_000L,
    private val conversationMode: Boolean = false,
    private val onQuestion: (String) -> Unit = {},
    private val onBargeInStop: () -> Unit = {},
    private val onBargeInQuestion: (String) -> Unit = {}
) {
    init { require(sessionDurationMillis in 60_000L..30 * 60_000L) }
    private val active = AtomicBoolean(false)
    private val responsePlayback = AtomicBoolean(false)

    // A changing generation prevents old playback streams from being reused
    // for a new TTS response or after revocation.
    private val playbackVersion = AtomicLong()
    @Volatile private var playbackReference: String? = null
    private val bargeStopPolicy = BargeInStopPolicy()
    private val bargeQuestionPolicy = BargeInQuestionPolicy()

    /** Public voice commands remain muted during TTS; only an AEC-gated,
     * exact and completed STOP may interrupt current output. */
    fun setResponsePlayback(playing: Boolean, spoken: String? = null) {
        playbackReference = if (playing) spoken?.take(1000) else null
        playbackVersion.incrementAndGet()
        responsePlayback.set(playing)
    }
    @Volatile private var worker: Thread? = null
    @Volatile private var recorder: AudioRecord? = null
    // Only public read-only time and battery requests; never authority to act.
    // ownerOnly = false until independently verified biometric speaker evidence exists.
    private val turnGate = PreviewWakeTurnGate()
    private val questionGate = OfflineVoiceQuestionGate()

    fun start(): Boolean {
        if (!active.compareAndSet(false, true)) return false
        val thread = Thread({ runRecognition() }, "jarvis-vosk-native-spanish")
        worker = thread
        thread.start()
        return true
    }

    fun stop() {
        active.set(false)
        setResponsePlayback(false)
        worker?.interrupt()
        try { recorder?.stop() } catch (_: RuntimeException) {}
    }

    /**
     * A fixed offline grammar is an optional optimization, never authority.
     * Use the normal offline recognizer when the bundled model lacks needed
     * words or the native grammar constructor fails.
     */
    private fun createCommandRecognizer(model: Model, folder: File): Recognizer {
        val grammar = OfflineCommandGrammarPolicy().fromVocabulary(
            File(folder, "graph/words.txt"))
        if (grammar != null) {
            try {
                val constrained = Recognizer(model, 16_000.0f, grammar)
                onStatus("ASR_NATIVE_COMMAND_GRAMMAR_READY")
                return constrained
            } catch (_: IOException) {
                onStatus("ASR_NATIVE_COMMAND_GRAMMAR_FALLBACK")
            }
        } else {
            onStatus("ASR_NATIVE_COMMAND_VOCAB_UNAVAILABLE")
        }
        return Recognizer(model, 16_000.0f)
    }

    private fun runRecognition() {
        var microphone: AudioRecord? = null
        var echoCanceller: AcousticEchoCanceler? = null
        var bargeRecognizer: Recognizer? = null
        var bargeVersion = -1L
        var bargeTriggered = false
        var bargeUnavailable = false
        var aecReady = false
        var model: Model? = null
        var recognizer: Recognizer? = null
        var wakeBridge: SherpaNativeWakeBridge? = null
        var result: PreviewWake? = null
        var question: String? = null
        var failure: String? = null
        // Separate fatal capture errors from ordinary no-wake/expiry fallback.
        var readFailure: String? = null
        val levels = AudioInputMeter()
        val earlyWake = PartialWakeSignalPolicy()
        val screenOffFrames = LockScreenCaptureAccumulator()
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        var lastPartialProbe = 0L
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
                recognizer = if (conversationMode) Recognizer(model, 16_000.0f)
                    else createCommandRecognizer(model, folder)
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
                recognizer = if (conversationMode) Recognizer(model, 16_000.0f)
                    else createCommandRecognizer(model, fresh)
            }
            if (!modelReady()) return
            // Optional second decoder on the same worker; never opens a second microphone.
            wakeBridge = if (conversationMode) null
                else SherpaNativeWakeBridge.tryOpen(context, onStatus)
            onStatus("ASR_NATIVE_AUDIO_OPEN")

            val minimum = AudioRecord.getMinBufferSize(
                16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            check(minimum > 0) { "Invalid PCM buffer size" }
            // AEC availability does not guarantee that VOICE_COMMUNICATION
            // can initialize or start capture on this particular device.
            // Retry only that failed route with MIC; never retry a revoked
            // permission, and never advertise barge-in without live AEC.
            val possibleAec = continuous && AcousticEchoCanceler.isAvailable()
            val capture = NativeAudioSourceFailoverPolicy.open(possibleAec) { source ->
                var attempted: AudioRecord? = null
                var effect: AcousticEchoCanceler? = null
                var started = false
                try {
                    val input = AudioRecord(
                        if (source == NativeAudioSourceChoice.VOICE_COMMUNICATION)
                            MediaRecorder.AudioSource.VOICE_COMMUNICATION
                        else MediaRecorder.AudioSource.MIC,
                        16_000, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 4096)
                    )
                    attempted = input
                    if (input.state != AudioRecord.STATE_INITIALIZED) null else {
                        if (source == NativeAudioSourceChoice.VOICE_COMMUNICATION) {
                            effect = try {
                                AcousticEchoCanceler.create(input.audioSessionId)?.apply {
                                    enabled = true
                                }
                            } catch (_: RuntimeException) { null }
                        }
                        input.startRecording()
                        if (input.recordingState != AudioRecord.RECORDSTATE_RECORDING) null
                        else {
                            started = true
                            input to effect
                        }
                    }
                } finally {
                    if (!started) {
                        try { effect?.release() } catch (_: RuntimeException) {}
                        try { attempted?.stop() } catch (_: RuntimeException) {}
                        try { attempted?.release() } catch (_: RuntimeException) {}
                    }
                }
            }
            val opened = checkNotNull(capture) { "AudioRecord not capturing" }
            microphone = opened.second.first
            recorder = microphone
            echoCanceller = opened.second.second
            aecReady = try { echoCanceller?.enabled == true }
                catch (_: RuntimeException) { false }
            if (possibleAec && opened.first == NativeAudioSourceChoice.MICROPHONE) {
                onStatus("ASR_NATIVE_AEC_CAPTURE_FALLBACK_MIC: origen de voz " +
                    "no disponible; Vosk continúa sin interrupción automática de TTS.")
            }
            if (continuous && aecReady) {
                onStatus("ASR_NATIVE_AEC_CONFIGURED: AEC habilitado por Android; " +
                    "eficacia acústica NO_CERTIFICADA.")
            }
            if (continuous && !aecReady) {
                onStatus("ASR_NATIVE_BARGEIN_AEC_UNAVAILABLE: usa DETENER en aviso.")
            }

            onReady()
            onStatus("ASR_NATIVE_READY: micrófono y motor Vosk español activos " +
                "${sessionDurationMillis / 1000} s.")
            val audio = ShortArray(2048)
            val start = SystemClock.elapsedRealtime()
            var lastStatus = start
            var lastAudioReport = start
            var audioSinceReport = 0L
            while (active.get() && !Thread.currentThread().isInterrupted &&
                SystemClock.elapsedRealtime() - start < sessionDurationMillis) {
                val received = microphone.read(
                    audio, 0, audio.size, AudioRecord.READ_BLOCKING
                )
                when {
                    received > 0 -> {
                        // AudioRecord bytes genuinely received, not a timer heartbeat.
                        // Emit only counts + elapsedRealtime; never PCM or transcripts.
                        audioSinceReport += received.toLong()
                        // Sample display state at AudioRecord.read(), not in UI.
                        if (continuous) {
                            val interactiveAtCapture = try {
                                power?.isInteractive ?: true
                            } catch (_: RuntimeException) { true }
                            screenOffFrames.observe(received, interactiveAtCapture)
                        }
                        val progressNow = SystemClock.elapsedRealtime()
                        if (progressNow - lastAudioReport >= 2_500L) {
                            val capturedWhileScreenOff = screenOffFrames.drainScreenOff()
                            if (continuous) {
                                if (capturedWhileScreenOff > 0L)
                                    onAudioProgress(capturedWhileScreenOff, progressNow)
                            } else onAudioProgress(audioSinceReport, progressNow)
                            audioSinceReport = 0L
                            lastAudioReport = progressNow
                        }
                        // Never route TTS echo into the normal wake/command
                        // recognizer. On an explicitly authorized long session,
                        // a *separate* temporary Vosk stream may detect only a
                        // completed STOP, and only when Android AEC is active.
                        if (responsePlayback.get()) {
                            val version = playbackVersion.get()
                            if (bargeVersion != version) {
                                try { bargeRecognizer?.close() }
                                catch (_: RuntimeException) {}
                                bargeRecognizer = null
                                bargeVersion = version
                                bargeTriggered = false
                                bargeUnavailable = false
                            }
                            val spoken = playbackReference
                            if (aecReady && spoken != null && !bargeTriggered &&
                                !bargeUnavailable && active.get()) {
                                try {
                                    val stopAsr = bargeRecognizer ?: Recognizer(
                                        checkNotNull(model), 16_000.0f
                                    ).also { bargeRecognizer = it }
                                    if (stopAsr.acceptWaveForm(audio, received)) {
                                        val heard = JSONObject(stopAsr.result)
                                            .optString("text", "")
                                        val decision = bargeStopPolicy.decide(
                                            heard, spoken, responsePlayback.get(),
                                            aecReady, finalUtterance = true
                                        )
                                        // A completed Vosk utterance must not
                                        // carry state into another segment.
                                        stopAsr.close()
                                        bargeRecognizer = null
                                        if (decision == BargeInStopDecision.STOP_TTS &&
                                            active.get() && responsePlayback.get() &&
                                            playbackVersion.get() == version) {
                                            bargeTriggered = true
                                            onBargeInStop()
                                        } else if (conversationMode && continuous &&
                                            active.get() && responsePlayback.get() &&
                                            playbackVersion.get() == version) {
                                            // Only the dedicated, opted-in local GGUF mode
                                            // can turn a full wake-prefixed interruption
                                            // into a new text question. Never tools/actions.
                                            val followup = bargeQuestionPolicy.decide(
                                                heard, spoken, playbackActive = true,
                                                echoCancellationActive = aecReady,
                                                finalUtterance = true
                                            )
                                            val next = followup.question
                                            if (followup.decision == BargeInQuestionDecision.ASK &&
                                                next != null) {
                                                bargeTriggered = true
                                                setResponsePlayback(true) // no TTS echo reference yet
                                                onBargeInQuestion(next)
                                            }
                                        }
                                    }
                                } catch (_: Exception) {
                                    try { bargeRecognizer?.close() }
                                    catch (_: RuntimeException) {}
                                    bargeRecognizer = null
                                    bargeUnavailable = true
                                    onStatus("ASR_NATIVE_BARGEIN_UNAVAILABLE: usa DETENER.")
                                }
                            }
                            audio.fill(0, 0, received)
                            continue
                        }
                        if (bargeRecognizer != null) {
                            try { bargeRecognizer?.close() }
                            catch (_: RuntimeException) {}
                            bargeRecognizer = null
                        }
                        levels.observe(audio, received)
                        // Native KWS provides only a candidate; Vosk still decides
                        // completed public requests. Voice identity stays UNAVAILABLE.
                        try {
                            if (wakeBridge?.consume(audio, received,
                                    SystemClock.elapsedRealtime()) == true) {
                                // The real JNI candidate opens only the same bounded
                                // 8-second PUBLIC command window as ASR "Jarvis".
                                // Identity remains UNAVAILABLE; never run partial orders.
                                val gateDecision = turnGate.observe(
                                    "Jarvis", SystemClock.elapsedRealtime(),
                                    SpeakerEvidence.UNAVAILABLE
                                )
                                if (gateDecision == WakeTurnDecision.WAITING) {
                                    onStatus("KWS_NATIVE_WAKE_WAITING: Jarvis detectado; " +
                                        "di «dime la hora» en 8 s (sin identidad verificada).")
                                }
                            }
                        } catch (_: Exception) {
                            try { wakeBridge?.close() } catch (_: Exception) {}
                            wakeBridge = null
                            onStatus("KWS_NATIVE_RUNTIME_FALLBACK: Vosk sigue activo.")
                        } catch (_: UnsatisfiedLinkError) {
                            try { wakeBridge?.close() } catch (_: Exception) {}
                            wakeBridge = null
                            onStatus("KWS_NATIVE_ABI_FALLBACK: Vosk sigue activo.")
                        }
                        if (recognizer.acceptWaveForm(audio, received)) {
                            // A partial wake is advisory only. Only completed speech
                            // enters PreviewWakeTurnGate for command authorization.
                            earlyWake.onUtteranceCompleted()
                            val text = JSONObject(recognizer.result).optString("text", "")
                            if (text.isNotBlank()) {
                                if (conversationMode) {
                                    // The unrestricted offline Vosk decoder produces untrusted
                                    // text; ONLY the explicit read-only GGUF session can use it.
                                    val event = questionGate.observe(text,
                                        SystemClock.elapsedRealtime())
                                    when (event.decision) {
                                        OfflineVoiceDecision.STOP -> {
                                            result = PreviewWake.STOP
                                            break
                                        }
                                        OfflineVoiceDecision.QUESTION -> {
                                            question = event.question
                                            if (conversationMode && continuous) {
                                                // Keep this *same microphone* alive during
                                                // GGUF inference/TTS; mute every normal
                                                // command until TTS finishes its echo guard.
                                                responsePlayback.set(true)
                                                if (question != null) onQuestion.invoke(question)
                                                question = null
                                            } else {
                                                break
                                            }
                                        }
                                        OfflineVoiceDecision.WAITING -> onStatus(
                                            "LOCAL_VOICE_WAKE_WAITING: pregunta en 8 segundos.")
                                        OfflineVoiceDecision.IGNORED -> hadUnmatchedSpeech = true
                                    }
                                    continue
                                }
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
                                        if (continuous) {
                                            // Set before dispatch to prevent TTS echo races.
                                            responsePlayback.set(true)
                                            onWake(PreviewWake.LOCAL_TIME)
                                        } else {
                                            result = PreviewWake.LOCAL_TIME
                                            break
                                        }
                                    }
                                    WakeTurnDecision.LOCAL_BATTERY -> {
                                        if (continuous) {
                                            responsePlayback.set(true)
                                            onWake(PreviewWake.LOCAL_BATTERY)
                                        } else {
                                            result = PreviewWake.LOCAL_BATTERY
                                            break
                                        }
                                    }
                                    WakeTurnDecision.WAITING -> onStatus(
                                        "ASR_NATIVE_WAKE_WAITING: Jarvis detectado. " +
                                            "Di «dime la hora» o «batería» en 8 s.")
                                    WakeTurnDecision.BLOCKED_IDENTITY -> {
                                        failure = "ASR_NATIVE_IDENTITY_BLOCKED: voz no confiable."
                                        break
                                    }
                                    WakeTurnDecision.IGNORED -> hadUnmatchedSpeech = true
                                }
                            }
                        } else {
                            val probeTime = SystemClock.elapsedRealtime()
                            // Bound partial JSON reads and notifications to avoid
                            // doing extra recognition work on every PCM buffer.
                            if (probeTime - lastPartialProbe >= 400L) {
                                lastPartialProbe = probeTime
                                val partial = JSONObject(recognizer.partialResult)
                                    .optString("partial", "")
                                if (earlyWake.shouldReport(partial)) onStatus(
                                    "ASR_NATIVE_WAKE_CANDIDATE: «Jarvis» detectado " +
                                        "parcialmente; esperando frase completa.")
                            }
                        }
                    }
                    received < 0 -> {
                        // Negative reads can mean a revoked microphone grant
                        // OR a device/driver failure. Distinguish them using
                        // current permission state, never claim revocation
                        // merely because AudioRecord returned an error.
                        val granted = try {
                            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                        } catch (_: RuntimeException) { true }
                        readFailure = NativeAudioReadFailurePolicy.classify(received, granted)
                        break
                    }
                }
                val now = SystemClock.elapsedRealtime()
                val wakeExpired = if (conversationMode) questionGate.expiredAt(now)
                    else turnGate.expiredAt(now)
                if (wakeExpired) {
                    if (conversationMode) {
                        onStatus("LOCAL_VOICE_WAKE_EXPIRED: di Jarvis otra vez.")
                    } else if (continuous) {
                        onStatus("ASR_NATIVE_WAKE_EXPIRED: espera otra vez «Jarvis».")
                    } else {
                        result = PreviewWake.WAKE_ONLY
                        break
                    }
                }
                if (now - lastStatus >= 10_000L) {
                    val reading = levels.reading()
                    onStatus("ASR_NATIVE_LISTENING: " +
                        "RMS ${reading.rms}; muestras ${reading.samples}. " +
                        "Escuchando «Jarvis, dime la hora» o «Jarvis, batería».")
                    lastStatus = now
                }
            }
            if (NativeAudioExitPolicy.mayDeliverRecognizedSpeech(readFailure) &&
                conversationMode && result == null && question == null && active.get()) {
                val finalText = JSONObject(recognizer.finalResult).optString("text", "")
                if (finalText.isNotBlank()) {
                    val event = questionGate.observe(finalText,
                        SystemClock.elapsedRealtime())
                    when (event.decision) {
                        OfflineVoiceDecision.STOP -> result = PreviewWake.STOP
                        OfflineVoiceDecision.QUESTION -> question = event.question
                        else -> Unit
                    }
                }
            }
            if (NativeAudioExitPolicy.mayDeliverRecognizedSpeech(readFailure) &&
                !conversationMode && result == null && active.get() && !continuous) {
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
                else if (finalDecision == WakeTurnDecision.LOCAL_BATTERY)
                    result = PreviewWake.LOCAL_BATTERY
                else if (finalDecision == WakeTurnDecision.WAITING || turnGate.waiting())
                    result = PreviewWake.WAKE_ONLY
                else if (finalDecision == WakeTurnDecision.BLOCKED_IDENTITY)
                    failure = "ASR_NATIVE_IDENTITY_BLOCKED: voz no confiable."
            }
            if (readFailure == null && failure == null &&
                result == null && active.get() && continuous) {
                failure = "ASR_NATIVE_SESSION_WINDOW_ENDED: fin de la sesión autorizada."
            }
            if (readFailure == null && failure == null &&
                result == null && question == null && active.get() && !continuous) {
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
            val deliverQuestion = active.get() && !Thread.currentThread().isInterrupted
            active.set(false)
            recorder = null
            try { microphone?.stop() } catch (_: RuntimeException) {}
            try { microphone?.release() } catch (_: RuntimeException) {}
            try { recognizer?.close() } catch (_: RuntimeException) {}
            try { bargeRecognizer?.close() } catch (_: RuntimeException) {}
            try { echoCanceller?.release() } catch (_: RuntimeException) {}
            try { wakeBridge?.close() } catch (_: Exception) {}
            try { model?.close() } catch (_: RuntimeException) {}
            worker = null
            // Fatal AudioRecord errors dominate ANY late decoder result or
            // ordinary timeout. The worker has already released its mic.
            val finalFailure = NativeAudioExitPolicy.resolveFailure(readFailure, failure)
            if (finalFailure != null) onFailure(finalFailure)
            else if (deliverQuestion && question != null) onQuestion(question)
            else if (result != null) onWake(result)
        }
    }
}
