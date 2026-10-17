package com.cardenaspiero255.jarvis

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.cardenaspiero255.jarvis.core.HandsFreePreviewPolicy
import com.cardenaspiero255.jarvis.core.PreviewStart
import com.cardenaspiero255.jarvis.core.PreviewTimeGate
import com.cardenaspiero255.jarvis.core.PreviewWake
import com.cardenaspiero255.jarvis.core.SpeakerEvidence
import java.time.LocalTime

/**
 * Explicit 60-second / one-utterance experiment, NOT always-on hotwording or
 * speaker verification. Uses on-device Android recognition only; NEVER cloud
 * speech fallback, stored transcripts, or external actions.
 */
class HandsFreePreviewService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val policy = HandsFreePreviewPolicy()
    private val timeGate = PreviewTimeGate(60_000L)
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var consumed = false
    private var closed = false
    private val timeout = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "JARVIS · manos libres (prueba)",
                NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START ||
            !intent.getBooleanExtra(EXTRA_TAPPED, false) || isRunning || closed) {
            if (!isRunning) stopSelf()
            return START_NOT_STICKY
        }
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val notifications = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val offline = Build.VERSION.SDK_INT >= 31 && try {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        } catch (_: RuntimeException) { false }
        val result = policy.canStart(true, mic, notifications, offline)
        if (result != PreviewStart.ALLOWED) {
            lastState = if (result == PreviewStart.OFFLINE_ENGINE_MISSING) {
                "Android no tiene reconocimiento local compatible. " +
                    "JARVIS no cambia a un proveedor en la nube."
            } else "Permisos de micrófono o notificaciones incompletos."
            stopSelf()
            return START_NOT_STICKY
        }
        if (!timeGate.begin(SystemClock.elapsedRealtime())) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification("Escuchando una frase…"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification("Escuchando una frase…"))
            }
        } catch (_: RuntimeException) {
            lastState = "Android no permitió iniciar el micrófono."
            stopSelf()
            return START_NOT_STICKY
        }
        isRunning = true
        lastState = "Prueba 60 s activa. Di: Jarvis dime la hora. " +
            "Aún NO se comprueba la identidad del hablante."
        main.postDelayed(timeout, 60_000L)
        prepareTts()
        startOfflineAsr()
        return START_NOT_STICKY
    }

    private fun startOfflineAsr() {
        if (!isRunning || !timeGate.isActiveAt(SystemClock.elapsedRealtime())) {
            stopSelf()
            return
        }
        try {
            val current = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            recognizer = current
            current.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) =
                    showStatus("Reconocimiento offline activo · una frase")
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = showStatus("Procesando localmente…")
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onError(error: Int) {
                    if (!isRunning || consumed) return
                    consumed = true
                    lastState = "Reconocimiento offline terminó (código $error). " +
                        "No se ejecutó ninguna orden."
                    stopSelf()
                }

                override fun onResults(results: Bundle?) {
                    if (!isRunning || consumed || !timeGate.isActiveAt(
                            SystemClock.elapsedRealtime())) {
                        stopSelf()
                        return
                    }
                    consumed = true
                    val transcript = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )?.firstOrNull().orEmpty()
                    closeRecognizer() // release microphone before starting TTS
                    when (policy.classify(transcript, SpeakerEvidence.UNAVAILABLE)) {
                        PreviewWake.LOCAL_TIME -> {
                            val now = LocalTime.now()
                            val answer = if (now.minute == 0)
                                "Son las " + now.hour + " en punto."
                            else "Son las " + now.hour + " y " + now.minute + "."
                            lastState = "Hora local reconocida. Voice Match aún pendiente."
                            speakThenStop(answer)
                        }
                        PreviewWake.WAKE_ONLY -> {
                            lastState = "Jarvis reconocido. Esta prueba acepta una frase completa."
                            speakThenStop("Te escuché. Di: Jarvis dime la hora.")
                        }
                        PreviewWake.IGNORED, PreviewWake.BLOCKED_IDENTITY -> {
                            lastState = "Frase ignorada. No se ejecutó ninguna orden."
                            stopSelf()
                        }
                    }
                }
            })
            current.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            })
        } catch (_: RuntimeException) {
            lastState = "No se pudo iniciar el reconocimiento local de Android."
            stopSelf()
        }
    }

    private fun prepareTts() {
        try {
            tts = TextToSpeech(applicationContext) { result ->
                main.post {
                    if (closed || !isRunning) return@post
                    if (result == TextToSpeech.SUCCESS) {
                        val engine = tts
                        val voice = try {
                            engine?.voices?.firstOrNull {
                                it.locale.language == "es" && !it.isNetworkConnectionRequired
                            }
                        } catch (_: RuntimeException) { null }
                        if (voice != null) {
                            ttsReady = try {
                                engine.setVoice(voice) == TextToSpeech.SUCCESS
                            } catch (_: RuntimeException) { false }
                        }
                    }
                    val pending = pendingSpeech
                    if (pending != null) {
                        pendingSpeech = null
                        if (ttsReady) play(pending) else {
                            lastState = "Reconocido, pero sin voz española local. " +
                                "Configura TTS sin conexión en Android."
                            stopSelf()
                        }
                    }
                }
            }
        } catch (_: RuntimeException) {
            ttsReady = false
        }
    }

    private fun speakThenStop(text: String) {
        if (!isRunning || !timeGate.isActiveAt(SystemClock.elapsedRealtime())) {
            stopSelf()
            return
        }
        closeRecognizer()
        if (ttsReady) play(text) else {
            pendingSpeech = text
            showStatus("Respuesta preparada · esperando TTS offline")
        }
    }

    private fun play(text: String) {
        val engine = tts ?: run { stopSelf(); return }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { main.post { stopSelf() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { stopSelf() } }
            override fun onError(utteranceId: String?, errorCode: Int) {
                main.post { stopSelf() }
            }
        })
        showStatus("JARVIS respondiendo localmente…")
        val result = try {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-preview")
        } catch (_: RuntimeException) { TextToSpeech.ERROR }
        if (result != TextToSpeech.SUCCESS) {
            lastState = "Android rechazó la síntesis de voz."
            stopSelf()
        }
    }

    private fun closeRecognizer() {
        val current = recognizer
        recognizer = null
        try { current?.cancel() } catch (_: RuntimeException) {}
        try { current?.destroy() } catch (_: RuntimeException) {}
    }

    private fun notification(summary: String): Notification {
        val stop = PendingIntent.getService(this, 404,
            Intent(this, HandsFreePreviewService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 405,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("JARVIS · prueba manos libres")
            .setContentText(summary)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "DETENER", stop)
            .build()
    }

    private fun showStatus(summary: String) {
        if (isRunning && !closed) getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notification(summary))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        closed = true
        isRunning = false
        consumed = true
        timeGate.stop()
        pendingSpeech = null
        main.removeCallbacks(timeout)
        closeRecognizer()
        try { tts?.stop() } catch (_: RuntimeException) {}
        try { tts?.shutdown() } catch (_: RuntimeException) {}
        tts = null
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: RuntimeException) {}
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.cardenaspiero255.jarvis.HANDSFREE_START"
        const val ACTION_STOP = "com.cardenaspiero255.jarvis.HANDSFREE_STOP"
        const val EXTRA_TAPPED = "jarvis.handsfree.explicit"
        private const val CHANNEL = "jarvis_handsfree_preview"
        private const val NOTIFICATION_ID = 4840
        @Volatile var isRunning: Boolean = false
            private set
        @Volatile var lastState: String = "Prueba manos libres apagada."
            private set
    }
}
