package com.cardenaspiero255.jarvis

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer

/**
 * One-shot, foreground-only speech recognition after an explicit HABLAR tap.
 *
 * The activity owns the microphone runtime permission and a separate one-use
 * recognition request gate. This component never starts automatically, stores
 * audio/transcripts, or runs as a background service. Recognition may be
 * provided by Android's configured speech provider (including network-based
 * providers, depending on the user's Android settings).
 */
class ForegroundSpeechRecognizer(
    private val context: Context,
    private val onResult: (String?) -> Unit,
    private val onFailure: (Int) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onAudioLevel: (Float) -> Unit = {}
) {
    private var speech: SpeechRecognizer? = null
    private var generation: Long = 0L
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private var watchdog: Runnable? = null

    private fun armWatchdog(ticket: Long, ms: Long, message: String, code: Int) {
        watchdog?.let { watchdogHandler.removeCallbacks(it) }
        watchdog = Runnable {
            if (isCurrent(ticket)) {
                onStatus(message)
                stop()
                onFailure(code)
            }
        }.also { watchdogHandler.postDelayed(it, ms) }
    }

    fun start(intent: Intent): Boolean {
        if (speech != null) return false
        val available = try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: RuntimeException) {
            false
        }
        if (!available) return false

        val ticket = ++generation
        return try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speech = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (isCurrent(ticket)) {
                        onStatus("Micrófono listo para escuchar. Habla ahora.")
                        armWatchdog(ticket, 23_000L,
                            "La escucha terminó sin respuesta del servicio Android.",
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                    }
                }

                override fun onBeginningOfSpeech() {
                    if (isCurrent(ticket)) onStatus("Voz detectada por Android. Sigue hablando.")
                }
                override fun onRmsChanged(rmsdB: Float) {
                    if (isCurrent(ticket) && rmsdB.isFinite()) onAudioLevel(rmsdB)
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (isCurrent(ticket)) {
                        onAudioLevel(-2f)
                        onStatus("Procesando lo que dijiste...")
                    }
                }

                override fun onError(error: Int) {
                    if (!isCurrent(ticket)) return
                    stop()
                    onFailure(error)
                }

                override fun onResults(results: Bundle?) {
                    if (!isCurrent(ticket)) return
                    val text = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )?.firstOrNull()
                    stop()
                    onResult(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // No storage or processing of partial / unconfirmed speech.
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            recognizer.startListening(intent)
            armWatchdog(ticket, 10_000L,
                "El reconocimiento de Android no inició el micrófono en diez segundos.",
                ERROR_NO_READY_CALLBACK)
            true
        } catch (_: RuntimeException) {
            // Includes SecurityException: no microphone session survives a denial.
            stop()
            false
        }
    }

    companion object {
        /** Local diagnostic only: no Android audio-ready callback occurred. */
        const val ERROR_NO_READY_CALLBACK = -101
    }

    private fun isCurrent(ticket: Long): Boolean =
        generation == ticket && speech != null

    /** Invalidates callbacks before stopping so older results cannot be reused. */
    fun stop() {
        generation++
        watchdog?.let { watchdogHandler.removeCallbacks(it) }
        watchdog = null
        onAudioLevel(-2f)
        val current = speech
        speech = null
        try { current?.cancel() } catch (_: RuntimeException) {}
        try { current?.destroy() } catch (_: RuntimeException) {}
    }
}
