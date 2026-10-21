package com.cardenaspiero255.jarvis

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.Context
import android.content.IntentFilter
import android.os.BatteryManager
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
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
import com.cardenaspiero255.jarvis.core.LockScreenAudioEvidence
import com.cardenaspiero255.jarvis.core.LockScreenAudioState
import com.cardenaspiero255.jarvis.core.SpeakerEvidence
import com.cardenaspiero255.jarvis.core.LocalGgufPolicy
import com.cardenaspiero255.jarvis.core.OfflineVoiceAnswerPolicy
import java.time.LocalTime

/**
 * Explicit bounded listening: legacy 60-second preview or user-approved
 * 30-minute native offline session that can continue after screen lock.
 * Not boot-autostart, permanent listening or speaker authentication.
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
    private var timeGate = PreviewTimeGate(60_000L)
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
    private var nativeModeRequested = false
    private var conversationMode = false
    private var conversationTurns = 0
    private var conversationGeneration = 0L
    private var localConversationInference: LocalGgufInference? = null
    private var conversationTimeout: Runnable? = null
    private var extendedListening = false
    // Persistent evidence contains sample counts only, never voices or transcript.
    private val screenOffEvidence = LockScreenAudioEvidence()
    private val wakeLockRefresh = object : Runnable {
        override fun run() {
            if (isRunning && !closed && extendedListening) acquireShortWakeLock()
        }
    }
    private var nativeRunner: NativeSpanishSpeechRunner? = null
    private val nativeStartupTimeout = Runnable {
        finish("ASR_NATIVE_INIT_TIMEOUT: el modelo tardó demasiado en inicializarse. " +
            "Prueba otra vez con la pantalla encendida.")
    }
    private var micDiagnosticSummary: String? = null
    private var diagnosticWorker: Thread? = null
    private var ttsWatchdog: Runnable? = null
    private var ttsPlaybackTicket = 0L
    private val timeout = Runnable {
        finish(if (extendedListening) "EXTENDED_LISTENING: sesión de 30 minutos terminada." else "La prueba de 60 segundos terminó. Micrófono liberado.")
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
            // Request receipt alone is not proof that AudioRecord was released.
            val receipt = intent.getLongExtra(EXTRA_STOP_DIAGNOSTIC_REQUEST_ID, -1L)
            if (receipt > 0L && isRunning && !closed && !endedNormally) {
                diagnostics.edit().putLong("stop_ack_id", receipt).commit()
            }
            finish("Prueba detenida por el usuario. Micrófono liberado.")
            return START_NOT_STICKY
        }
        val diagnostic = intent?.action == ACTION_DIAGNOSE
        val systemRequested = intent?.action == ACTION_START_SYSTEM
        val extendedRequested = intent?.action == ACTION_START_LONG_NATIVE
        val conversationRequested = intent?.action == ACTION_START_CONVERSATION
        val longConversationRequested =
            intent?.action == ACTION_START_LONG_CONVERSATION
        val nativeRequested = intent?.action == ACTION_START_NATIVE || extendedRequested ||
            conversationRequested || longConversationRequested
        if ((intent?.action != ACTION_START && !diagnostic && !systemRequested &&
                !nativeRequested) ||
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
        extendedListening = extendedRequested || longConversationRequested
        conversationMode = conversationRequested || longConversationRequested
        val debugBuild = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (longConversationRequested && !debugBuild) {
            finish("LOCAL_VOICE_EXPERIMENT_DISABLED: requiere pruebas acústicas " +
                "reales antes de habilitar interrupción conversacional.")
            return START_NOT_STICKY
        }
        // Multi-turn local GGUF is a separate explicit UI action; no automatic
        // upgrade of the previous one-shot voice request.
        if (longConversationRequested &&
            !LocalGgufModelStore(applicationContext).hasModel()) {
            finish("LOCAL_VOICE_MODEL_MISSING: importa GGUF antes de conversación larga.")
            return START_NOT_STICKY
        }
        timeGate = PreviewTimeGate(if (extendedListening) 30 * 60_000L else 60_000L)
        val result = policy.canStart(
            explicitUserTap = true,
            micGranted = mic,
            notificationsGranted = notifications,
            offlineRecognizerAvailable = when {
                diagnostic -> true
                nativeRequested -> true // model is hash-verified inside the APK
                systemRequested -> systemAvailable
                else -> offline
            }
        )
        if (result != PreviewStart.ALLOWED) {
            finish(if (result == PreviewStart.OFFLINE_ENGINE_MISSING) {
                "Android no tiene reconocimiento local compatible. " +
                    "JARVIS no cambia a un proveedor en la nube."
            } else "Permisos de micrófono o notificaciones incompletos.")
            return START_NOT_STICKY
        }
        // For native ASR, start its 60s window AFTER first-run model expansion.
        if (!nativeRequested && !timeGate.begin(SystemClock.elapsedRealtime())) {
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
        // A short probe cannot erase the last explicit long session evidence.
        if (extendedListening) {
            screenOffEvidence.reset()
            diagnostics.edit().remove("lockscreen_off_samples")
                .remove("lockscreen_off_reports").remove("lockscreen_result")
                .remove("lockscreen_time_commands").remove("lockscreen_stop_commands")
                .putBoolean("lockscreen_last_extended", true).commit()
        }
        diagnosticMode = diagnostic
        systemRecognizerRequested = systemRequested
        nativeModeRequested = nativeRequested
        record(when {
            conversationMode -> if (extendedListening)
                "LOCAL_VOICE_MULTITURN: sesión local iniciada por pulsación, " +
                    "máximo 6 preguntas en 30 minutos; STOP en aviso."
                else "LOCAL_VOICE_CONVERSATION: sesión explícita offline; " +
                    "una pregunta para un GGUF privado, sin autoridad Android."
            nativeModeRequested -> "ASR_NATIVE_PREPARING: preparando motor español " +
                "Vosk incluido en la APK; no usa servicios de voz de Android."
            diagnostic -> "DIAGNOSTIC_START: micrófono y TTS sin usar reconocedor."
            systemRecognizerRequested -> "ASR_SYSTEM_SELECTED: el usuario autorizó " +
                "el proveedor normal de Android, que puede usar Internet. " +
                "Esta prueba no envía órdenes a ChatGPT."
            else -> "ASR_ON_DEVICE_SELECTED: solo proveedor offline Android. " +
                "La prueba no comprueba quién está hablando."
        }, active = true)
        acquireShortWakeLock()
        if (nativeModeRequested) {
            main.postDelayed(nativeStartupTimeout, 90_000L)
        } else {
            main.postDelayed(timeout, 60_000L)
        }
        prepareTts()
        when {
            nativeModeRequested -> startNativeSpanishPreview()
            diagnosticMode -> startMicrophoneDiagnostic()
            else -> startOfflineAsr()
        }
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

    /**
     * First-run model expansion runs in a worker. Audio then passes directly
     * through Vosk for a bounded 60s window, fully offline. No cloud or ASR
     * language packs from Funtouch are involved.
     */
    private fun startNativeSpanishPreview() {
        val runner = NativeSpanishSpeechRunner(
            applicationContext,
            onStatus = { message ->
                main.post {
                    if (isRunning && !closed && nativeModeRequested) showStatus(message)
                }
            },
            onAudioProgress = { sampleCount, elapsed ->
                main.post {
                    if (!isRunning || closed || !extendedListening || endedNormally) return@post
                    // Only screen-off PCM from the capture worker reaches here.
                    val previousState = screenOffEvidence.snapshot().state
                    screenOffEvidence.observe(elapsed, sampleCount, interactive = false)
                    val snapshot = screenOffEvidence.snapshot()
                    diagnostics.edit()
                        .putLong("lockscreen_off_samples", snapshot.screenOffSamples)
                        .putInt("lockscreen_off_reports", snapshot.screenOffObservations)
                        .putString("lockscreen_result", snapshot.state.name).apply()
                    if (previousState != LockScreenAudioState.SCREEN_OFF_AUDIO_OBSERVED &&
                        snapshot.state == LockScreenAudioState.SCREEN_OFF_AUDIO_OBSERVED) {
                        showStatus("LOCKSCREEN_PCM_OBSERVED: micrófono entregó muestras " +
                            "durante pantalla apagada; solo mide continuidad, no identidad.")
                    }
                }
            },
            onReady = {
                main.post {
                    if (!isRunning || closed || !nativeModeRequested || endedNormally)
                        return@post
                    main.removeCallbacks(nativeStartupTimeout)
                    if (!timeGate.begin(SystemClock.elapsedRealtime())) {
                        finish("ASR_NATIVE_CLOCK_ERROR: no se pudo iniciar el temporizador.")
                        return@post
                    }
                    // Renew bounded CPU lock after native initialization.
                    try { if (wakeLock?.isHeld == true) wakeLock?.release() }
                    catch (_: RuntimeException) {}
                    wakeLock = null
                    acquireShortWakeLock()
                    val duration = if (extendedListening) 30 * 60_000L else 60_000L
                    main.postDelayed(timeout, duration)
                    showStatus(if (conversationMode)
                        (if (extendedListening)
                            "LOCAL_VOICE_MULTITURN_READY: pregunta con «Jarvis»; " +
                                "interrumpe durante TTS con otra pregunta. " +
                                "Hasta 6 turnos; DETENER en aviso."
                         else "LOCAL_VOICE_READY: di «Jarvis» y una pregunta en español. " +
                            "Una respuesta por sesión; STOP disponible en aviso.") else
                        if (extendedListening)
                        "EXTENDED_LISTENING: micrófono local activo hasta 30 min; " +
                            "aviso visible con DETENER. Di «Jarvis, dime la hora»." else
                        "ASR_NATIVE_READY: escucha local 60 s activa. " +
                            "Di «Jarvis, dime la hora» con pantalla encendida o apagada.")
                }
            },
            onWake = { wake ->
                // Captured at recognition, not after main-thread dispatch.
                val screenOffAtRecognition = if (extendedListening) try {
                    (getSystemService(POWER_SERVICE) as? PowerManager)?.isInteractive == false
                } catch (_: RuntimeException) { false } else false
                main.post {
                    if (!isRunning || closed || !nativeModeRequested ||
                        (!extendedListening && consumed) ||
                        !timeGate.isActiveAt(SystemClock.elapsedRealtime())) return@post
                    if (screenOffAtRecognition) {
                        val key = when (wake) {
                            PreviewWake.LOCAL_TIME -> "lockscreen_time_commands"
                            PreviewWake.STOP -> "lockscreen_stop_commands"
                            else -> null
                        }
                        if (key != null) {
                            diagnostics.edit().putInt(key,
                                (diagnostics.getInt(key, 0) + 1).coerceAtMost(999)).apply()
                        }
                    }
                    if (!extendedListening) {
                        consumed = true
                        nativeRunner?.stop()
                    }
                    when (wake) {
                        PreviewWake.STOP -> finish("ASR_NATIVE_STOP_BY_VOICE: micrófono detenido por orden de voz.")
                        PreviewWake.LOCAL_TIME -> {
                            val now = LocalTime.now()
                            val answer = if (now.minute == 0)
                                "Son las " + now.hour + " en punto."
                            else "Son las " + now.hour + " y " + now.minute + "."
                            record("ASR_NATIVE_MATCHED: frase de hora reconocida " +
                                "localmente; Voice Match del propietario pendiente.", active = true)
                            speakThenStop(answer)
                        }
                        PreviewWake.LOCAL_BATTERY -> {
                            record("ASR_NATIVE_BATTERY: consulta local de batería; " +
                                "Voice Match no autoriza órdenes.", active = true)
                            speakThenStop(batteryAnswer())
                        }
                        PreviewWake.WAKE_ONLY -> {
                            if (extendedListening) {
                                showStatus("EXTENDED_LISTENING: Jarvis reconocido; " +
                                    "esperando la orden pública sin verificar identidad.")
                            } else {
                                record("ASR_NATIVE_WAKE_ONLY: palabra Jarvis reconocida, " +
                                    "prueba todavía de una sola frase.", active = true)
                                speakThenStop("Te escuché. Di: Jarvis, dime la hora.")
                            }
                        }
                        else -> finish("ASR_NATIVE_IGNORED: voz no autorizada por parser.")
                    }
                }
            },
            onFailure = { message ->
                main.post {
                    if (isRunning && !closed && nativeModeRequested && !consumed)
                        finish(message)
                }
            },
            conversationMode = conversationMode,
            onQuestion = { question ->
                main.post { processOfflineQuestion(question) }
            },
            onBargeInQuestion = { question ->
                main.post {
                    if (!conversationMode || !extendedListening ||
                        !nativeModeRequested || !isRunning || closed || endedNormally ||
                        !timeGate.isActiveAt(SystemClock.elapsedRealtime())) return@post
                    // A new question cannot inherit the prior TTS ticket or
                    // GGUF callback. It may only become untrusted local text.
                    ++ttsPlaybackTicket
                    try { tts?.stop() } catch (_: RuntimeException) {}
                    ttsWatchdog?.let { main.removeCallbacks(it) }
                    ttsWatchdog = null
                    pendingSpeech = null
                    nativeRunner?.setResponsePlayback(true)
                    showStatus("LOCAL_VOICE_BARGEIN_QUESTION: respuesta anterior " +
                        "interrumpida; procesando una nueva pregunta local.")
                    processOfflineQuestion(question)
                }
            },
            onBargeInStop = {
                // The native worker supplies NO transcript, only the strictly
                // filtered, completed STOP event. Never route into actions.
                main.post {
                    if (extendedListening && isRunning && !closed &&
                        !endedNormally && nativeModeRequested &&
                        timeGate.isActiveAt(SystemClock.elapsedRealtime())) {
                        try { tts?.stop() } catch (_: RuntimeException) {}
                        nativeRunner?.stop()
                        finish("ASR_NATIVE_BARGEIN_STOP: voz interrumpida; " +
                            "sesión cerrada sin ejecutar comandos.")
                    }
                }
            },
            continuous = extendedListening,
            sessionDurationMillis = if (extendedListening) 30 * 60_000L else 60_000L
        )
        nativeRunner = runner
        if (!runner.start()) {
            finish("ASR_NATIVE_ENGINE_BUSY: no se pudo crear la sesión de micrófono.")
        }
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
                        PreviewWake.STOP -> {
                            consumed = true
                            finish("ASR_STOP_BY_VOICE: micrófono detenido por orden de voz.")
                        }
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
                        PreviewWake.LOCAL_BATTERY -> {
                            consumed = true
                            record("ASR_LOCAL_BATTERY: consulta de batería sin acciones.", active = true)
                            speakThenStop(batteryAnswer())
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
        ++conversationGeneration
        ++ttsPlaybackTicket
        conversationTimeout?.let { main.removeCallbacks(it) }
        conversationTimeout = null
        localConversationInference?.cancelActive()
        val prefix = micDiagnosticSummary
        record(if (diagnosticMode && prefix != null) "$prefix | $reason" else reason,
            active = false)
        stopSelf()
    }

    private fun acquireShortWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as? PowerManager ?: return
            // Never overlap consecutive timed CPU locks during extended sessions.
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "jarvis:HandsFreePreview").also { it.acquire(60_000L) }
            if (extendedListening) {
                main.removeCallbacks(wakeLockRefresh)
                main.postDelayed(wakeLockRefresh, 45_000L)
            }
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
                            finishOrResume("TTS_OFFLINE_UNAVAILABLE: respuesta reconocida, " +
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

    private fun finishOrResume(reason: String) {
        if (extendedListening && isRunning && !closed && !endedNormally) {
            // Guard both speaker tail-echo and stale callbacks from a prior
            // TTS turn; a new ticket may not be unmuted by an old timer.
            val ticket = ttsPlaybackTicket
            main.postDelayed({
                if (isRunning && !closed && extendedListening &&
                    !endedNormally && ttsPlaybackTicket == ticket) {
                    nativeRunner?.setResponsePlayback(false)
                }
            }, 350L)
            showStatus("EXTENDED_LISTENING: $reason. Micrófono sigue disponible; DETENER en aviso.")
        } else finish(reason)
    }

    /** System sticky battery broadcast; no permission or hardware mutation. */
    /**
     * Vosk has already released AudioRecord and decoder BEFORE this callback.
     * No text leaves this process and model output never routes into tools.
     */
    private fun processOfflineQuestion(question: String) {
        if (!conversationMode || closed || endedNormally ||
            (!extendedListening && consumed) || !isRunning ||
            !timeGate.isActiveAt(SystemClock.elapsedRealtime())) return
        if (!extendedListening) {
            // Preserve the original single-turn security contract:
            // microphone released before native GGUF inference.
            consumed = true
            nativeRunner?.stop()
            main.removeCallbacks(timeout)
            timeGate.stop()
        } else {
            if (conversationTurns >= MAX_CONVERSATION_TURNS) {
                finish("LOCAL_VOICE_TURN_LIMIT: máximo 6 preguntas por sesión.")
                return
            }
            conversationTurns++
            // Silence the normal ASR during inference, not just during TTS.
            nativeRunner?.setResponsePlayback(true)
        }
        if (!LocalGgufPolicy.validPrompt(question)) {
            finish("LOCAL_VOICE_QUERY_DENIED: transcripción no permitida.")
            return
        }
        val modelStore = LocalGgufModelStore(applicationContext)
        if (!modelStore.hasModel()) {
            finish("LOCAL_VOICE_MODEL_MISSING: importa primero un GGUF privado.")
            return
        }
        // A session keeps one worker and its verified model lease. The
        // completed turn has already released its run slot before callback.
        // Avoid creating overlapping JNI executors on every interruption.
        val engine = if (extendedListening) {
            localConversationInference ?: LocalGgufInference(modelStore).also {
                localConversationInference = it
            }
        } else {
            localConversationInference?.close()
            LocalGgufInference(modelStore).also { localConversationInference = it }
        }
        val ticket = ++conversationGeneration
        showStatus(if (extendedListening)
            "LOCAL_VOICE_LLM_THINKING: micrófono activo pero aislado " +
                "de órdenes mientras GGUF responde sin Internet."
            else "LOCAL_VOICE_LLM_THINKING: micrófono cerrado; " +
                "respondiendo sin Internet. Puedes detener desde el aviso.")
        val watchdog = Runnable {
            if (isRunning && !closed && !endedNormally && conversationMode &&
                conversationGeneration == ticket) {
                engine.cancelActive()
                finish("LOCAL_VOICE_LLM_TIMEOUT: generación cancelada sin respuesta.")
            }
        }
        conversationTimeout = watchdog
        main.postDelayed(watchdog, 90_000L)
        val accepted = engine.ask(question) { response ->
            if (!isRunning || closed || endedNormally || !conversationMode ||
                conversationGeneration != ticket ||
                conversationTimeout !== watchdog) return@ask
            main.removeCallbacks(watchdog)
            conversationTimeout = null
            val safeReply = OfflineVoiceAnswerPolicy.forTts(response)
            if (safeReply == null) {
                finish("LOCAL_VOICE_LLM_UNAVAILABLE: modelo no preparado o respuesta inválida.")
            } else {
                showStatus("LOCAL_VOICE_TTS_READY: respuesta generada en el teléfono; " +
                    "no constituye una acción ejecutada.")
                speakThenStop(safeReply)
            }
        }
        if (!accepted) {
            finish("LOCAL_VOICE_LLM_BUSY: generación no disponible.")
        }
    }

    private fun batteryAnswer(): String {
        val state = try {
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: RuntimeException) { null }
        val level = state?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = state?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (scale <= 0 || level < 0 || level > scale) {
            return "No pude obtener un nivel de batería válido."
        }
        val percent = ((level.toLong() * 100L) / scale).toInt()
        return "La batería está al $percent por ciento."
    }

    private fun speakThenStop(text: String) {
        // The one-turn conversation closes its microphone and original 60 s
        // listening window BEFORE local model inference starts.
        if (!isRunning || (!conversationMode &&
                !timeGate.isActiveAt(SystemClock.elapsedRealtime()))) {
            finish("Sesión terminada; micrófono liberado.")
            return
        }
        if (!extendedListening) closeRecognizer()
        when {
            ttsReady -> play(text)
            ttsInitialized -> finishOrResume(
                "TTS_OFFLINE_UNAVAILABLE: Android no proporciona una voz " +
                    "española local. Instala datos de síntesis de voz en Android.")
            else -> {
                pendingSpeech = text
                showStatus("Respuesta preparada · esperando inicialización TTS offline")
                if (extendedListening) {
                    val guard = Runnable {
                        if (pendingSpeech != null && isRunning && !closed) {
                            pendingSpeech = null
                            finishOrResume("TTS_INIT_TIMEOUT: el motor local no respondió.")
                        }
                    }
                    ttsWatchdog?.let { main.removeCallbacks(it) }
                    ttsWatchdog = guard
                    main.postDelayed(guard, 7_000L)
                }
            }
        }
    }

    private fun play(text: String) {
        val engine = tts ?: run {
            finishOrResume("TTS_UNAVAILABLE: motor de voz local no inicializado.")
            return
        }
        // Native runner already muted normal commands before dispatch.
        // TTS text is kept in memory only to suppress its own ASR echo.
        val ticket = ++ttsPlaybackTicket
        if (extendedListening && nativeModeRequested) {
            nativeRunner?.setResponsePlayback(true, text)
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                main.post {
                    if (utteranceId != "jarvis-preview-$ticket" ||
                        ttsPlaybackTicket != ticket || endedNormally) return@post
                    if (isRunning && !closed) showStatus(
                        "TTS_STARTED: Android inició la síntesis; " +
                            "comprobar si el altavoz realmente se escucha.")
                }
            }
            override fun onDone(utteranceId: String?) {
                main.post {
                    if (utteranceId != "jarvis-preview-$ticket" ||
                        ttsPlaybackTicket != ticket || endedNormally) return@post
                    ttsWatchdog?.let { main.removeCallbacks(it) }
                    ttsWatchdog = null
                    val volume = try {
                        (getSystemService(AUDIO_SERVICE) as? AudioManager)
                            ?.getStreamVolume(AudioManager.STREAM_MUSIC)
                    } catch (_: RuntimeException) { null }
                    val hint = if (volume == 0) " Volumen multimedia a cero." else ""
                    finishOrResume("TTS_DONE: Android terminó la síntesis; " +
                        "comprueba si escuchaste el audio.$hint")
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post {
                    if (utteranceId == "jarvis-preview-$ticket" &&
                        ttsPlaybackTicket == ticket && !endedNormally) {
                        finishOrResume("Error TTS: no se pudo reproducir la voz local.")
                    }
                }
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                main.post {
                    if (utteranceId == "jarvis-preview-$ticket" &&
                        ttsPlaybackTicket == ticket && !endedNormally) {
                        finishOrResume("Error TTS ($errorCode): voz local no disponible.")
                    }
                }
            }
        })
        showStatus("JARVIS respondiendo localmente…")
        val result = try {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-preview-$ticket")
        } catch (_: RuntimeException) { TextToSpeech.ERROR }
        if (result != TextToSpeech.SUCCESS) {
            finishOrResume("TTS_REJECTED: Android rechazó la síntesis de voz local.")
            return
        }
        val deadline = Runnable {
            if (isRunning && !closed && !endedNormally &&
                ttsPlaybackTicket == ticket) {
                finishOrResume("TTS_NO_PLAYBACK_CALLBACK: Android no confirmó " +
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
            .setContentTitle(if (conversationMode) "JARVIS · conversación local" else
                if (extendedListening) "JARVIS · escucha 30 min" else
                "JARVIS · prueba manos libres")
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
        main.removeCallbacks(wakeLockRefresh)
        main.removeCallbacks(nativeStartupTimeout)
        conversationTimeout?.let { main.removeCallbacks(it) }
        conversationTimeout = null
        localConversationInference?.close()
        localConversationInference = null
        nativeRunner?.stop()
        nativeRunner = null
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
        const val ACTION_START_NATIVE = "com.cardenaspiero255.jarvis.HANDSFREE_VOSK_NATIVE"
        const val ACTION_START_CONVERSATION =
            "com.cardenaspiero255.jarvis.HANDSFREE_LOCAL_GGUF_CONVERSATION"
        const val ACTION_START_LONG_CONVERSATION =
            "com.cardenaspiero255.jarvis.HANDSFREE_LOCAL_GGUF_MULTITURN"
        const val MAX_CONVERSATION_TURNS = 6
        const val ACTION_START_LONG_NATIVE = "com.cardenaspiero255.jarvis.HANDSFREE_LONG_NATIVE"
        const val ACTION_DIAGNOSE = "com.cardenaspiero255.jarvis.HANDSFREE_AUDIO_DIAGNOSTIC"
        const val ACTION_START_SYSTEM = "com.cardenaspiero255.jarvis.HANDSFREE_SYSTEM_SPEECH"
        const val ACTION_STOP = "com.cardenaspiero255.jarvis.HANDSFREE_STOP"
        const val EXTRA_STOP_DIAGNOSTIC_REQUEST_ID = "jarvis.handsfree.stop_receipt_request_id"
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
            val safeState = prefs.getString("lockscreen_result", null)
            val independent = if (safeState == LockScreenAudioState.SCREEN_OFF_AUDIO_OBSERVED.name)
                " | LOCKSCREEN_PCM_OBSERVED: " +
                    prefs.getLong("lockscreen_off_samples", 0L) +
                    " muestras (sin audio almacenado)" else ""
            return PreviewDiagnosticSummary.visible(
                prefs.getBoolean("active", false), isRunning, previous) + independent
        }
    }
}
