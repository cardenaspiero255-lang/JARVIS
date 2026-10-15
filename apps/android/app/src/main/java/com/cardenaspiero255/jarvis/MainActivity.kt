package com.cardenaspiero255.jarvis

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.widget.EditText
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

import com.cardenaspiero255.jarvis.core.LocalDialogue
import com.cardenaspiero255.jarvis.core.GameHubLaunchPolicy
import com.cardenaspiero255.jarvis.core.LocalCapabilityDiagnostics
import com.cardenaspiero255.jarvis.core.LocalCapabilitySnapshot
import com.cardenaspiero255.jarvis.core.ReadoutRedactionPolicy
import com.cardenaspiero255.jarvis.core.SensitiveReadout
import com.cardenaspiero255.jarvis.core.ScreenCapturePrivacyGate
import com.cardenaspiero255.jarvis.core.SessionLockChannel
import com.cardenaspiero255.jarvis.core.SessionLockCoordinator
import com.cardenaspiero255.jarvis.core.DeviceHealthFormatter
import com.cardenaspiero255.jarvis.core.GameHubPreflightGate
import com.cardenaspiero255.jarvis.core.GameReadinessLevel
import com.cardenaspiero255.jarvis.core.GameReadinessAdvisor
import com.cardenaspiero255.jarvis.core.GamingPreference
import com.cardenaspiero255.jarvis.core.DeviceActionPolicy
import com.cardenaspiero255.jarvis.core.HandsStatus
import com.cardenaspiero255.jarvis.core.DeviceOperation
import com.cardenaspiero255.jarvis.core.VoiceAction
import com.cardenaspiero255.jarvis.core.VoiceCommandParser
import com.cardenaspiero255.jarvis.core.VoiceMathSession
import com.cardenaspiero255.jarvis.core.VoiceRecognitionRequestGate
import com.cardenaspiero255.jarvis.core.VoiceFaqRouter
import com.cardenaspiero255.jarvis.core.SpokenMessage
import com.cardenaspiero255.jarvis.core.VoiceStyle
import com.cardenaspiero255.jarvis.core.VoiceConsentGate
import com.cardenaspiero255.jarvis.core.VoiceForegroundGate
import com.cardenaspiero255.jarvis.core.VoiceRequestedAction

/**
 * User-operated Android shell. No background microphone, screen-reading,
 * remote API or automatic device actions are enabled. HOME requires consent.
 */
class MainActivity : Activity() {
    private val policy = DeviceActionPolicy()
    private val parser = VoiceCommandParser()
    private val localDialogue = LocalDialogue()
    private val voiceConsent = VoiceConsentGate()
    private val voiceMathSession = VoiceMathSession()
    private val recognitionRequest = VoiceRecognitionRequestGate()
    private val foregroundGate = VoiceForegroundGate()
    private val capturePrivacyGate = ScreenCapturePrivacyGate()
    private val capturePrivacyHandler = Handler(Looper.getMainLooper())
    private val capturePrivacyExpiry = Runnable { enforceCapturePrivacy() }
    private var gamingPreference = GamingPreference.BALANCED
    private val gameHubPreflight = GameHubPreflightGate()
    private lateinit var statusText: TextView
    private lateinit var spokenReplyEngine: SpokenReplyEngine
    private lateinit var visionRecognizer: VisionRecognizer
    private lateinit var handsStatusText: TextView
    private lateinit var privateMemory: PrivateConversationStore
    private lateinit var chatLog: TextView
    private lateinit var chatInput: EditText
    private lateinit var healthStatusText: TextView
    private lateinit var capabilityStatusText: TextView
    private lateinit var gameReadinessText: TextView
    private var lastChatReply: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Protect chat, encrypted-memory controls and system-task thumbnails before
        // creating any UI; a screenshot exception always requires a separate tap.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
            setBackgroundColor(Color.rgb(10, 10, 15))
        }
        val heading = label("JARVIS OMEGA", 30f, Color.rgb(239, 43, 54)).apply {
            typeface = Typeface.DEFAULT_BOLD
        }
        layout.addView(heading)
        layout.addView(label("OBJETIVO SSS+ | PROTOTIPO ANDROID", 12f,
            Color.rgb(163, 165, 176)))
        layout.addView(label("SIN CERTIFICACIÓN — validación física pendiente", 12f,
            Color.rgb(163, 165, 176)))
        layout.addView(label("Modo observador", 22f, Color.WHITE).apply {
            setPadding(0, dp(36), 0, dp(10))
            typeface = Typeface.DEFAULT_BOLD
        })
        statusText = label(
            "JARVIS está iniciado. Las acciones sensibles permanecen desactivadas.",
            17f, Color.rgb(211, 213, 220)
        )
        layout.addView(statusText)
        layout.addView(primaryButton("BLOQUEAR SESIÓN — PRIVACIDAD") {
            lockSessionByUserRequest()
        })
        layout.addView(label(
            "Pantalla protegida contra capturas por defecto. Si necesitas compartir " +
                "una imagen para depuración, autoriza capturas durante 60 segundos. " +
                "Durante ese plazo Android u otra app podrían capturar información visible.",
            12f, Color.rgb(164, 166, 174)
        ))
        layout.addView(primaryButton("PERMITIR CAPTURAS 60 S — DEPURACIÓN") {
            val allowed = capturePrivacyGate.allowForDebugByTap(SystemClock.elapsedRealtime())
            if (allowed) {
                enforceCapturePrivacy()
                capturePrivacyHandler.removeCallbacks(capturePrivacyExpiry)
                capturePrivacyHandler.postDelayed(capturePrivacyExpiry, 60_000L)
                statusText.text = "Capturas autorizadas temporalmente por 60 s. " +
                    "BLOQUEAR SESIÓN o salir de JARVIS revoca el permiso."
            } else {
                enforceCapturePrivacy()
                statusText.text = "Capturas ya autorizadas, o solicitud rechazada."
            }
        })
        layout.addView(primaryButton("PROTEGER PANTALLA AHORA") {
            revokeCaptureException()
            statusText.text = "Pantalla protegida contra capturas y vista previa de recientes."
        })
        spokenReplyEngine = SpokenReplyEngine(this) { statusText.text = it }
        visionRecognizer = VisionRecognizer(this) { statusText.text = it }
        privateMemory = PrivateConversationStore(this)
        layout.addView(label(
            "Puedes consultar el estado mediante voz o abrir los ajustes de accesibilidad. " +
                "JARVIS solo permite HOME y abrir GameHub con permiso; no navega otras interfaces.",
            14f, Color.rgb(164, 166, 174)
        ).apply { setPadding(0, dp(24), 0, dp(16)) })

        layout.addView(label("OMEGA CHAT — CONVERSACIÓN LOCAL", 20f, Color.rgb(239, 43, 54)).apply {
            setPadding(0, dp(20), 0, dp(8))
            typeface = Typeface.DEFAULT_BOLD
        })
        layout.addView(label(
            "Chat local en español con calculadora y continuidad de resultados. Sin IA general. " +
                "La memoria en disco está desactivada hasta que la autorices.",
            13f, Color.rgb(164, 166, 174)
        ))
        chatLog = label("Sin mensajes guardados en esta sesión.", 14f, Color.WHITE)
        layout.addView(chatLog)
        chatInput = EditText(this).apply {
            hint = "Escribe un mensaje para JARVIS"
            setHintTextColor(Color.rgb(150, 150, 160))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 3
        }
        layout.addView(chatInput)
        layout.addView(primaryButton("ENVIAR MENSAJE") {
            val message = chatInput.text.toString()
            if (message.isBlank()) {
                statusText.text = "Escribe un mensaje antes de enviarlo."
            } else {
                val answer = localDialogue.reply(message)
                lastChatReply = answer
                chatInput.text.clear()
                chatLog.text = localDialogue.history().takeLast(8).joinToString("\n")
                statusText.text = answer
                if (privateMemory.isEnabled()) {
                    try {
                        if (!privateMemory.save(localDialogue.history().joinToString("\n"))) {
                            statusText.text = "No se pudo guardar el historial cifrado."
                        }
                    } catch (_: Exception) {
                        privateMemory.stop()
                        statusText.text = "La memoria cifrada no está disponible; no se guardó."
                    }
                }
            }
        })
        layout.addView(primaryButton("LEER ÚLTIMA RESPUESTA DEL CHAT") {
            val answer = lastChatReply
            if (answer == null) {
                statusText.text = "Primero genera una respuesta local en esta sesión."
            } else spokenReplyEngine.speakChatByTap(answer)
        })
        layout.addView(primaryButton("ACTIVAR MEMORIA PRIVADA CIFRADA") {
            try {
                if (privateMemory.enableByTap()) {
                    val saved = privateMemory.load()
                    if (saved != null) {
                        localDialogue.restore(saved)
                        lastChatReply = null // Restored rows are not trusted speech sources.
                        chatLog.text = localDialogue.history().takeLast(8).joinToString("\n")
                    }
                    statusText.text = "Memoria cifrada activada para esta sesión. Puedes borrarla."
                }
            } catch (_: Exception) {
                privateMemory.stop()
                statusText.text = "No se pudo abrir la memoria privada."
            }
        })
        layout.addView(primaryButton("DETENER MEMORIA PRIVADA") {
            privateMemory.stop()
            statusText.text = "Memoria privada desactivada hasta nueva autorización."
        })
        layout.addView(primaryButton("BORRAR HISTORIAL Y CLAVE CIFRADA") {
            try {
                if (privateMemory.eraseByTap()) {
                    localDialogue.clear()
                    lastChatReply = null
                    chatLog.text = "Historial eliminado de este dispositivo."
                    statusText.text = "Historial y clave de memoria eliminados."
                } else statusText.text = "Android no confirmó el borrado del historial."
            } catch (_: Exception) {
                statusText.text = "No se pudo confirmar el borrado. Reinténtalo."
            }
        })

        layout.addView(label("OMEGA HANDS — CONTROL LIMITADO", 20f, Color.rgb(239, 43, 54)).apply {
            setPadding(0, dp(20), 0, dp(8))
            typeface = Typeface.DEFAULT_BOLD
        })
        handsStatusText = label(handsStatus(), 15f, Color.rgb(211, 213, 220))
        layout.addView(handsStatusText)
        layout.addView(label(
            "Opcional: habilita el servicio desde los ajustes de Android. " +
                "Después podrás autorizar solo el botón Inicio. " +
                "Detener Hands desactiva inmediatamente las órdenes de JARVIS, " +
                "sin cambiar los permisos del sistema.",
            13f, Color.rgb(164, 166, 174)
        ))
        layout.addView(primaryButton("CONSULTAR ESTADO HANDS") {
            refreshHandsStatus()
        })
        layout.addView(primaryButton("AUTORIZAR HANDS — SOLO INICIO") {
            if (!OmegaHandsService.isConnected()) {
                statusText.text = "Primero habilita OMEGA Hands en los ajustes de accesibilidad."
            } else {
                OmegaHandsService.setEmergencyStop(this, false)
                statusText.text = if (OmegaHandsService.isStopped(this)) {
                    "No se pudo autorizar Hands. Comprueba el estado del servicio."
                } else {
                    "Autorizado en esta sesión: solo Inicio mediante pulsación visible."
                }
            }
            refreshHandsStatus()
        })
        layout.addView(primaryButton("IR A INICIO (PULSACIÓN EXPLÍCITA)") {
            val result = OmegaHandsService.goHome(this, explicitTap = true)
            statusText.text = if (result) "Orden Inicio aceptada por Android." else
                "No se ejecutó. Comprueba el servicio y la parada de emergencia."
            refreshHandsStatus()
        })
        layout.addView(primaryButton("PARADA DE EMERGENCIA HANDS") {
            OmegaHandsService.setEmergencyStop(this, true)
            statusText.text = "HANDS detenido. No se ejecutarán nuevas acciones."
            refreshHandsStatus()
        })

        layout.addView(label("OMEGA DIAGNÓSTICO — SOLO LECTURA", 20f,
            Color.rgb(239, 43, 54)).apply {
            setPadding(0, dp(24), 0, dp(8))
            typeface = Typeface.DEFAULT_BOLD
        })
        capabilityStatusText = label("Sin diagnóstico. Consulta manual y privada.", 13f,
            Color.rgb(211, 213, 220))
        layout.addView(capabilityStatusText)
        layout.addView(primaryButton("DIAGNÓSTICO LOCAL DE JARVIS") {
            capabilityStatusText.text = LocalCapabilityDiagnostics.format(readCapabilities())
        })

        layout.addView(label("OMEGA PERFORMANCE — SALUD DEL TELÉFONO", 20f,
            Color.rgb(239, 43, 54)).apply {
            setPadding(0, dp(24), 0, dp(8))
            typeface = Typeface.DEFAULT_BOLD
        })
        healthStatusText = label("Sin consulta. La lectura es solo manual.", 13f,
            Color.rgb(211, 213, 220))
        layout.addView(healthStatusText)
        layout.addView(primaryButton("CONSULTAR BATERÍA, ESTADO TÉRMICO Y RAM") {
            healthStatusText.text = DeviceHealthFormatter.format(DeviceHealthReader(this).read())
        })
        layout.addView(primaryButton("LEER SALUD DEL TELÉFONO EN VOZ ALTA") {
            val reading = DeviceHealthReader(this).read()
            healthStatusText.text = DeviceHealthFormatter.format(reading)
            spokenReplyEngine.speakHealthByTap(reading)
        })
        layout.addView(label(
            "Datos locales consultados una sola vez al pulsar. El estado térmico " +
                "no es una temperatura en grados. No se guardan ni transmiten lecturas.",
            12f, Color.rgb(159, 161, 171)
        ))

        layout.addView(label("OMEGA GAME READINESS — ASESOR PARA JUGAR", 20f,
            Color.rgb(239, 43, 54)).apply {
            setPadding(0, dp(24), 0, dp(8))
            typeface = Typeface.DEFAULT_BOLD
        })
        layout.addView(label(
            "Evaluación local y manual de batería, estado térmico y memoria antes " +
                "de jugar. Solo recomendaciones: no modifica el hardware ni inicia juegos.",
            13f, Color.rgb(164, 166, 174)
        ))
        gameReadinessText = label(
            "Elige un perfil y pulsa EVALUAR ANTES DE JUGAR.",
            13f, Color.rgb(211, 213, 220)
        )
        layout.addView(gameReadinessText)
        layout.addView(primaryButton("PERFIL JUEGO: EQUILIBRADO") {
            gameHubPreflight.cancel()
            gamingPreference = GamingPreference.BALANCED
            gameReadinessText.text = "Perfil equilibrado seleccionado. Evalúa de nuevo."
        })
        layout.addView(primaryButton("PERFIL JUEGO: COMPETITIVO") {
            gameHubPreflight.cancel()
            gamingPreference = GamingPreference.COMPETITIVE
            gameReadinessText.text = "Perfil competitivo seleccionado. Evalúa de nuevo."
        })
        layout.addView(primaryButton("PERFIL JUEGO: CALIDAD VISUAL") {
            gameHubPreflight.cancel()
            gamingPreference = GamingPreference.VISUAL_QUALITY
            gameReadinessText.text = "Perfil calidad visual seleccionado. Evalúa de nuevo."
        })
        layout.addView(primaryButton("EVALUAR ANTES DE JUGAR") {
            val snapshot = DeviceHealthReader(this).read()
            val result = GameReadinessAdvisor.assess(snapshot, gamingPreference)
            gameReadinessText.text = GameReadinessAdvisor.format(result)
        })

        layout.addView(primaryButton("PREPARAR APERTURA SEGURA DE GAMEHUB") {
            val latest = GameReadinessAdvisor.assess(
                DeviceHealthReader(this).read(), gamingPreference)
            gameReadinessText.text = GameReadinessAdvisor.format(latest)
            val staged = gameHubPreflight.stageByTap(
                latest.level, gamingPreference, SystemClock.elapsedRealtime())
            statusText.text = if (staged) {
                "GameHub preparado con lectura local. Pulsa CONFIRMAR APERTURA " +
                    "SEGURA DE GAMEHUB en menos de 30 segundos para revalidar y abrir."
            } else {
                "No se preparó la apertura: precaución térmica crítica, datos " +
                    "insuficientes o solicitud previa aún vigente. Reevalúa manualmente."
            }
        })
        layout.addView(primaryButton("CONFIRMAR APERTURA SEGURA DE GAMEHUB") {
            val authorized = gameHubPreflight.consumeByTap(
                gamingPreference, SystemClock.elapsedRealtime())
            if (!authorized) {
                statusText.text = "Sin autorización vigente: vuelve a preparar la apertura segura."
            } else {
                // Avoid using the old snapshot for a launch decision.
                val latest = GameReadinessAdvisor.assess(
                    DeviceHealthReader(this).read(), gamingPreference)
                gameReadinessText.text = GameReadinessAdvisor.format(latest)
                if (latest.level == GameReadinessLevel.READY ||
                    latest.level == GameReadinessLevel.CAUTION
                ) {
                    val launched = GameHubLauncher(this).launchOnTap()
                    statusText.text = if (launched) {
                        "GameHub abierto con confirmación y nueva lectura del dispositivo."
                    } else {
                        "Android no pudo abrir GameHub. Verifica que esté instalado."
                    }
                } else {
                    statusText.text = "Apertura segura cancelada: el estado actual ya " +
                        "no permite confirmar el inicio asistido."
                }
            }
        })

        layout.addView(label("OMEGA VISION — OCR LOCAL", 20f, Color.rgb(239, 43, 54)).apply {
            setPadding(0, dp(24), 0, dp(8))
            typeface = Typeface.DEFAULT_BOLD
        })
        layout.addView(label(
            "Selecciona una captura o imagen para reconocer su texto en el teléfono. " +
                "JARVIS no ve tu pantalla continuamente ni convierte el texto en comandos.",
            13f, Color.rgb(164, 166, 174)
        ))
        layout.addView(primaryButton("ANALIZAR IMAGEN SELECCIONADA") { pickVisionImage() })
        layout.addView(primaryButton("ABRIR GAMEHUB ULTRA") {
            val launched = GameHubLauncher(this).launchOnTap()
            statusText.text = if (launched) "GameHub Ultra se abrió mediante Android."
                else "No se encontró GameHub Ultra instalado o Android rechazó la apertura."
        })
        layout.addView(primaryButton("HABLAR CON JARVIS") { startSpeech() })
        layout.addView(primaryButton("CONFIRMAR AJUSTES SOLICITADOS POR VOZ") {
            val confirmed = voiceConsent.consume(
                VoiceRequestedAction.ACCESSIBILITY_SETTINGS,
                SystemClock.elapsedRealtime()
            )
            if (confirmed) openAccessibilitySettings(explicitUserGesture = true)
            else statusText.text = "Sin solicitud vigente. Vuelve a pedir los ajustes por voz."
        })
        layout.addView(primaryButton("CONFIRMAR APERTURA DE GAMEHUB POR VOZ") {
            val confirmed = voiceConsent.consume(
                VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH,
                SystemClock.elapsedRealtime()
            )
            if (!confirmed) {
                statusText.text = "Sin solicitud válida para GameHub. Vuelve a pedirlo por voz."
            } else {
                val launched = GameHubLauncher(this).launchOnTap()
                statusText.text = if (launched) "GameHub Ultra se abrió tras tu confirmación."
                    else "No se pudo abrir GameHub Ultra; comprueba si está instalado."
            }
        })
        layout.addView(primaryButton("PERFIL: ESPAÑOL LOCAL") {
            spokenReplyEngine.selectStyleByTap(VoiceStyle.SPANISH)
        })
        layout.addView(primaryButton("PERFIL: ASISTENTE BRITÁNICO LOCAL") {
            spokenReplyEngine.selectStyleByTap(VoiceStyle.BRITISH_ASSISTANT)
        })
        layout.addView(primaryButton("ACTIVAR RESPUESTAS DE VOZ") {
            spokenReplyEngine.enableByTap()
        })
        layout.addView(primaryButton("LEER ESTADO EN VOZ ALTA") {
            spokenReplyEngine.speakByTap(SpokenMessage.LOCAL_STATUS)
        })
        layout.addView(primaryButton("DETENER RESPUESTA DE VOZ") {
            spokenReplyEngine.stop()
            statusText.text = "Voz detenida. No volverá a hablar sin activación explícita."
        })
        layout.addView(primaryButton("ABRIR AJUSTES DE ACCESIBILIDAD") {
            openAccessibilitySettings(explicitUserGesture = true)
        })
        layout.addView(label(
            "Privacidad: el reconocimiento de voz lo realiza la aplicación de voz de Android. " +
                "Puede requerir conexión y usar un servicio externo. " +
                "JARVIS no graba de forma continua ni guarda transcripciones. " +
                "Las respuestas TTS usan únicamente una voz local instalada en Android. " +
                "El perfil británico es un estilo de asistente, no la voz del actor de cine. " +
                "Si no se encuentra una voz compatible, instálala desde los ajustes de texto a voz.",
            12f, Color.rgb(159, 161, 171)
        ).apply { setPadding(0, dp(24), 0, 0) })

        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(layout)
        setContentView(scroll)
    }

    override fun onStart() {
        super.onStart()
        enforceCapturePrivacy()
        foregroundGate.onStart()
    }

    override fun onStop() {
        gameHubPreflight.cancel()
        revokeCaptureException()
        foregroundGate.onStop()
        clearVisibleReadouts()
        // The system recognizer may be another Activity: preserve only a short-lived
        // numeric answer while our explicitly started recognition is pending.
        if (!recognitionRequest.isPending(SystemClock.elapsedRealtime())) voiceMathSession.clear()
        if (::privateMemory.isInitialized) privateMemory.stop()
        voiceConsent.cancel()
        lastChatReply = null
        if (::visionRecognizer.isInitialized) visionRecognizer.cancel()
        if (::spokenReplyEngine.isInitialized) spokenReplyEngine.stop()
        super.onStop()
    }

    override fun onDestroy() {
        gameHubPreflight.cancel()
        revokeCaptureException()
        voiceMathSession.clear()
        recognitionRequest.cancel()
        if (::spokenReplyEngine.isInitialized) spokenReplyEngine.close()
        if (::visionRecognizer.isInitialized) visionRecognizer.close()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::handsStatusText.isInitialized) refreshHandsStatus()
    }

    /** Revoke local modules on a visible tap or a one-shot foreground speech request. */
    private fun lockSessionByUserRequest() {
        val actions: Map<SessionLockChannel, () -> Unit> = mapOf(
            SessionLockChannel.VOICE_REQUEST to {
                voiceConsent.cancel()
                voiceMathSession.clear()
                recognitionRequest.cancel()
            },
            SessionLockChannel.SPOKEN_REPLY to { spokenReplyEngine.stop() },
            SessionLockChannel.IMAGE_OCR to { visionRecognizer.cancel() },
            SessionLockChannel.HANDS to {
                OmegaHandsService.setEmergencyStop(this, true)
                check(OmegaHandsService.isStopped(this))
            },
            SessionLockChannel.PRIVATE_MEMORY to { privateMemory.stop() },
            SessionLockChannel.LOCAL_DIAGNOSTICS to { clearVisibleReadouts() },
            SessionLockChannel.GAMEHUB_PREFLIGHT to { gameHubPreflight.cancel() },
            SessionLockChannel.SCREEN_CAPTURE to {
                revokeCaptureException()
                check(window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            },
            SessionLockChannel.LOCAL_CHAT to {
                localDialogue.clear()
                lastChatReply = null
                chatInput.text.clear()
                chatLog.text = "Conversación de esta sesión eliminada de pantalla."
            }
        )
        val report = SessionLockCoordinator().lock(actions)
        if (::handsStatusText.isInitialized) refreshHandsStatus()
        statusText.text = if (report.complete) {
            "Sesión bloqueada: voz, OCR, Hands y memoria privada desactivados. " +
                "El historial cifrado en disco NO se ha borrado."
        } else {
            "Bloqueo parcial: no se pudo confirmar " +
                report.failed.joinToString() + ". Comprueba los controles."
        }
    }

    /**
     * Hide all one-shot hardware/availability evaluations when the app leaves the
     * foreground or the user invokes the privacy lock. Never erase encrypted disk
     * memory or leave an old diagnostic in the visible UI.
     */
    private fun clearVisibleReadouts() {
        if (::capabilityStatusText.isInitialized) {
            capabilityStatusText.text =
                ReadoutRedactionPolicy.redacted(SensitiveReadout.CAPABILITIES)
        }
        if (::healthStatusText.isInitialized) {
            healthStatusText.text =
                ReadoutRedactionPolicy.redacted(SensitiveReadout.DEVICE_HEALTH)
        }
        if (::gameReadinessText.isInitialized) {
            gameReadinessText.text =
                ReadoutRedactionPolicy.redacted(SensitiveReadout.GAME_READINESS)
        }
    }

    /** Reinstates Android's protection, including after timeout or app switch. */
    private fun enforceCapturePrivacy() {
        if (capturePrivacyGate.captureAllowed(SystemClock.elapsedRealtime())) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun revokeCaptureException() {
        capturePrivacyGate.revoke()
        capturePrivacyHandler.removeCallbacks(capturePrivacyExpiry)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun readCapabilities(): LocalCapabilitySnapshot {
        val gameHub = GameHubLaunchPolicy().packageName
        val launchable = try {
            packageManager.getLaunchIntentForPackage(gameHub)?.component?.packageName == gameHub
        } catch (_: RuntimeException) { false }
        val recognitionAvailable = try {
            SpeechRecognizer.isRecognitionAvailable(this)
        } catch (_: RuntimeException) { false }
        return LocalCapabilitySnapshot(
            androidApi = android.os.Build.VERSION.SDK_INT,
            gameHubLaunchable = launchable,
            handsConnected = OmegaHandsService.isConnected(),
            handsStopped = OmegaHandsService.isStopped(this),
            offlineSpeechReady = spokenReplyEngine.offlineVoiceReady(),
            speechRecognizerAvailable = recognitionAvailable,
            encryptedMemoryUnlocked = privateMemory.isEnabled()
        )
    }

    private fun handsStatus(): String = HandsStatus.present(
        OmegaHandsService.isConnected(),
        OmegaHandsService.isStopped(this)
    )

    private fun refreshHandsStatus() {
        handsStatusText.text = handsStatus()
    }

    private fun pickVisionImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, VISION_REQUEST)
        } catch (_: ActivityNotFoundException) {
            statusText.text = "No se encontró un selector de imágenes en Android."
        } catch (_: SecurityException) {
            statusText.text = "Android no permitió elegir una imagen."
        }
    }

    private fun startSpeech() {
        voiceConsent.cancel()
        if (::spokenReplyEngine.isInitialized) spokenReplyEngine.stop()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla con JARVIS")
        }
        if (!recognitionRequest.begin(SystemClock.elapsedRealtime())) {
            statusText.text = "Ya hay un reconocimiento de voz activo. Cancélalo antes de iniciar otro."
            return
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, SPEECH_REQUEST)
        } catch (_: ActivityNotFoundException) {
            recognitionRequest.cancel()
            voiceMathSession.clear()
            statusText.text = "No hay un proveedor de reconocimiento de voz disponible."
        } catch (_: SecurityException) {
            recognitionRequest.cancel()
            voiceMathSession.clear()
            statusText.text = "El reconocimiento de voz no tiene permisos suficientes."
        }
    }

    @Deprecated("Uses the platform Activity result API without additional dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VISION_REQUEST) {
            if (resultCode == RESULT_OK && foregroundGate.canAcceptRecognitionResult()) {
                val selectedUri = data?.data
                if (selectedUri != null) visionRecognizer.inspectSelectedImage(selectedUri)
            }
            return
        }
        if (requestCode != SPEECH_REQUEST) return
        val requested = recognitionRequest.consume(SystemClock.elapsedRealtime())
        if (!requested || !foregroundGate.canAcceptRecognitionResult()) {
            voiceMathSession.clear()
            voiceConsent.cancel()
            return
        }
        if (resultCode != RESULT_OK) {
            voiceMathSession.clear()
            statusText.text = "Escucha cancelada. No se ejecutó ninguna acción."
            return
        }
        val transcript = data?.getStringArrayListExtra(
            RecognizerIntent.EXTRA_RESULTS
        )?.firstOrNull()
        if (transcript.isNullOrBlank()) {
            voiceMathSession.clear()
            statusText.text = "No se reconoció ningún comando."
            return
        }
        val action = parser.parse(transcript)
        if (action != VoiceAction.UNSUPPORTED) voiceMathSession.clear()
        when (action) {
            VoiceAction.READ_STATUS -> {
                if (policy.canExecute(DeviceOperation.READ_LOCAL_STATUS,
                        explicitUserGesture = false)) {
                    statusText.text = "JARVIS operativo en modo observador. " +
                        "Integraciones remotas y automatización aún no habilitadas."
                }
            }
            VoiceAction.READ_DEVICE_HEALTH -> {
                // Voice recognition is read-only: no automatic TTS or Android changes.
                val reading = DeviceHealthReader(this).read()
                statusText.text = DeviceHealthFormatter.format(reading)
            }
            VoiceAction.READ_GAME_READINESS -> {
                // Voice only requests a one-shot, read-only advisory from Android.
                // No app launch, hardware changes, TTS or persistent telemetry.
                val reading = DeviceHealthReader(this).read()
                val report = GameReadinessAdvisor.assess(reading, gamingPreference)
                statusText.text = GameReadinessAdvisor.format(report)
            }
            VoiceAction.OPEN_ACCESSIBILITY_SETTINGS -> {
                voiceConsent.request(
                    VoiceRequestedAction.ACCESSIBILITY_SETTINGS,
                    SystemClock.elapsedRealtime()
                )
                statusText.text = "Solicitud recibida. Pulsa CONFIRMAR AJUSTES antes de 30 segundos."
            }
            VoiceAction.OPEN_GAMEHUB_ULTRA -> {
                // Voice only requests; the separate on-screen button authorizes launch.
                voiceConsent.request(
                    VoiceRequestedAction.GAMEHUB_ULTRA_LAUNCH,
                    SystemClock.elapsedRealtime()
                )
                statusText.text = "GameHub solicitado. Pulsa CONFIRMAR APERTURA " +
                    "antes de 30 segundos. La voz no abre apps por sí sola."
            }
            VoiceAction.STOP_SESSION -> {
                // A single-use recognition request was consumed and foreground-checked
                // above. Emergency stop only revokes capabilities; it grants none.
                lockSessionByUserRequest()
            }
            VoiceAction.UNSUPPORTED -> {
                // Read-only math fallback: no Android actions or saved transcript.
                val localAnswer = voiceMathSession.answer(transcript,
                    SystemClock.elapsedRealtime())
                    ?: VoiceFaqRouter.answer(transcript)
                if (localAnswer != null) lastChatReply = localAnswer
                statusText.text = localAnswer ?:
                    "Orden no disponible. No se realizó ninguna acción."
            }
        }
    }

    private fun openAccessibilitySettings(explicitUserGesture: Boolean) {
        if (!policy.canExecute(DeviceOperation.OPEN_ACCESSIBILITY_SETTINGS,
                explicitUserGesture)) {
            statusText.text = "Acción rechazada por la política de seguridad."
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            statusText.text = "Se abrieron los ajustes de Android. " +
                "Activa OMEGA Hands solo si aceptas su alcance limitado."
        } catch (_: ActivityNotFoundException) {
            statusText.text = "Los ajustes de accesibilidad no están disponibles."
        } catch (_: SecurityException) {
            statusText.text = "Android no permite abrir los ajustes."
        }
    }

    private fun label(value: String, size: Float, color: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
        }

    private fun primaryButton(value: String, action: () -> Unit): Button =
        Button(this).apply {
            text = value
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.rgb(179, 18, 32))
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val SPEECH_REQUEST = 7001
        const val VISION_REQUEST = 7002
    }
}
