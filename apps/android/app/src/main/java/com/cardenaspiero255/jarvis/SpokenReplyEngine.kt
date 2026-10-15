package com.cardenaspiero255.jarvis

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.cardenaspiero255.jarvis.core.ChatSpeechPolicy
import com.cardenaspiero255.jarvis.core.DeviceHealthSnapshot
import com.cardenaspiero255.jarvis.core.DeviceHealthSpeechPolicy
import com.cardenaspiero255.jarvis.core.InstalledVoice
import com.cardenaspiero255.jarvis.core.SpeechSessionState
import com.cardenaspiero255.jarvis.core.SpeechUtteranceTracker
import com.cardenaspiero255.jarvis.core.SpokenMessage
import com.cardenaspiero255.jarvis.core.SpokenReplyPolicy
import com.cardenaspiero255.jarvis.core.VoiceStyle
import com.cardenaspiero255.jarvis.core.VoiceStyleSelector

/**
 * Local TTS on explicit tap. Tracks the actual Android utterance callbacks,
 * never claims audible output solely because TextToSpeech.speak() queued text.
 * No network voice, background listening, transcript storage, or actor cloning.
 */
class SpokenReplyEngine(
    context: Context,
    private val showStatus: (String) -> Unit,
    private val onSpeechProgress: (Boolean) -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val tracker = SpeechUtteranceTracker()
    private val session = SpeechSessionState()
    private val policy = SpokenReplyPolicy()
    private val chatSpeech = ChatSpeechPolicy()
    private val healthSpeech = DeviceHealthSpeechPolicy()
    private var synth: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var style = VoiceStyle.SPANISH

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            handler.post {
                if (!closed && tracker.started(utteranceId)) {
                    onSpeechProgress(true)
                    showStatus("JARVIS está reproduciendo voz local por Android. " + audioHint())
                }
            }
        }

        override fun onDone(utteranceId: String?) {
            handler.post {
                if (!closed && tracker.done(utteranceId)) {
                    onSpeechProgress(false)
                    showStatus("Android completó la síntesis de voz. " + audioHint())
                }
            }
        }

        @Deprecated("Android also dispatches onError with a code")
        override fun onError(utteranceId: String?) {
            notifyError(utteranceId)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            notifyError(utteranceId)
        }

        private fun notifyError(utteranceId: String?) {
            handler.post {
                if (!closed && tracker.failed(utteranceId)) {
                    onSpeechProgress(false)
                    showStatus("Android notificó un fallo de voz. Comprueba los datos " +
                        "de voz sin conexión en ajustes y prueba nuevamente.")
                }
            }
        }
    }

    init {
        synth = TextToSpeech(context.applicationContext) { result ->
            // Listener may fire before constructor assigned synth on some OEMs;
            // posting resolves that race without persisting user consent.
            handler.post {
                if (!closed) {
                    if (result == TextToSpeech.SUCCESS) {
                        synth?.setOnUtteranceProgressListener(listener)
                        ready = configureOfflineVoice()
                    } else {
                        ready = false
                    }
                    if (!ready) showStatus(
                        "Voz no preparada: revisa motor de síntesis y descarga " +
                            "una voz local de español en ajustes de Android."
                    )
                }
            }
        }
    }

    private fun configureOfflineVoice(): Boolean {
        val engine = synth ?: return false
        val voices = try { engine.voices?.toList() ?: emptyList() }
            catch (_: RuntimeException) { emptyList() }
        val installed = voices.map {
            InstalledVoice(it.name, it.locale.toLanguageTag(), it.isNetworkConnectionRequired)
        }
        val options = VoiceStyleSelector.candidates(style, installed)
        if (options.isEmpty()) return false

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        return try {
            engine.stop()
            engine.setAudioAttributes(attributes)
            for (candidate in options) {
                val voice = voices.firstOrNull { it.name == candidate.name &&
                    it.locale.toLanguageTag().equals(candidate.localeTag, ignoreCase = true) &&
                    !it.isNetworkConnectionRequired } ?: continue
                // Some OEM engines reject their first listed voice. Try the next
                // installed, offline voice rather than becoming permanently mute.
                if (engine.setVoice(voice) == TextToSpeech.SUCCESS &&
                    engine.setSpeechRate(VoiceStyleSelector.rate(style)) == TextToSpeech.SUCCESS &&
                    engine.setPitch(VoiceStyleSelector.pitch(style)) == TextToSpeech.SUCCESS) {
                    return true
                }
            }
            false
        } catch (_: RuntimeException) { false }
    }

    private fun audioHint(): String {
        val level = try { audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) }
            catch (_: RuntimeException) { null }
        return if (level == 0) {
            "El volumen multimedia está en cero; súbelo antes de probar."
        } else {
            "Si no escuchas sonido, revisa volumen multimedia, Bluetooth " +
                "y la voz instalada en Android."
        }
    }

    fun selectStyleByTap(next: VoiceStyle) {
        if (closed) return
        stop()
        style = next
        ready = configureOfflineVoice()
        showStatus(
            if (ready) when (next) {
                VoiceStyle.SPANISH -> "Voz española local preparada. Pulsa PROBAR AUDIO JARVIS."
                VoiceStyle.BRITISH_ASSISTANT ->
                    "Voz británica local preparada. Pulsa ACTIVAR RESPUESTAS DE VOZ."
            } else "No se encontró voz compatible SIN conexión para ese perfil. " +
                "Revisa los datos de síntesis de Android."
        )
    }

    fun enableByTap() {
        if (closed) return
        if (!ready) ready = configureOfflineVoice()
        if (!ready) {
            showStatus("Motor de voz no preparado. Instala una voz local o cambia el perfil.")
            return
        }
        session.resume()
        showStatus("Voz local activada para esta sesión. Pulsa LEER ESTADO EN VOZ ALTA.")
    }

    /** One visible tap grants only a single local canned speech attempt. */
    fun testVoiceByTap(): Boolean {
        if (closed) return false
        if (!ready) ready = configureOfflineVoice()
        if (!ready) {
            showStatus("No se pudo preparar una voz offline. Pulsa CONFIGURAR VOZ EN ANDROID.")
            return false
        }
        session.resume()
        return speakByTap(SpokenMessage.LOCAL_STATUS)
    }

    private fun speakExplicitText(text: String): Boolean {
        if (closed || !ready || session.stopped) {
            showStatus("Voz detenida o no preparada. Pulsa ACTIVAR RESPUESTAS DE VOZ.")
            return false
        }
        val engine = synth ?: return false
        val ticket = tracker.begin() ?: return false
        val result = try {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, ticket)
        } catch (_: RuntimeException) { TextToSpeech.ERROR }
        if (result != TextToSpeech.SUCCESS) {
            tracker.failed(ticket)
            showStatus("Android rechazó la reproducción. Revisa voz local y ajustes TTS.")
            return false
        }
        showStatus("Audio solicitado a Android. Esperando inicio real... " + audioHint())
        handler.postDelayed({
            if (!closed && tracker.timedOut(ticket)) {
                // A hung OEM TTS queue must not start playing unexpectedly after
                // the timeout. This only stops the still-current ticket.
                try { synth?.stop() } catch (_: RuntimeException) { /* fail closed */ }
                onSpeechProgress(false)
                showStatus("Android no inició la voz en 10 segundos; cancelé la " +
                    "reproducción pendiente. Prueba CONFIGURAR VOZ EN ANDROID " +
                    "y revisa Bluetooth.")
            }
        }, 10_000L)
        return true
    }

    fun speakByTap(message: SpokenMessage): Boolean {
        if (!policy.maySpeak(message, explicitTap = true, ready = ready && !closed,
                stopped = session.stopped)) {
            showStatus("Voz detenida o motor no preparado. Pulsa ACTIVAR RESPUESTAS DE VOZ.")
            return false
        }
        return speakExplicitText(policy.safeText(message, style))
    }

    fun speakChatByTap(answer: String?): Boolean {
        val safe = chatSpeech.textForSpeech(answer, explicitTap = true,
            ready = ready && !closed, stopped = session.stopped)
        if (safe == null) {
            showStatus("Respuesta de chat no autorizada para voz. " +
                "Activa la voz y genera una respuesta local.")
            return false
        }
        return speakExplicitText(safe)
    }

    fun offlineVoiceReady(): Boolean = ready && !closed

    fun speakHealthByTap(snapshot: DeviceHealthSnapshot): Boolean {
        val safe = healthSpeech.textForSpeech(snapshot, explicitTap = true,
            ready = ready && !closed, stopped = session.stopped)
        if (safe == null) {
            showStatus("Voz local detenida o no preparada. Actívala antes de leer el estado.")
            return false
        }
        return speakExplicitText(safe)
    }

    fun stop() {
        onSpeechProgress(false)
        tracker.cancel()
        session.stop()
        try { synth?.stop() } catch (_: RuntimeException) { /* fail closed */ }
    }

    fun close() {
        if (closed) return
        stop()
        closed = true
        ready = false
        try { synth?.shutdown() } catch (_: RuntimeException) { /* fail closed */ }
        synth = null
    }
}
