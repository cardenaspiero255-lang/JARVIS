package com.cardenaspiero255.jarvis

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.cardenaspiero255.jarvis.core.HandsFreePreviewPolicy
import com.cardenaspiero255.jarvis.core.PreviewAsrRecoveryPolicy
import com.cardenaspiero255.jarvis.core.PreviewAsrDecision
import com.cardenaspiero255.jarvis.core.PreviewDiagnosticSummary
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
    private val recoveryPolicy = PreviewAsrRecoveryPolicy()
    private val diagnostics by lazy {
        getSharedPreferences("jarvis_handsfree_diag_v1", Context.MODE_PRIVATE)
    }
    private val timeGate = PreviewTimeGate(60_000L)
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var consumed = false
    private var closed = false
    private var attempts = 0
    // Old Android ASR callbacks must never stop a newer retry session.
    private var recognizerGeneration = 0L
    private var wakeLock: PowerManager.WakeLock? = null
    private var endedNormally = false
    private val timeout = Runnable {
        finish("La prueba de 60 segundos terminó. Micrófono liberado.")
    }

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
            finish("Prueba detenida por el usuario. Micrófono liberado.")
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
            finish(if (result == PreviewStart.OFFLINE_ENGINE_MISSING) {
                "Android no tiene reconocimiento local compatible. " +
                    "JARVIS no cambia a un proveedor en la nube."
            } else "Permisos de micrófono o notificaciones incompletos.")
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
            finish("Android no permitió iniciar el micrófono foreground. " +
                "Revisa permisos y Funtouch OS.")
            return START_NOT_STICKY
        }
        isRunning = true
        record("Micrófono de JARVIS activo hasta 60 s. " +
            "Esta prueba NO comprueba quién habla.", active = true)
        // Keep CPU awake ONLY for this explicitly authorized 60s hardware test.
        acquireShortWakeLock()
        main.postDelayed(timeout, 60_000L)
        prepareTts()
        startOfflineAsr()
        return START_NOT_STICKY
    }

    private var pendingRetry: Runnable? = null

    private fun startOfflineAsr() {
        if (!isRunning || closed || !timeGate.isActiveAt(SystemClock.elapsedRealtime())) {
            finish("Tiempo de la prueba terminado antes de escuchar una orden.")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            finish("PERMISSION_REVOKED: Android retiró acceso al micrófono.")
            return
        }
        attempts++
        val ticket = ++recognizerGeneration
        try {
            val current = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            recognizer = current
            current.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (currentTicket(ticket)) showStatus("Escucha local activa. Intento $attempts de 6.")
                }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    if (currentTicket(ticket)) showStatus("Procesando una frase local…")
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onError(error: Int) {
                    if (!currentTicket(ticket)) return
                    recoverFromAsrError(error)
                }

                override fun onResults(results: Bundle?) {
                    if (!currentTicket(ticket)) return
                    if (!timeGate.isActiveAt(SystemClock.elapsedRealtime())) {
                        finish("La ventana de 60 segundos expiró.")
                        return
                    }
                    val transcript = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )?.firstOrNull().orEmpty()
                    closeRecognizer()
                    when (policy.classify(transcript, SpeakerEvidence.UNAVAILABLE)) {
                        PreviewWake.LOCAL_TIME -> {
                            consumed = true
                            val now = LocalTime.now()
                            val answer = if (now.minute == 0)
                                "Son las " + now.hour + " en punto."
                            else "Son las " + now.hour + " y " + now.minute + "."
                            record("Se reconoció la petición de hora. " +
                                "Voice Match pendiente.", active = true)
                            speakThenStop(answer)
                        }
                        PreviewWake.WAKE_ONLY -> {
                            consumed = true
                            record("Se reconoció Jarvis; esta versión requiere " +
                                "la orden en la misma frase.", active = true)
                            speakThenStop("Te escuché. Di: Jarvis dime la hora.")
                        }
                        PreviewWake.IGNORED ->
                            recoverFromAsrError(SpeechRecognizer.ERROR_NO_MATCH)
                        PreviewWake.BLOCKED_IDENTITY ->
                            finish("La petición fue bloqueada: voz o identidad no confiable.")
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
            recoverFromAsrError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    private fun currentTicket(ticket: Long): Boolean =
        isRunning && !closed && !consumed && recognizer != null &&
            recognizerGeneration == ticket

    private fun recoverFromAsrError(error: Int) {
        closeRecognizer()
        if (!isRunning || closed || consumed) return
        val decision = recoveryPolicy.afterError(
            error, attempts, timeGate.remainingMillis(SystemClock.elapsedRealtime())
        )
        val screen = try {
            if ((getSystemService(POWER_SERVICE) as? PowerManager)?.isInteractive == false)
                "apagada" else "encendida"
        } catch (_: RuntimeException) { "desconocida" }
        if (decision.decision == PreviewAsrDecision.STOP) {
            finish("ASR_${decision.reasonCode} · error $error · pantalla $screen. " +
                "Proveedor local finalizó antes de responder; micrófono detenido.")
            return
        }
        record("ASR_${decision.reasonCode}: intento $attempts de 6 · " +
            "pantalla $screen. Reintentando en ${decision.delayMillis} ms.", active = true)
        showStatus("Esperando voz local · reintento ${attempts + 1} de 6")
        pendingRetry?.let(main::removeCallbacks)
        val task = Runnable {
            pendingRetry = null
            if (isRunning && !closed && !consumed &&
                timeGate.isActiveAt(SystemClock.elapsedRealtime())) {
                startOfflineAsr()
            }
        }
        pendingRetry = task
        main.postDelayed(task, decision.delayMillis)
    }

    private fun record(message: String, active: Boolean) {
        lastState = message.take(460)
        // Status-only; never persist recognized text, audio, voice features or credentials.
        diagnostics.edit().putString("status", lastState)
            .putBoolean("active", active).commit()
    }

    private fun finish(reason: String) {
        if (endedNormally || closed) return
        endedNormally = true
        record(reason, active = false)
        stopSelf()
    }

    private fun acquireShortWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "jarvis:HandsFreePreview").also { it.acquire(60_000L) }
        } catch (_: RuntimeException) {
            record("Prueba activa; Android no concedió bloqueo CPU temporal.", active = true)
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
                                engine?.setVoice(voice) == TextToSpeech.SUCCESS
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
            override fun onDone(utteranceId: String?) {
                main.post { finish("Respuesta de hora reproducida. Micrófono liberado.") }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { finish("Error TTS: no se pudo reproducir la voz local.") }
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                main.post { finish("Error TTS ($errorCode): voz local no disponible.") }
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
        // Ignore late onError caused by cancel()/destroy() of an old recognizer.
        recognizerGeneration++
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
        if (isRunning && !closed) {
            record(summary, active = true)
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, notification(summary))
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        finish("JARVIS fue retirado de aplicaciones recientes. Micrófono liberado.")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (!endedNormally) record(
            "El servicio terminó inesperadamente sin resultado de voz; " +
                "no se puede atribuir la causa al fabricante sin registros.",
            active = false)
        closed = true
        isRunning = false
        consumed = true
        timeGate.stop()
        pendingSpeech = null
        main.removeCallbacks(timeout)
        pendingRetry?.let(main::removeCallbacks)
        pendingRetry = null
        closeRecognizer()
        try { tts?.stop() } catch (_: RuntimeException) {}
        try { tts?.shutdown() } catch (_: RuntimeException) {}
        tts = null
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: RuntimeException) {}
        wakeLock = null
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

        /** Restores last safe exit cause after OEM process death, without audio logs. */
        fun readDiagnostic(context: Context): String {
            val prefs = context.getSharedPreferences(
                "jarvis_handsfree_diag_v1", Context.MODE_PRIVATE)
            val previous = prefs.getString("status", "Prueba manos libres apagada.")
                ?: "Prueba manos libres apagada."
            return PreviewDiagnosticSummary.visible(
                prefs.getBoolean("active", false), isRunning, previous)
        }
    }
}
