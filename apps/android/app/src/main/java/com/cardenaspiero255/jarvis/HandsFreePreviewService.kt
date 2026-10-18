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
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.cardenaspiero255.jarvis.core.HandsFreePreviewPolicy
import com.cardenaspiero255.jarvis.core.AudioInputMeter
import com.cardenaspiero255.jarvis.core.OfflineSpanishSupportPolicy
import com.cardenaspiero255.jarvis.core.SpanishAvailability
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
    private val spanishPolicy = OfflineSpanishSupportPolicy()
    private var supportQueried = false
    private var selectedLanguage: String? = null
    private var installedSpanish: List<String>? = null
    private val languageAttempts = mutableSetOf<String>()
    private var languageProbeTask: Runnable? = null
    private val diagnostics by lazy {
        getSharedPreferences("jarvis_handsfree_diag_v1", Context.MODE_PRIVATE)
    }
    private val timeGate = PreviewTimeGate(60_000L)
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsInitialized = false
    private var pendingSpeech: String? = null
    private var consumed = false
    private var closed = false
    private var attempts = 0
    // Old Android ASR callbacks must never stop a newer retry session.
    private var recognizerGeneration = 0L
    private var wakeLock: PowerManager.WakeLock? = null
    private var endedNormally = false
    private var diagnosticMode = false
    private var systemRecognizerRequested = false
    private var micDiagnosticSummary: String? = null
    private var diagnosticWorker: Thread? = null
    private var ttsWatchdog: Runnable? = null
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
        val diagnostic = intent?.action == ACTION_DIAGNOSE
        val systemRequested = intent?.action == ACTION_START_SYSTEM
        if ((intent?.action != ACTION_START && !diagnostic && !systemRequested) ||
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
        // The optional system-recognizer route is authorized only by its own
        // separately explained Activity button. It may use a cloud provider.
        val systemAvailable = systemRequested && try {
            SpeechRecognizer.isRecognitionAvailable(this)
        } catch (_: RuntimeException) { false }
        val result = policy.canStart(
            explicitUserTap = true,
            micGranted = mic,
            notificationsGranted = notifications,
            offlineRecognizerAvailable = diagnostic || offline || systemAvailable
        )
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
        diagnosticMode = diagnostic
        systemRecognizerRequested = systemRequested
        record(when {
            diagnostic -> "DIAGNOSTIC_START: micrófono y TTS sin usar reconocedor."
            systemRecognizerRequested -> "ASR_SYSTEM_SELECTED: el usuario autorizó " +
                "el proveedor normal de Android, que puede usar Internet. " +
                "Esta prueba no envía órdenes a ChatGPT."
            else -> "ASR_ON_DEVICE_SELECTED: solo proveedor offline Android. " +
                "La prueba no comprueba quién está hablando."
        }, active = true)
        acquireShortWakeLock()
        main.postDelayed(timeout, 60_000L)
        prepareTts()
        if (diagnosticMode) startMicrophoneDiagnostic() else startOfflineAsr()
        return START_NOT_STICKY
    }

    private var pendingRetry: Runnable? = null

    /**
     * A short raw-audio read proves microphone capture independently of
     * Android SpeechRecognizer and its locale/ASR provider. Raw samples never
     * leave RAM; only sample count and acoustic signal levels are retained.
     */
    private fun startMicrophoneDiagnostic() {
        if (!isRunning || closed || !diagnosticMode) return
        showStatus("DIAGNOSTIC_MIC: habla durante 4 segundos y comprueba el volumen.")
        val worker = Thread({
            var input: AudioRecord? = null
            var message: String
            try {
                val minimum = AudioRecord.getMinBufferSize(
                    16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                if (minimum <= 0) throw IllegalStateException("buffer size: $minimum")
                val bufferSize = maxOf(minimum * 2, 4096)
                input = AudioRecord(
                    MediaRecorder.AudioSource.MIC, 16_000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (input.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("AudioRecord no inicializado")
                }
                input.startRecording()
                if (input.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw IllegalStateException("AudioRecord no arrancó")
                }
                val meter = AudioInputMeter()
                val samples = ShortArray(1024)
                val started = SystemClock.elapsedRealtime()
                while (isRunning && !closed && !Thread.currentThread().isInterrupted &&
                    SystemClock.elapsedRealtime() - started < 4_000L) {
                    val count = input.read(samples, 0, samples.size, AudioRecord.READ_NON_BLOCKING)
                    if (count > 0) {
                        meter.observe(samples, count)
                    } else if (count < 0) {
                        throw IllegalStateException("AudioRecord.read error $count")
                    }
                    Thread.sleep(25L)
                }
                message = meter.reading().diagnostic()
            } catch (_: SecurityException) {
                message = "MIC_PERMISSION_DENIED: Android rechazó capturar audio."
            } catch (e: RuntimeException) {
                message = "MIC_CAPTURE_ERROR: ${e.javaClass.simpleName}; " +
                    "comprueba el permiso y privacidad del micrófono."
            } catch (_: InterruptedException) {
                message = "MIC_INTERRUPTED: captura detenida."
            } finally {
                try { input?.stop() } catch (_: RuntimeException) {}
                try { input?.release() } catch (_: RuntimeException) {}
            }
            val safeMessage = message
            main.post {
                if (!isRunning || closed || !diagnosticMode || endedNormally) return@post
                micDiagnosticSummary = safeMessage
                record(safeMessage + " Comprobando salida de voz offline…", active = true)
                speakThenStop("Prueba de voz. Soy Jarvis. ¿Puedes escucharme?")
                main.postDelayed({
                    if (isRunning && !closed && diagnosticMode && !ttsReady) {
                        finish("TTS_TIMEOUT: Android no preparó una voz local disponible.")
                    }
                }, 7_000L)
            }
        }, "jarvis-microphone-diagnostic")
        diagnosticWorker = worker
        worker.start()
    }

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
            // Device-only mode remains private; never switch providers after an
            // offline failure without the user's separate explicit consent.
            val current = if (systemRecognizerRequested) {
                SpeechRecognizer.createSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            }
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
            if (systemRecognizerRequested) {
                // System recognizer chooses its configured language. Unlike the
                // on-device engine, it may contact a remote speech provider.
                selectedLanguage = null
                current.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                })
            } else discoverOfflineSpanishAndListen(current, ticket)
        } catch (_: RuntimeException) {
            recoverFromAsrError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    private fun currentTicket(ticket: Long): Boolean =
        isRunning && !closed && !consumed && recognizer != null &&
            recognizerGeneration == ticket


    private fun offlineIntent(language: String?): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            if (language != null) putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

    private fun beginLocalSpanish(
        current: SpeechRecognizer, ticket: Long, language: String
    ) {
        if (!currentTicket(ticket) ||
            !timeGate.isActiveAt(SystemClock.elapsedRealtime())) return
        selectedLanguage = language
        languageAttempts.add(language)
        record("ASR_LOCAL_$language: iniciando escucha de español offline.",
            active = true)
        try {
            current.startListening(offlineIntent(language))
        } catch (_: RuntimeException) {
            recoverFromAsrError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    /**
     * Android 14's on-device recognizer may exist without es-CL. Query installed
     * language variants; never use the default cloud recognizer or an online list.
     */
    private fun discoverOfflineSpanishAndListen(current: SpeechRecognizer, ticket: Long) {
        if (!currentTicket(ticket)) return
        if (selectedLanguage != null) {
            beginLocalSpanish(current, ticket, selectedLanguage!!)
            return
        }
        if (Build.VERSION.SDK_INT < 33 || supportQueried) {
            beginLocalSpanish(current, ticket, spanishPolicy.unverifiedFallbackLanguage())
            return
        }
        supportQueried = true
        var settled = false
        val fallback = Runnable {
            if (settled || !currentTicket(ticket)) return@Runnable
            settled = true
            languageProbeTask = null
            // Unsupported support-query is not evidence of absent Spanish.
            beginLocalSpanish(current, ticket, spanishPolicy.unverifiedFallbackLanguage())
        }
        languageProbeTask = fallback
        main.postDelayed(fallback, 2500L)
        try {
            current.checkRecognitionSupport(
                offlineIntent(null), mainExecutor, object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        if (settled || !currentTicket(ticket)) return
                        settled = true
                        main.removeCallbacks(fallback)
                        languageProbeTask = null
                        val installed = support.installedOnDeviceLanguages
                        installedSpanish = installed
                        val spanish = spanishPolicy.selectInstalled(installed)
                        if (spanish != null) {
                            beginLocalSpanish(current, ticket, spanish)
                        } else if (spanishPolicy.availability(installed,
                                support.supportedOnDeviceLanguages) ==
                            SpanishAvailability.DOWNLOAD_REQUIRED) {
                            finish("ASR_SPANISH_DOWNLOAD_REQUIRED: el motor offline " +
                                "admite español pero falta su modelo descargado.")
                        } else {
                            finish("ASR_SPANISH_NOT_SUPPORTED: este proveedor " +
                                "de reconocimiento local no anuncia español.")
                        }
                    }

                    override fun onError(error: Int) {
                        if (settled || !currentTicket(ticket)) return
                        settled = true
                        main.removeCallbacks(fallback)
                        languageProbeTask = null
                        record("ASR_SUPPORT_CHECK_$error: proveedor no confirmó idiomas; " +
                            "probando español estándar sin nube.", active = true)
                        beginLocalSpanish(current, ticket,
                            spanishPolicy.unverifiedFallbackLanguage())
                    }
                }
            )
        } catch (_: RuntimeException) {
            if (!settled && currentTicket(ticket)) {
                settled = true
                main.removeCallbacks(fallback)
                languageProbeTask = null
                beginLocalSpanish(current, ticket,
                    spanishPolicy.unverifiedFallbackLanguage())
            }
        }
    }

    private fun recoverFromAsrError(error: Int) {
        closeRecognizer()
        if (!isRunning || closed || consumed) return
        if (systemRecognizerRequested &&
            (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)) {
            finish("ASR_SYSTEM_LANGUAGE_ERROR_$error: el proveedor normal de Android " +
                "rechazó su idioma configurado. Comprueba el idioma del " +
                "reconocimiento de voz del sistema.")
            return
        }
        if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED) {
            val next = if (installedSpanish == null) {
                spanishPolicy.fallbackAfterUnsupported(
                    selectedLanguage ?: "", languageAttempts)
            } else spanishPolicy.selectInstalled(installedSpanish.orEmpty(), languageAttempts)
            if (next != null && attempts < 4 &&
                timeGate.remainingMillis(SystemClock.elapsedRealtime()) > 4000L) {
                selectedLanguage = next
                record("ASR_LANGUAGE_NOT_SUPPORTED error 12: " +
                    "reintentando otra variante española offline ($next).", active = true)
                val task = Runnable {
                    pendingRetry = null
                    if (isRunning && !closed &&
                        timeGate.isActiveAt(SystemClock.elapsedRealtime())) startOfflineAsr()
                }
                pendingRetry = task
                main.postDelayed(task, 750L)
            } else {
                finish("ASR_LANGUAGE_NOT_SUPPORTED error 12: ninguna variante " +
                    "española instalada resultó compatible con el reconocedor " +
                    "local de Android. Comprueba su proveedor de reconocimiento.")
            }
            return
        }
        if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
            finish("ASR_LANGUAGE_MODEL_NOT_INSTALLED error 13: idioma reconocido " +
                "pero falta un modelo local listo para usarse.")
            return
        }
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
        showStatus("Esperando voz local · reintento ${attempts + 1} de 6")
        record("ASR_${decision.reasonCode}: intento $attempts de 6 · " +
            "pantalla $screen. Reintentando en ${decision.delayMillis} ms.", active = true)
        pendingRetry?.let { main.removeCallbacks(it) }
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
        val prefix = micDiagnosticSummary
        record(if (diagnosticMode && prefix != null) "$prefix | $reason" else reason,
            active = false)
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
                                engine?.setAudioAttributes(AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build())
                                engine?.setVoice(voice) == TextToSpeech.SUCCESS
                            } catch (_: RuntimeException) { false }
                        }
                    }
                    ttsInitialized = true
                    val pending = pendingSpeech
                    if (pending != null) {
                        pendingSpeech = null
                        if (ttsReady) play(pending) else {
                            finish("TTS_OFFLINE_UNAVAILABLE: respuesta reconocida, " +
                                "pero falta una voz española local instalada.")
                        }
                    }
                }
            }
        } catch (_: RuntimeException) {
            ttsReady = false
            ttsInitialized = true
        }
    }

    private fun speakThenStop(text: String) {
        if (!isRunning || !timeGate.isActiveAt(SystemClock.elapsedRealtime())) {
            stopSelf()
            return
        }
        closeRecognizer()
        when {
            ttsReady -> play(text)
            ttsInitialized -> finish(
                "TTS_OFFLINE_UNAVAILABLE: Android no proporciona una voz " +
                    "española local. Instala datos de síntesis de voz en Android.")
            else -> {
                pendingSpeech = text
                showStatus("Respuesta preparada · esperando inicialización TTS offline")
            }
        }
    }

    private fun play(text: String) {
        val engine = tts ?: run {
            finish("TTS_UNAVAILABLE: motor de voz local no inicializado.")
            return
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                main.post {
                    if (isRunning && !closed) showStatus(
                        "TTS_STARTED: Android inició la síntesis; " +
                            "comprobar si el altavoz realmente se escucha.")
                }
            }
            override fun onDone(utteranceId: String?) {
                main.post {
                    ttsWatchdog?.let { main.removeCallbacks(it) }
                    ttsWatchdog = null
                    val volume = try {
                        (getSystemService(AUDIO_SERVICE) as? AudioManager)
                            ?.getStreamVolume(AudioManager.STREAM_MUSIC)
                    } catch (_: RuntimeException) { null }
                    val hint = if (volume == 0) " Volumen multimedia a cero." else ""
                    finish("TTS_DONE: Android terminó la síntesis; " +
                        "comprueba si escuchaste el audio.$hint")
                }
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
            finish("TTS_REJECTED: Android rechazó la síntesis de voz local.")
            return
        }
        val deadline = Runnable {
            if (isRunning && !closed && !endedNormally) {
                finish("TTS_NO_PLAYBACK_CALLBACK: Android no confirmó " +
                    "inicio/fin de reproducción en 10 segundos.")
            }
        }
        ttsWatchdog?.let { main.removeCallbacks(it) }
        ttsWatchdog = deadline
        main.postDelayed(deadline, 10_000L)
    }

    private fun closeRecognizer() {
        // Ignore late onError caused by cancel()/destroy() of an old recognizer.
        languageProbeTask?.let { main.removeCallbacks(it) }
        languageProbeTask = null
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
        ttsWatchdog?.let { main.removeCallbacks(it) }
        ttsWatchdog = null
        pendingRetry?.let(main::removeCallbacks)
        pendingRetry = null
        // The worker owns and releases AudioRecord; do not retain PCM data.
        diagnosticWorker?.interrupt()
        diagnosticWorker = null
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
        const val ACTION_DIAGNOSE = "com.cardenaspiero255.jarvis.HANDSFREE_AUDIO_DIAGNOSTIC"
        const val ACTION_START_SYSTEM = "com.cardenaspiero255.jarvis.HANDSFREE_SYSTEM_SPEECH"
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
