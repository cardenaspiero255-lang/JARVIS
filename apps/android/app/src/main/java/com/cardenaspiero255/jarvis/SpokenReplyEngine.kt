package com.cardenaspiero255.jarvis

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.cardenaspiero255.jarvis.core.ChatSpeechPolicy
import com.cardenaspiero255.jarvis.core.InstalledVoice
import com.cardenaspiero255.jarvis.core.SpeechSessionState
import com.cardenaspiero255.jarvis.core.SpokenMessage
import com.cardenaspiero255.jarvis.core.SpokenReplyPolicy
import com.cardenaspiero255.jarvis.core.VoiceStyle
import com.cardenaspiero255.jarvis.core.VoiceStyleSelector

/**
 * On-device, explicitly triggered speech using an installed offline TTS voice.
 * Never listens in the background, clones an actor, or speaks arbitrary model
 * content. Locale, voice identity, and availability belong to Android's engine.
 */
class SpokenReplyEngine(
    context: Context,
    private val showStatus: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val session = SpeechSessionState()
    private val policy = SpokenReplyPolicy()
    private val chatSpeech = ChatSpeechPolicy()
    private var synth: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var style: VoiceStyle = VoiceStyle.SPANISH

    init {
        synth = TextToSpeech(context.applicationContext) { result ->
            handler.post {
                if (!closed) {
                    ready = result == TextToSpeech.SUCCESS && configureOfflineVoice()
                    if (!ready) {
                        showStatus("No hay voz local compatible. Instala datos de voz sin conexión en los ajustes de Android.")
                    }
                }
            }
        }
    }

    private fun configureOfflineVoice(): Boolean {
        val engine = synth ?: return false
        val voices = try {
            engine.voices?.toList() ?: emptyList()
        } catch (_: RuntimeException) {
            return false
        }
        val desired = VoiceStyleSelector.select(style, voices.map {
            InstalledVoice(it.name, it.locale.toLanguageTag(), it.isNetworkConnectionRequired)
        }) ?: return false
        val selected = voices.firstOrNull {
            it.name == desired.name &&
                it.locale.toLanguageTag().equals(desired.localeTag, ignoreCase = true) &&
                !it.isNetworkConnectionRequired
        } ?: return false
        return try {
            engine.stop()
            engine.setVoice(selected) == TextToSpeech.SUCCESS &&
                engine.setSpeechRate(VoiceStyleSelector.rate(style)) == TextToSpeech.SUCCESS &&
                engine.setPitch(VoiceStyleSelector.pitch(style)) == TextToSpeech.SUCCESS
        } catch (_: RuntimeException) {
            false
        }
    }

    /** Selection stops speech and requires another explicit activation. */
    fun selectStyleByTap(next: VoiceStyle) {
        if (closed) return
        session.stop()
        try { synth?.stop() } catch (_: RuntimeException) { /* fail closed */ }
        style = next
        ready = configureOfflineVoice()
        showStatus(
            if (ready) when (style) {
                VoiceStyle.SPANISH ->
                    "Voz local en español preparada. Pulsa ACTIVAR RESPUESTAS DE VOZ."
                VoiceStyle.BRITISH_ASSISTANT ->
                    "Voz británica local preparada. Pulsa ACTIVAR RESPUESTAS DE VOZ."
            } else "No se encontró una voz local compatible para este perfil. Consulta los ajustes de texto a voz."
        )
    }

    /** Explicit button only; never persists consent across restarts. */
    fun enableByTap() {
        if (closed) return
        if (!ready) {
            showStatus("Motor de voz no preparado. Instala la voz local o prueba otro perfil.")
            return
        }
        session.resume()
        showStatus("Respuestas de voz activadas solo para esta sesión. Pulsa LEER ESTADO.")
    }

    /** Returns platform acceptance, not audible proof or voice identity proof. */
    fun speakByTap(message: SpokenMessage): Boolean {
        if (!policy.maySpeak(message, explicitTap = true, ready = ready && !closed,
                stopped = session.stopped)) {
            showStatus("Voz detenida o motor no preparado. Pulsa ACTIVAR RESPUESTAS DE VOZ.")
            return false
        }
        val accepted = try {
            synth?.speak(policy.safeText(message, style), TextToSpeech.QUEUE_FLUSH,
                null, "jarvis-local-offline") == TextToSpeech.SUCCESS
        } catch (_: RuntimeException) {
            false
        }
        showStatus(if (accepted) "Android aceptó la respuesta de voz local."
            else "Android no pudo iniciar la respuesta de voz.")
        return accepted
    }

    /**
     * Reads only locally generated, allowlisted chat responses. A button press
     * AND an already enabled offline voice session are mandatory. Never accept
     * OCR, arbitrary prompts or restored chat rows as speech commands.
     */
    fun speakChatByTap(answer: String?): Boolean {
        val safe = chatSpeech.textForSpeech(answer, explicitTap = true,
            ready = ready && !closed, stopped = session.stopped)
        if (safe == null) {
            showStatus("Respuesta de chat no autorizada para voz. Activa la voz y genera una respuesta local.")
            return false
        }
        val accepted = try {
            synth?.speak(safe, TextToSpeech.QUEUE_FLUSH, null,
                "jarvis-local-chat-offline") == TextToSpeech.SUCCESS
        } catch (_: RuntimeException) {
            false
        }
        showStatus(if (accepted) "Android aceptó la respuesta de chat para voz local."
            else "Android no pudo hablar la respuesta local.")
        return accepted
    }

    /** Snapshot of Android's local TTS readiness, not proof that sound is audible. */
    fun offlineVoiceReady(): Boolean = ready && !closed

    fun stop() {
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
