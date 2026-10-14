package com.cardenaspiero255.jarvis

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.cardenaspiero255.jarvis.core.SpeechSessionState
import com.cardenaspiero255.jarvis.core.SpokenMessage
import com.cardenaspiero255.jarvis.core.SpokenReplyPolicy
import java.util.Locale

/**
 * Opt-in, user-operated Android text to speech.
 *
 * This is NOT a wake word, listening service or LLM response engine.
 * All spoken content comes from a short, local whitelist. Android's selected
 * speech engine is external and may process speech with its own privacy rules.
 */
class SpokenReplyEngine(
    context: Context,
    private val showStatus: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val session = SpeechSessionState()
    private val policy = SpokenReplyPolicy()
    private var synth: TextToSpeech? = null
    private var ready = false
    private var closed = false

    init {
        synth = TextToSpeech(context.applicationContext) { result ->
            handler.post {
                if (!closed) {
                    val engine = synth
                    if (result == TextToSpeech.SUCCESS && engine != null) {
                        val availability = engine.setLanguage(Locale.forLanguageTag("es-CL"))
                        ready = availability != TextToSpeech.LANG_MISSING_DATA &&
                            availability != TextToSpeech.LANG_NOT_SUPPORTED
                        if (!ready) showStatus("No hay voz española disponible en el motor de Android.")
                    } else {
                        ready = false
                        showStatus("El motor de voz de Android no está disponible.")
                    }
                }
            }
        }
    }

    /** Explicit button only; does not persist across restarts. */
    fun enableByTap() {
        if (!closed) {
            session.resume()
            showStatus("Respuestas de voz habilitadas en esta sesión. Pulsa LEER ESTADO.")
        }
    }

    /** Returns only that the platform accepted a request, not audible proof. */
    fun speakByTap(message: SpokenMessage): Boolean {
        if (!policy.maySpeak(message, explicitTap = true, ready = ready && !closed,
                stopped = session.stopped)) {
            showStatus("Voz detenida o motor aún no preparado. Pulsa ACTIVAR VOZ.")
            return false
        }
        val result = try {
            synth?.speak(policy.safeText(message), TextToSpeech.QUEUE_FLUSH,
                null, "jarvis-local-status") == TextToSpeech.SUCCESS
        } catch (_: IllegalStateException) {
            false
        }
        showStatus(if (result) "Android aceptó la solicitud de voz."
            else "Android no pudo iniciar la respuesta de voz.")
        return result
    }

    fun stop() {
        session.stop()
        try {
            synth?.stop()
        } catch (_: IllegalStateException) {
            // Shutdown or missing speech provider: remain fail-closed.
        }
    }

    fun close() {
        stop()
        closed = true
        ready = false
        synth?.shutdown()
        synth = null
    }
}
