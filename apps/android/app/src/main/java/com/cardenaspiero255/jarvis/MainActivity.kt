package com.cardenaspiero255.jarvis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.media.projection.MediaProjectionManager
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.ContentResolver
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import com.cardenaspiero255.jarvis.core.PrivateKnowledgePack
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.net.Uri
import com.cardenaspiero255.jarvis.core.LocalGgufPolicy
import android.text.InputType
import android.widget.EditText
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

import com.cardenaspiero255.jarvis.core.LocalDialogue
import com.cardenaspiero255.jarvis.core.GameHubLaunchPolicy
import com.cardenaspiero255.jarvis.core.LocalCapabilityDiagnostics
import com.cardenaspiero255.jarvis.core.LocalCapabilitySnapshot
import com.cardenaspiero255.jarvis.core.ReadoutRedactionPolicy
import com.cardenaspiero255.jarvis.core.SensitiveReadout
import com.cardenaspiero255.jarvis.core.ScreenObservationReleaseGate
import com.cardenaspiero255.jarvis.core.ScreenNotificationGate
import com.cardenaspiero255.jarvis.core.ScreenConsentRequestGate
import com.cardenaspiero255.jarvis.core.ScreenCapturePrivacyGate
import com.cardenaspiero255.jarvis.core.SessionLockChannel
import com.cardenaspiero255.jarvis.core.SessionLockCoordinator
import com.cardenaspiero255.jarvis.core.DeviceHealthFormatter
import com.cardenaspiero255.jarvis.core.DeviceHealthSnapshot
import com.cardenaspiero255.jarvis.core.GameReadinessReport
import com.cardenaspiero255.jarvis.core.GameHubPreflightGate
import com.cardenaspiero255.jarvis.core.GameReadinessLevel
import com.cardenaspiero255.jarvis.core.GameReadinessAdvisor
import com.cardenaspiero255.jarvis.core.GamingPreference
import com.cardenaspiero255.jarvis.core.DeviceActionPolicy
import com.cardenaspiero255.jarvis.core.HandsStatus
import com.cardenaspiero255.jarvis.core.DeviceOperation
import com.cardenaspiero255.jarvis.core.VoiceAction
import com.cardenaspiero255.jarvis.core.VoiceCommandParser
import com.cardenaspiero255.jarvis.core.HandsFreePreviewPolicy
import com.cardenaspiero255.jarvis.core.PreviewStart
import com.cardenaspiero255.jarvis.core.OfflineRoutinePlanner
import com.cardenaspiero255.jarvis.core.VisionPickerRequestGate
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
    private val noteImportEpoch = AtomicLong(0)
    private val noteImportBusy = AtomicBoolean(false)
    private var notePickerOpen = false
    private var pendingPrivateNoteUri: Uri? = null
    private val offlineRoutinePlanner = OfflineRoutinePlanner()
    private val voiceConsent = VoiceConsentGate()
    private val voiceMathSession = VoiceMathSession()
    private val recognitionRequest = VoiceRecognitionRequestGate()
    private var foregroundSpeech: ForegroundSpeechRecognizer? = null
    private var handsFreePreviewButton: Button? = null
    private lateinit var handsFreePreviewStatus: TextView
    // Refresh on the visible Activity only. Service state remains observable and
    // recording keeps running under its separate, explicitly started foreground FGS.
    private val handsFreeUiHandler = Handler(Looper.getMainLooper())
    private val handsFreeUiRefresh = object : Runnable {
        override fun run() {
            if (::handsFreePreviewStatus.isInitialized) refreshHandsFreePreviewUi()
            handsFreeUiHandler.postDelayed(this, 800L)
        }
    }
    private lateinit var visionPickerRequest: VisionPickerRequestGate
    private lateinit var screenConsentGate: ScreenConsentRequestGate
    private var screenConsentRequested = false
    private val foregroundGate = VoiceForegroundGate()
    private val capturePrivacyGate = ScreenCapturePrivacyGate()
    private val capturePrivacyHandler = Handler(Looper.getMainLooper())
    private val capturePrivacyExpiry = Runnable { enforceCapturePrivacy() }
    private var gamingPreference = GamingPreference.BALANCED
    private val gameHubPreflight = GameHubPreflightGate()
    private lateinit var statusText: TextView
    private lateinit var omegaCore: OmegaPulseCore
    private lateinit var omegaScroll: ScrollView
    private lateinit var compactPanels: OmegaCompactPanels
    private lateinit var chatCard: LinearLayout
    private lateinit var visionCard: LinearLayout
    private lateinit var gameCard: LinearLayout
    private val scrollUiHandler = Handler(Looper.getMainLooper())
    private val scrollResumeRunnable = Runnable {
        if (::omegaCore.isInitialized) omegaCore.resumeMotion()
    }
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
    private lateinit var localGgufStore: LocalGgufModelStore
    private lateinit var localGgufInference: LocalGgufInference


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localGgufStore = LocalGgufModelStore(this)
        localGgufInference = LocalGgufInference(localGgufStore)
        visionPickerRequest = VisionPickerRequestGate(
            savedInstanceState?.getInt(VISION_PICKER_COUNTER_KEY, 0) ?: 0
        )
        screenConsentGate = ScreenConsentRequestGate(
            savedInstanceState?.getInt(SCREEN_CONSENT_COUNTER_KEY, 0) ?: 0
        )
        // Protect chat, encrypted-memory controls and system-task thumbnails before
        // creating any UI; a screenshot exception always requires a separate tap.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
            setBackgroundColor(Color.rgb(9, 11, 19))
        }
        layout.addView(OmegaUiKit.hero(this))
        // Preview is an explicit user action. HOME selection remains controlled by Android.
        layout.addView(primaryButton("PROBAR JARVIS OS — SIN CAMBIAR FUNTOUCH") {
            startActivity(Intent(this, JarvisHomeActivity::class.java))
        })
        // Offline walk-throughs inspired by open-source Android Jarvis projects.
        // This is an explicit tap; tutorial content has no command executor.
        layout.addView(primaryButton("GUÍAS JARVIS — VOZ, SHIZUKU, TERMUX") {
            startActivity(Intent(this, GuidedTutorialActivity::class.java))
        })
        omegaCore = OmegaPulseCore(this)
        layout.addView(omegaCore)
        layout.addView(label("NÚCLEO OMEGA  /  SISTEMA EN OBSERVACIÓN", 11f,
            Color.rgb(235, 112, 131)).apply {
            gravity = android.view.Gravity.CENTER
            letterSpacing = .16f
            setPadding(0, dp(0), 0, dp(18))
        })
        layout.addView(OmegaUiKit.quickActions(this,
            onChat = { if (::compactPanels.isInitialized) compactPanels.show("CHAT Y MEMORIA") },
            onVision = { if (::compactPanels.isInitialized) compactPanels.show("VISIÓN Y PANTALLA") },
            onGame = { if (::compactPanels.isInitialized) compactPanels.show("JUEGOS Y RENDIMIENTO") }
        ))
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
        statusText.background = OmegaUiKit.statusSurface(this)
        statusText.setPadding(dp(16), dp(16), dp(16), dp(16))
        statusText.minHeight = dp(76)
        layout.addView(statusText)
        layout.addView(primaryButton("BLOQUEAR SESIÓN — PRIVACIDAD") {
            lockSessionByTap()
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
        spokenReplyEngine = SpokenReplyEngine(this,
            showStatus = { statusText.text = it },
            onSpeechProgress = { omegaCore.setSpeaking(it) }
        )
        layout.addView(primaryButton("PROBAR AUDIO JARVIS") {
            spokenReplyEngine.testVoiceByTap()
        })
        layout.addView(primaryButton("CONFIGURAR VOZ EN ANDROID") {
            openTtsSettingsByTap()
        })
        layout.addView(primaryButton("HABLAR CON JARVIS") { startSpeech() })
        // Single explicit opt-in button, NOT hidden background activation.
        handsFreePreviewButton = primaryButton("PROBAR MANOS LIBRES · 60 S") {
            toggleHandsFreePreviewByTap()
        }.also { layout.addView(it) }
        layout.addView(primaryButton("CONVERSAR OFFLINE CON JARVIS · 1 PREGUNTA") {
            if (HandsFreePreviewService.isRunning) {
                stopHandsFreePreview()
            } else if (!localGgufStore.hasModel()) {
                statusText.text = "Importa primero un modelo GGUF local en OMEGA CHAT. " +
                    "No se descarga ni envía audio a Internet."
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Una pregunta por voz · completamente local")
                    .setMessage("La sesión empieza únicamente al confirmar. " +
                        "Vosk escucha hasta 60 segundos en primer plano; di " +
                        "«Jarvis, ¿qué es un procesador?». Se cerrará el micrófono " +
                        "antes de pasar el texto temporal al GGUF privado. " +
                        "Android leerá la respuesta solo si hay una voz española " +
                        "offline instalada. Sin reconocimiento del propietario, " +
                        "comandos del teléfono ni Internet. Puedes DETENER en el aviso.")
                    .setNegativeButton("CANCELAR", null)
                    .setPositiveButton("INICIAR UNA PREGUNTA") { _, _ ->
                        toggleHandsFreePreviewByTap(conversation = true)
                    }.show()
            }
        })
        layout.addView(primaryButton("CONVERSACIÓN GGUF · INTERRUMPIBLE · 30 MIN") {
            if (HandsFreePreviewService.isRunning) {
                stopHandsFreePreview()
            } else if (!localGgufStore.hasModel()) {
                statusText.text = "Importa primero un GGUF privado verificado."
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Conversación interrumpible · prueba experimental")
                    .setMessage("Solo tras pulsar INICIAR, JARVIS escuchará con Vosk " +
                        "hasta 30 minutos y un máximo de seis preguntas. " +
                        "Di «Jarvis, ¿qué es un satélite?» y, mientras responda, " +
                        "puedes interrumpir con otra pregunta iniciada por «Jarvis». " +
                        "Si Android no permite cancelación de eco (AEC), " +
                        "no se habilitarán interrupciones automáticas durante la voz. " +
                        "El micrófono y el modelo GGUF funcionan localmente; " +
                        "ninguna pregunta ejecuta acciones en el teléfono. " +
                        "Puedes detener toda la sesión desde la notificación. " +
                        "No verifica tu identidad ni certifica resistencia a replay.")
                    .setNegativeButton("CANCELAR", null)
                    .setPositiveButton("INICIAR CONVERSACIÓN") { _, _ ->
                        toggleHandsFreePreviewByTap(conversationExtended = true)
                    }.show()
            }
        })
        layout.addView(primaryButton("ACTIVAR ESCUCHA LOCAL · 30 MIN") {
            if (HandsFreePreviewService.isRunning) {
                stopHandsFreePreview()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Escucha local de hasta 30 minutos")
                    .setMessage("El micrófono se indica mediante una notificación " +
                        "que permite DETENER la sesión. Puede continuar al bloquear " +
                        "pantalla mientras Android/Funtouch no interrumpa el servicio. " +
                        "Solo órdenes públicas: la identidad no está calibrada. " +
                        "No es una activación automática tras reiniciar.")
                    .setNegativeButton("CANCELAR", null)
                    .setPositiveButton("ACTIVAR") { _, _ ->
                        toggleHandsFreePreviewByTap(extended = true)
                    }
                    .show()
            }
        })
        layout.addView(primaryButton("REGISTRAR MI VOZ · 3 MUESTRAS") {
            // Biometric enrollment is optional and requires a separate consent.
            startActivity(Intent(this, OwnerVoiceEnrollmentActivity::class.java))
        })
        layout.addView(primaryButton("DIAGNÓSTICO MICRO Y VOZ · 4 S") {
            toggleHandsFreePreviewByTap(diagnostic = true)
        })
        layout.addView(primaryButton("PROBAR RECONOCIMIENTO DEL SISTEMA · 60 S") {
            AlertDialog.Builder(this)
                .setTitle("Reconocimiento del sistema · prueba opcional")
                .setMessage("Esta alternativa usa el reconocedor de voz configurado en " +
                    "Android. Según tu proveedor puede usar Internet y enviar " +
                    "audio para transcribirlo. Solo reconocerá «Jarvis, dime la hora» " +
                    "en esta prueba. No se activará automáticamente, no guarda el audio " +
                    "en JARVIS ni envía instrucciones a ChatGPT. ¿Quieres probarlo?")
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("ACEPTAR Y PROBAR") { _, _ ->
                    toggleHandsFreePreviewByTap(systemMode = true)
                }.show()
        })
        layout.addView(label(
            "El modo de 60 s usa SOLO reconocimiento offline. " +
                "La prueba alternativa del sistema puede usar Internet: " +
                "se inicia únicamente con tu autorización explícita.",
            12f, Color.rgb(164, 166, 174)
        ))
        handsFreePreviewStatus = label(HandsFreePreviewService.readDiagnostic(this),
            12f, Color.rgb(211, 213, 220))
        layout.addView(handsFreePreviewStatus)
        layout.addView(label(
            "La escucha de 30 min requiere activación explícita y puede continuar " +
                "con pantalla bloqueada, sin garantía 24/7 ni activación al reiniciar. " +
                "MANOS LIBRES 60 S usa ahora Vosk español local integrado en esta APK, " +
                "sin depender de SpeechRecognizer, Funtouch ni Internet. " +
                "Al primer uso puede tardar en preparar el modelo. Di " +
                "«Jarvis, dime la hora». Esta prueba no identifica al propietario " +
                "ni ejecuta órdenes sensibles; termina sola o con DETENER.",
            12f, Color.rgb(164, 166, 174)
        ))
        visionRecognizer = VisionRecognizer(this) { statusText.text = it }
        privateMemory = PrivateConversationStore(this)
        RetiredCloudCredentialCleaner.purge(this)
        layout.addView(label(
            "Puedes consultar el estado mediante voz o abrir los ajustes de accesibilidad. " +
                "JARVIS solo permite HOME y abrir GameHub con permiso; no navega otras interfaces.",
            14f, Color.rgb(164, 166, 174)
        ).apply { setPadding(0, dp(24), 0, dp(16)) })

        var section: LinearLayout
        section = OmegaUiKit.createSection(this, layout)
        chatCard = section
        section.addView(label("OMEGA CHAT — CONVERSACIÓN LOCAL", 20f, Color.rgb(239, 43, 54)).apply {
            setPadding(0, dp(20), 0, dp(8))
            typeface = Typeface.DEFAULT_BOLD
        })
        section.addView(label(
            "Chat local en español con calculadora y continuidad de resultados. Sin IA general. " +
                "La memoria en disco está desactivada hasta que la autorices.",
            13f, Color.rgb(164, 166, 174)
        ))
        chatLog = label("Sin mensajes guardados en esta sesión.", 14f, Color.WHITE)
        section.addView(chatLog)
        chatInput = EditText(this).apply {
            hint = "Escribe un mensaje para JARVIS"
            setHintTextColor(Color.rgb(150, 150, 160))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 3
        }
        section.addView(chatInput)
        section.addView(primaryButton("IMPORTAR NOTA LOCAL · TXT") {
            // User-selected short text only. No broad storage permissions,
            // automatic indexing, networking or file access persistence.
            if (notePickerOpen || noteImportBusy.get()) {
                statusText.text = "Otra importación de nota sigue activa."
            } else {
                notePickerOpen = true
                val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    startActivityForResult(picker, NOTE_PICKER_REQUEST)
                } catch (_: ActivityNotFoundException) {
                    notePickerOpen = false
                    statusText.text = "Android no dispone de un selector para notas TXT."
                }
            }
        })
        section.addView(label(
            "Notas TXT de hasta 64 KiB, solo en memoria mientras JARVIS está abierto. " +
                "Escribe «busca en mis notas Shizuku» para consultar fragmentos " +
                "no verificados. Nunca ejecuta instrucciones del archivo.",
            12f, Color.rgb(164, 166, 174)
        ))
        section.addView(primaryButton("ENVIAR MENSAJE") {
            val message = chatInput.text.toString()
            when {
                message.isBlank() ->
                    statusText.text = "Escribe un mensaje antes de enviarlo."
                message.equals("estado ia", ignoreCase = true) -> {
                    chatInput.text.clear()
                    statusText.text = "JARVIS funciona en modo local gratuito. " +
                        "No utiliza el gateway ni una clave API de OpenAI. " +
                        "El modelo generativo local está en preparación."
                }
                message.equals("conectar ia", ignoreCase = true) ||
                    message.trimStart().startsWith("consulta nube:", ignoreCase = true) -> {
                    chatInput.text.clear()
                    statusText.text = "La conexión de pago fue retirada. " +
                        "JARVIS utiliza solo conocimientos y funciones locales. " +
                        "Ninguna consulta se envió a OpenAI."
                }
                message.equals("desconectar ia", ignoreCase = true) -> {
                    chatInput.text.clear()
                    statusText.text = if (RetiredCloudCredentialCleaner.purge(this))
                        "El antiguo token de nube quedó borrado de este dispositivo."
                    else "No se pudo confirmar la limpieza de todas las credenciales antiguas."
                }
                else -> {
                    val answer = localDialogue.reply(message)
                    lastChatReply = answer
                    chatInput.text.clear()
                    chatLog.text = localDialogue.history().takeLast(8).joinToString("\n")
                    statusText.text = answer
                    if (privateMemory.isEnabled()) {
                        try {
                            if (!privateMemory.save(localDialogue.snapshotForStorage())) {
                                statusText.text = "No se pudo guardar el historial cifrado."
                            }
                        } catch (_: Exception) {
                            privateMemory.stop()
                            statusText.text = "La memoria cifrada no está disponible; no se guardó."
                        }
                    }
                }
            }
        })
        section.addView(label(
            "IA GENERATIVA LOCAL OPCIONAL · GGUF. Sin conexión ni comandos " +
                "automáticos. Importa un modelo compatible que tengas derecho " +
                "a usar; puede ocupar hasta 1,6 GB y consumir bastante RAM. " +
                "Si no instalas ninguno, el chat de siempre continúa funcionando.",
            12f, Color.rgb(164, 166, 174)
        ))
        section.addView(primaryButton("IMPORTAR MODELO GGUF · ARCHIVO LOCAL") {
            AlertDialog.Builder(this)
                .setTitle("Importar un modelo GGUF privado")
                .setMessage("La importación es opcional. Comprueba los derechos " +
                    "de uso del modelo. Se copiará al almacenamiento privado " +
                    "de JARVIS, se calculará SHA-256 y nunca se subirá. " +
                    "El archivo puede ser pesado. ¿Quieres seleccionarlo?")
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("SELECCIONAR") { _, _ ->
                    val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        startActivityForResult(picker, GGUF_PICKER_REQUEST)
                    } catch (_: ActivityNotFoundException) {
                        statusText.text = "Android no dispone de un selector de archivos."
                    }
                }.show()
        })
        section.addView(primaryButton("PREGUNTAR AL MODELO LOCAL · SIN INTERNET") {
            val question = chatInput.text.toString().trim()
            if (!localGgufStore.hasModel()) {
                statusText.text = "Importa primero un GGUF compatible."
            } else if (!LocalGgufPolicy.validPrompt(question)) {
                statusText.text = "Introduce una pregunta normal y breve, " +
                    "sin órdenes para controlar el teléfono."
            } else if (localGgufInference.ask(question) { answer ->
                    if (isFinishing || isDestroyed) return@ask
                    // Generated output is ephemeral, untrusted data only.
                    lastChatReply = answer
                    chatLog.text = "JARVIS (modelo privado, no verificado): " + answer
                    statusText.text = "Respuesta local generada. Ninguna acción se ejecutó."
                }) {
                chatInput.text.clear()
                statusText.text = "Generando en este dispositivo, sin Internet…"
            } else {
                statusText.text = "Ya hay otra consulta activa o la entrada es inválida."
            }
        })
        section.addView(primaryButton("ELIMINAR MODELO LOCAL · LIBERAR ESPACIO") {
            AlertDialog.Builder(this)
                .setTitle("¿Eliminar el modelo de JARVIS?")
                .setMessage("Se borrará la copia privada GGUF del dispositivo. " +
                    "No se eliminará el archivo original que seleccionaste.")
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("ELIMINAR") { _, _ ->
                    statusText.text = "Cancelando inferencia y liberando GGUF privado…"
                    val accepted = localGgufInference.deleteModel { deleted ->
                        if (!isDestroyed) statusText.text =
                            if (deleted == true) "Modelo privado eliminado y liberado."
                            else "No se pudo eliminar completamente el modelo."
                    }
                    if (!accepted) statusText.text =
                        "Hay otra importación o borrado en curso. Inténtalo luego."
                }.show()
        })
        section.addView(primaryButton("LEER ÚLTIMA RESPUESTA DEL CHAT") {
            val answer = lastChatReply
            if (answer == null) {
                statusText.text = "Primero genera una respuesta local en esta sesión."
            } else spokenReplyEngine.speakChatByTap(answer)
        })
        section.addView(primaryButton("ACTIVAR MEMORIA PRIVADA CIFRADA") {
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
        section.addView(primaryButton("DETENER MEMORIA PRIVADA") {
            privateMemory.stop()
            statusText.text = "Memoria privada desactivada hasta nueva autorización."
        })
        section.addView(primaryButton("BORRAR HISTORIAL Y CLAVE CIFRADA") {
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

        section = OmegaUiKit.createSection(this, layout)
        val handsCard = section
        section.addView(label("OMEGA HANDS — CONTROL LIMITADO", 20f, Color.rgb(239, 43, 54)).apply {
            setPadding(0, dp(20), 0, dp(8))
            typeface = Typeface.DEFAULT_BOLD
        })
        handsStatusText = label(handsStatus(), 15f, Color.rgb(211, 213, 220))
        section.addView(handsStatusText)
        section.addView(label(
            "Opcional: habilita el servicio desde los ajustes de Android. " +
                "El icono azul es un acceso directo del sistema: puedes " +
                "desactivar ese acceso directo en Accesibilidad sin ocultar " +
                "los avisos de seguridad. Después autoriza solo Inicio y Atrás, mediante botones visibles. " +
                "Detener Hands desactiva inmediatamente las órdenes de JARVIS, " +
                "sin cambiar los permisos del sistema.",
            13f, Color.rgb(164, 166, 174)
        ))
        section.addView(primaryButton("CONSULTAR ESTADO HANDS") {
            refreshHandsStatus()
        })
        section.addView(primaryButton("AUTORIZAR HANDS — INICIO Y ATRÁS") {
            if (!OmegaHandsService.isConnected()) {
                statusText.text = "Primero habilita OMEGA Hands en los ajustes de accesibilidad."
            } else {
                OmegaHandsService.setEmergencyStop(this, false)
                statusText.text = if (OmegaHandsService.isStopped(this)) {
                    "No se pudo autorizar Hands. Comprueba el estado del servicio."
                } else {
                    "Autorizado en esta sesión: Inicio y Atrás solo por pulsación visible."
                }
            }
            refreshHandsStatus()
        })
        section.addView(primaryButton("IR A INICIO (PULSACIÓN EXPLÍCITA)") {
            val result = OmegaHandsService.goHome(this, explicitTap = true)
            statusText.text = if (result) "Orden Inicio aceptada por Android." else
                "No se ejecutó. Comprueba el servicio y la parada de emergencia."
            refreshHandsStatus()
        })
        section.addView(primaryButton("VOLVER ATRÁS (PULSACIÓN EXPLÍCITA)") {
            // Distinct typed local action. Never called by ASR, GGUF, or TTS.
            val result = OmegaHandsService.goBack(this, explicitTap = true)
            statusText.text = if (result) "Orden Atrás aceptada por Android." else
                "No se ejecutó Atrás. Comprueba autorización y parada de emergencia."
            refreshHandsStatus()
        })
        section.addView(primaryButton("PARADA DE EMERGENCIA HANDS") {
            OmegaHandsService.setEmergencyStop(this, true)
            statusText.text = "HANDS detenido. No se ejecutarán nuevas acciones."
            refreshHandsStatus()
        })

        section = OmegaUiKit.createSection(this, layout)
        val diagnosticsCard = section
        section.addView(label("OMEGA DIAGNÓSTICO — SOLO LECTURA", 20f,
            Color.rgb(239, 43, 54)).apply {
            setPadding(0, dp(24), 0, dp(8))
            typeface = Typeface.DEFAULT_BOLD
        })
        capabilityStatusText = label("Sin diagnóstico. Consulta manual y privada.", 13f,
            Color.rgb(211, 213, 220))
        section.addView(capabilityStatusText)
        section.addView(primaryButton("DIAGNÓSTICO LOCAL DE JARVIS") {
            capabilityStatusText.text = LocalCapabilityDiagnostics.format(readCapabilities())
        })

        section = OmegaUiKit.createSection(this, layout)
        val performanceCard = section
        section.addView(label("OMEGA PERFORMANCE — SALUD DEL TELÉFONO", 20f,
            Color.rgb(239, 43, 54)).apply {
            setPadding(0, dp(24), 0, dp(8))
            typeface = Typeface.DEFAULT_BOLD
        })
        healthStatusText = label("Sin consulta. La lectura es solo manual.", 13f,
            Color.rgb(211, 213, 220))
        section.addView(healthStatusText)
        section.addView(primaryButton("CONSULTAR BATERÍA, ESTADO TÉRMICO Y RAM") {
            healthStatusText.text = DeviceHealthFormatter.format(DeviceHealthReader(this).read())
        })
        section.addView(primaryButton("LEER SALUD DEL TELÉFONO EN VOZ ALTA") {
            val reading = DeviceHealthReader(this).read()
            healthStatusText.text = DeviceHealthFormatter.format(reading)
            spokenReplyEngine.speakHealthByTap(reading)
        })
        section.addView(label(
            "Datos locales consultados una sola vez al pulsar. El estado térmico " +
                "no es una temperatura en grados. No se guardan ni transmiten lecturas.",
            12f, Color.rgb(159, 161, 171)
        ))

        section = OmegaUiKit.createSection(this, layout)
        gameCard = section
        section.addView(label("OMEGA GAME READINESS — ASESOR PARA JUGAR", 20f,
            Color.rgb(239, 43, 54)).apply {
            setPadding(0, dp(24), 0, dp(8))
            typeface = Typeface.DEFAULT_BOLD
        })
        section.addView(label(
            "Evaluación local y manual de batería, estado térmico y memoria antes " +
                "de jugar. Solo recomendaciones: no modifica el hardware ni inicia juegos.",
            13f, Color.rgb(164, 166, 174)
        ))
        gameReadinessText = label(
            "Elige un perfil y pulsa EVALUAR ANTES DE JUGAR.",
            13f, Color.rgb(211, 213, 220)
        )
        section.addView(gameReadinessText)
        section.addView(primaryButton("PERFIL JUEGO: EQUILIBRADO") {
            gameHubPreflight.cancel()
            gamingPreference = GamingPreference.BALANCED
            gameReadinessText.text = "Perfil equilibrado seleccionado. Evalúa de nuevo."
        })
        section.addView(primaryButton("PERFIL JUEGO: COMPETITIVO") {
            gameHubPreflight.cancel()
            gamingPreference = GamingPreference.COMPETITIVE
            gameReadinessText.text = "Perfil competitivo seleccionado. Evalúa de nuevo."
        })
        section.addView(primaryButton("PERFIL JUEGO: CALIDAD VISUAL") {
            gameHubPreflight.cancel()
            gamingPreference = GamingPreference.VISUAL_QUALITY
            gameReadinessText.text = "Perfil calidad visual seleccionado. Evalúa de nuevo."
        })
        section.addView(primaryButton("EVALUAR ANTES DE JUGAR") {
            val snapshot = DeviceHealthReader(this).read()
            val result = GameReadinessAdvisor.assess(snapshot, gamingPreference)
            gameReadinessText.text = GameReadinessAdvisor.format(result)
        })

        section.addView(primaryButton("PREPARAR APERTURA SEGURA DE GAMEHUB") {
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
        section.addView(primaryButton("CONFIRMAR APERTURA SEGURA DE GAMEHUB") {
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

        section = OmegaUiKit.createSection(this, layout)
        visionCard = section
        section.addView(label("OMEGA VISION — OCR LOCAL", 20f, Color.rgb(239, 43, 54)).apply {
            setPadding(0, dp(24), 0, dp(8))
            typeface = Typeface.DEFAULT_BOLD
        })
        section.addView(label(
            "Selecciona una captura o imagen para reconocer su texto en el teléfono. " +
                "JARVIS no ve tu pantalla continuamente ni convierte el texto en comandos.",
            13f, Color.rgb(164, 166, 174)
        ))
        section.addView(primaryButton("ANALIZAR IMAGEN SELECCIONADA") { pickVisionImage() })
        section.addView(label(
            "Visión continua desactivada mientras completamos las pruebas físicas. " +
                "El análisis de imágenes elegidas sigue disponible. " +
                "Cuando se habilite, Android exigirá tu permiso. " +
                "El icono de transmisión y la notificación de captura " +
                "son indicadores del sistema, no se pueden ocultar desde JARVIS. " +
                "Se detiene a los 10 minutos. Sin clics ni imágenes guardadas.",
            13f, Color.rgb(164, 166, 174)
        ))
        section.addView(primaryButton("INICIAR VISIÓN DE PANTALLA") { requestScreenObservation() })
        section.addView(primaryButton("DETENER VISIÓN DE PANTALLA") {
            screenConsentRequested = false
            screenConsentGate.cancel()
            statusText.text = if (ScreenObservationService.stopNow())
                "Observación y autorizaciones pendientes detenidas."
            else "No se pudo confirmar la detención de la observación."
        })
        section.addView(primaryButton("ESTADO VISIÓN DE PANTALLA") {
            statusText.text = ScreenObservationService.status()
        })
        section.addView(primaryButton("ABRIR GAMEHUB ULTRA") {
            val launched = GameHubLauncher(this).launchOnTap()
            statusText.text = if (launched) "GameHub Ultra se abrió mediante Android."
                else "No se encontró GameHub Ultra instalado o Android rechazó la apertura."
        })
        section.addView(primaryButton("CONFIRMAR AJUSTES SOLICITADOS POR VOZ") {
            val confirmed = voiceConsent.consume(
                VoiceRequestedAction.ACCESSIBILITY_SETTINGS,
                SystemClock.elapsedRealtime()
            )
            if (confirmed) openAccessibilitySettings(explicitUserGesture = true)
            else statusText.text = "Sin solicitud vigente. Vuelve a pedir los ajustes por voz."
        })
        section.addView(primaryButton("CONFIRMAR APERTURA DE GAMEHUB POR VOZ") {
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
        section.addView(primaryButton("PERFIL: ESPAÑOL LOCAL") {
            spokenReplyEngine.selectStyleByTap(VoiceStyle.SPANISH)
        })
        section.addView(primaryButton("PERFIL: ASISTENTE BRITÁNICO LOCAL") {
            spokenReplyEngine.selectStyleByTap(VoiceStyle.BRITISH_ASSISTANT)
        })
        section.addView(primaryButton("LEER ESTADO EN VOZ ALTA") {
            spokenReplyEngine.speakByTap(SpokenMessage.LOCAL_STATUS)
        })
        section.addView(primaryButton("DETENER RESPUESTA DE VOZ") {
            stopForegroundSpeech()
            recognitionRequest.cancel()
            voiceConsent.cancel()
            spokenReplyEngine.stop()
            statusText.text = "Micrófono y voz detenidos. Pulsa HABLAR o PROBAR AUDIO para reiniciar por tu cuenta."
        })
        section.addView(primaryButton("ABRIR AJUSTES DE ACCESIBILIDAD") {
            openAccessibilitySettings(explicitUserGesture = true)
        })
        section.addView(label(
            "Privacidad: el reconocimiento de voz lo realiza la aplicación de voz de Android. " +
                "Puede requerir conexión y usar un servicio externo. " +
                "JARVIS no graba de forma continua ni guarda transcripciones. " +
                "Las respuestas TTS usan únicamente una voz local instalada en Android. " +
                "El perfil británico es un estilo de asistente, no la voz del actor de cine. " +
                "Si no se encuentra una voz compatible, instálala desde los ajustes de texto a voz.",
            12f, Color.rgb(159, 161, 171)
        ).apply { setPadding(0, dp(24), 0, 0) })

        // Reorganize the existing 37 action handlers, never cloning or triggering
        // them automatically. Emergency privacy lock and start-talking remain visible.
        val advancedNames = setOf(
            "PROBAR JARVIS OS — SIN CAMBIAR FUNTOUCH",
            "PERMITIR CAPTURAS 60 S — DEPURACIÓN",
            "PROTEGER PANTALLA AHORA",
            "PROBAR AUDIO JARVIS",
            "CONFIGURAR VOZ EN ANDROID"
        )
        val advancedControls = (0 until layout.childCount)
            .map { layout.getChildAt(it) }
            .filterIsInstance<Button>()
            .filter { it.text.toString() in advancedNames }
        compactPanels = OmegaCompactPanels(this, layout) { header ->
            if (::omegaScroll.isInitialized) {
                omegaScroll.post {
                    val shell = header.parent as? android.view.View
                    if (shell != null) omegaScroll.smoothScrollTo(0, shell.top)
                }
            }
        }
        compactPanels.addGroup("CHAT Y MEMORIA", listOf(chatCard))
        compactPanels.addGroup("JUEGOS Y RENDIMIENTO", listOf(gameCard, performanceCard))
        compactPanels.addGroup("VISIÓN Y PANTALLA", listOf(visionCard))
        compactPanels.addGroup(
            "SISTEMA Y PRIVACIDAD",
            listOf<android.view.View>(handsCard, diagnosticsCard) + advancedControls
        )
        // Exact allowlist for a UI-only deep link; never triggers a sensitive action.
        val requestedPanel = intent?.getStringExtra("jarvis.omega.ui.panel")

        omegaScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.rgb(9, 11, 19))
        }
        omegaScroll.setOnScrollChangeListener { _, _, _, _, _ ->
            omegaCore.pauseMotion()
            scrollUiHandler.removeCallbacks(scrollResumeRunnable)
            scrollUiHandler.postDelayed(scrollResumeRunnable, 250L)
        }
        omegaScroll.addView(layout)
        setContentView(omegaScroll)
        // Defer until the hierarchy has measured; the permitted panel opens
        // visibly and scrolls into view, without invoking its child actions.
        if (requestedPanel == "JUEGOS Y RENDIMIENTO") {
            omegaScroll.post { compactPanels.show(requestedPanel) }
        }
        requestMicrophoneOnFirstLaunch()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Keep request codes monotonic on rotation; never persist live consent.
        outState.putInt(VISION_PICKER_COUNTER_KEY, visionPickerRequest.issuedCount())
        outState.putInt(SCREEN_CONSENT_COUNTER_KEY, screenConsentGate.issuedCount())
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        if (::omegaCore.isInitialized) omegaCore.resumeMotion()
        enforceCapturePrivacy()
        foregroundGate.onStart()
        consumePendingPrivateNote()
    }

    override fun onStop() {
        handsFreeUiHandler.removeCallbacks(handsFreeUiRefresh)
        scrollUiHandler.removeCallbacks(scrollResumeRunnable)
        if (::omegaCore.isInitialized) {
            omegaCore.pauseMotion()
            omegaCore.setSpeaking(false)
        }
        gameHubPreflight.cancel()
        revokeCaptureException()
        // The document picker may briefly background this Activity without
        // holding private bytes. Other transitions revoke pending workers.
        if (!notePickerOpen) noteImportEpoch.incrementAndGet()
        localDialogue.forgetLocalNote()
        foregroundGate.onStop()
        if (foregroundSpeech != null) {
            stopForegroundSpeech()
            recognitionRequest.cancel()
        }
        clearVisibleReadouts()
        // The system recognizer may be another Activity: preserve only a short-lived
        // numeric answer while our explicitly started recognition is pending.
        if (!recognitionRequest.isPending(SystemClock.elapsedRealtime())) voiceMathSession.clear()
        if (::privateMemory.isInitialized) privateMemory.stop()
        if (::localGgufInference.isInitialized) localGgufInference.cancelActive()
        voiceConsent.cancel()
        lastChatReply = null
        if (::visionRecognizer.isInitialized) visionRecognizer.cancel()
        if (::spokenReplyEngine.isInitialized) spokenReplyEngine.stop()
        super.onStop()
    }

    override fun onDestroy() {
        handsFreeUiHandler.removeCallbacks(handsFreeUiRefresh)
        scrollUiHandler.removeCallbacks(scrollResumeRunnable)
        gameHubPreflight.cancel()
        revokeCaptureException()
        voiceMathSession.clear()
        recognitionRequest.cancel()
        stopForegroundSpeech()
        visionPickerRequest.cancel()
        screenConsentRequested = false
        screenConsentGate.cancel()
        ScreenObservationService.stopNow()
        if (::spokenReplyEngine.isInitialized) spokenReplyEngine.close()
        if (::visionRecognizer.isInitialized) visionRecognizer.close()
        if (::localGgufInference.isInitialized) localGgufInference.close()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::handsStatusText.isInitialized) refreshHandsStatus()
        // The prior UI wrote ASR_NATIVE_LOADING once and never showed subsequent
        // service statuses until the Activity was reopened. This foreground-only
        // ticker follows ASR_NATIVE_* phases and actual terminal errors.
        handsFreeUiHandler.removeCallbacks(handsFreeUiRefresh)
        handsFreeUiHandler.post(handsFreeUiRefresh)
    }

    private fun refreshHandsFreePreviewUi() {
        handsFreePreviewButton?.text = if (HandsFreePreviewService.isRunning) {
            "DETENER MANOS LIBRES · PRUEBA"
        } else "PROBAR MANOS LIBRES · 60 S"
        if (::handsFreePreviewStatus.isInitialized) {
            handsFreePreviewStatus.text = HandsFreePreviewService.readDiagnostic(this)
        }
    }

    private fun stopHandsFreePreview() {
        // Explicit local stop. No other app may start this unexported service.
        try {
            startService(Intent(this, HandsFreePreviewService::class.java).apply {
                action = HandsFreePreviewService.ACTION_STOP
            })
        } catch (_: RuntimeException) { /* service may already have timed out */ }
        handsFreePreviewButton?.text = "PROBAR MANOS LIBRES · 60 S"
        if (::handsFreePreviewStatus.isInitialized) {
            handsFreePreviewStatus.text = "Deteniendo micrófono…"
        }
    }

    private fun toggleHandsFreePreviewByTap(diagnostic: Boolean = false,
        systemMode: Boolean = false, extended: Boolean = false,
        conversation: Boolean = false, conversationExtended: Boolean = false) {
        if (HandsFreePreviewService.isRunning) {
            stopHandsFreePreview()
            statusText.text = "Prueba manos libres detenida. Micrófono liberado."
            return
        }
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!mic) {
            statusText.text = "Autoriza el micrófono y vuelve a pulsar PROBAR MANOS LIBRES."
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
            return
        }
        val notifications = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!notifications) {
            statusText.text = "Autoriza las notificaciones para tener un control visible " +
                "de DETENER MICRÓFONO. Luego vuelve a pulsar."
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_PREVIEW_NOTIFICATIONS)
            return
        }
        val offline = Build.VERSION.SDK_INT >= 31 && try {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        } catch (_: RuntimeException) { false }
        val systemAvailable = systemMode && try {
            SpeechRecognizer.isRecognitionAvailable(this)
        } catch (_: RuntimeException) { false }
        // Bundled Vosk uses its own audio pipeline; it does not need the
        // phone vendor's on-device SpeechRecognizer language packs.
        when (HandsFreePreviewPolicy().canStart(
            true, mic, notifications, when {
                diagnostic -> true
                systemMode -> systemAvailable
                else -> true // native Vosk model bundled with the APK
            }
        )) {
            PreviewStart.DENIED -> statusText.text = "Faltan permisos para la escucha."
            PreviewStart.OFFLINE_ENGINE_MISSING -> statusText.text =
                if (systemMode) "No hay proveedor de reconocimiento de voz del sistema."
                else "No hay motor de reconocimiento 100% local en Android. " +
                    "Instala el idioma español offline; no cambio a nube automáticamente."
            PreviewStart.ALLOWED -> try {
                // The foreground HABLAR session must release the same microphone
                // before this isolated hardware diagnostic/preview starts.
                stopForegroundSpeech()
                if (::spokenReplyEngine.isInitialized) spokenReplyEngine.stop()
                startForegroundService(Intent(this, HandsFreePreviewService::class.java).apply {
                    action = when {
                        diagnostic -> HandsFreePreviewService.ACTION_DIAGNOSE
                        systemMode -> HandsFreePreviewService.ACTION_START_SYSTEM
                        conversationExtended ->
                            HandsFreePreviewService.ACTION_START_LONG_CONVERSATION
                        extended -> HandsFreePreviewService.ACTION_START_LONG_NATIVE
                        conversation -> HandsFreePreviewService.ACTION_START_CONVERSATION
                        else -> HandsFreePreviewService.ACTION_START_NATIVE
                    }
                    putExtra(HandsFreePreviewService.EXTRA_TAPPED, true)
                })
                statusText.text = when {
                    diagnostic -> "Diagnóstico de micrófono 4 s. Habla ahora. " +
                        "Después JARVIS probará el TTS sin depender del idioma ASR."
                    systemMode -> "Prueba del proveedor normal de Android solicitada. " +
                        "Puede usar Internet. Di «Jarvis, dime la hora»; " +
                        "después revisa el diagnóstico. No envía órdenes externas."
                    conversationExtended -> "LOCAL_VOICE_MULTITURN_PREPARING: " +
                        "GGUF local con un máximo de seis preguntas, control DETENER " +
                        "y supresión de eco. Sin autorización biométrica."
                    conversation -> "LOCAL_VOICE_PREPARING: escucha Vosk local de una sola " +
                        "pregunta y GGUF privado. Modelo y voz sujetos a validación real."
                    extended -> "EXTENDED_LISTENING: 30 minutos de escucha local " +
                        "con control DETENER visible. No requiere Internet ni identifica propietario."
                    else -> "Preparando reconocimiento Vosk español dentro de la APK. " +
                        "Cuando esté listo, di «Jarvis, dime la hora» durante 60 s. " +
                        "No requiere Internet ni reconocedor de Android."
                }
                handsFreePreviewButton?.text = "DETENER MANOS LIBRES · PRUEBA"
                handsFreePreviewStatus.text = when {
                    diagnostic -> "Midiendo micrófono y probando TTS…"
                    systemMode -> "Intentando reconocimiento del sistema · puede usar Internet…"
                    else -> "ASR_NATIVE_LOADING: preparando motor de voz español local…"
                }

            } catch (_: RuntimeException) {
                statusText.text = "Android impidió iniciar el micrófono. " +
                    "Comprueba permisos y restricciones de Funtouch."
            }
        }
    }

    /** Revoke local modules on a visible tap or a one-shot foreground speech request. */
    private fun lockSessionByTap() {
        val actions: Map<SessionLockChannel, () -> Unit> = mapOf(
            SessionLockChannel.VOICE_REQUEST to {
                voiceConsent.cancel()
                voiceMathSession.clear()
                recognitionRequest.cancel()
                stopForegroundSpeech()
                stopHandsFreePreview()
            },
            SessionLockChannel.SPOKEN_REPLY to { spokenReplyEngine.stop() },
            SessionLockChannel.IMAGE_OCR to {
                visionPickerRequest.cancel()
                visionRecognizer.cancel()
            },
            SessionLockChannel.SCREEN_PROJECTION to {
                screenConsentRequested = false
                screenConsentGate.cancel()
                check(ScreenObservationService.stopNow())
            },
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
                noteImportEpoch.incrementAndGet()
                pendingPrivateNoteUri = null
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

    private fun requestScreenObservation() {
        if (!ScreenObservationReleaseGate.ENABLED) {
            statusText.text = ScreenObservationReleaseGate.UNAVAILABLE_MESSAGE
            return
        }
        val notificationGranted = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!ScreenNotificationGate.canBegin(Build.VERSION.SDK_INT, notificationGranted)) {
            statusText.text = "Autoriza las notificaciones y vuelve a pulsar INICIAR VISIÓN."
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_SCREEN_NOTIFICATIONS)
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (manager == null) {
            statusText.text = "Este Android no admite MediaProjection."
            return
        }
        val requestCode = screenConsentGate.begin(SystemClock.elapsedRealtime())
        if (requestCode == null) {
            statusText.text = "Ya existe una autorización pendiente o se agotó el cupo de sesión."
            return
        }
        try {
            screenConsentRequested = true
            @Suppress("DEPRECATION")
            startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
        } catch (_: RuntimeException) {
            screenConsentRequested = false
            screenConsentGate.cancel()
            statusText.text = "Android no pudo solicitar autorización de captura."
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MICROPHONE) {
            statusText.text = if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                "Micrófono autorizado. Pulsa HABLAR CON JARVIS para empezar una escucha puntual."
            else "Sin permiso de micrófono. Podrás volver a pedirlo al pulsar HABLAR CON JARVIS."
            return
        }
        if (requestCode == REQUEST_PREVIEW_NOTIFICATIONS) {
            statusText.text = if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                "Notificaciones permitidas; vuelve a pulsar PROBAR MANOS LIBRES."
            else "Prueba detenida: no hay permiso para mostrar el control de micrófono."
            return
        }
        if (requestCode == REQUEST_SCREEN_NOTIFICATIONS) {
            statusText.text = if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                "Notificaciones autorizadas. Pulsa INICIAR VISIÓN para pedir el permiso de captura."
            else "Sin notificaciones, la observación no se inició."
        }
    }

    private fun pickVisionImage() {
        // Fresh request code on every picker launch: old OS callbacks cannot
        // authorize a new image after privacy lock, expiry, or cancellation.
        val requestCode = visionPickerRequest.begin(SystemClock.elapsedRealtime())
        if (requestCode == null) {
            statusText.text = "Hay otra selección en curso o se agotó el cupo de esta sesión."
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, requestCode)
        } catch (_: ActivityNotFoundException) {
            visionPickerRequest.cancel()
            statusText.text = "No se encontró un selector de imágenes en Android."
        } catch (_: SecurityException) {
            visionPickerRequest.cancel()
            statusText.text = "Android no permitió elegir una imagen."
        } catch (_: RuntimeException) {
            visionPickerRequest.cancel()
            statusText.text = "Android no pudo iniciar el selector de imágenes."
        }
    }

    /**
     * Android owns the first-launch microphone dialog. Granting permission only
     * unlocks the HABLAR button; it never turns on continuous listening.
     * Never re-prompt automatically after an explicit rejection.
     */
    private fun requestMicrophoneOnFirstLaunch() {
        if (Build.VERSION.SDK_INT < 23 ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("omega_mic_permission_v1", MODE_PRIVATE)
        if (prefs.getBoolean("requested_once", false)) return
        prefs.edit().putBoolean("requested_once", true).apply()
        statusText.text = "JARVIS solicitará permiso para el micrófono. " +
            "Solo escuchará cuando pulses HABLAR CON JARVIS, nunca al abrirse."
        try {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
        } catch (_: RuntimeException) {
            statusText.text = "Android no pudo solicitar permiso de micrófono. " +
                "Puedes intentarlo pulsando HABLAR."
        }
    }

    private fun stopForegroundSpeech() {
        foregroundSpeech?.stop()
        foregroundSpeech = null
    }

    private fun startSpeech() {
        voiceConsent.cancel()
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Autoriza el micrófono para una escucha puntual. " +
                "El permiso no inicia grabación automática."
            try {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
            } catch (_: RuntimeException) {
                statusText.text = "Android no permitió solicitar permiso de micrófono."
            }
            return
        }
        if (::spokenReplyEngine.isInitialized) spokenReplyEngine.stop()
        if (!recognitionRequest.begin(SystemClock.elapsedRealtime())) {
            statusText.text = "Ya hay un reconocimiento de voz activo. Cancélalo antes de iniciar otro."
            return
        }
        // A one-shot in-app recognizer is preferred; retain the Android voice
        // activity as an explicit foreground fallback for OEM compatibility.
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla con JARVIS")
        }
        stopForegroundSpeech()
        val recognizer = ForegroundSpeechRecognizer(
            context = this,
            onResult = { transcript ->
                foregroundSpeech = null
                val valid = recognitionRequest.consume(SystemClock.elapsedRealtime())
                if (!valid || !foregroundGate.canAcceptRecognitionResult()) {
                    voiceMathSession.clear()
                    voiceConsent.cancel()
                } else if (transcript.isNullOrBlank()) {
                    voiceMathSession.clear()
                    statusText.text = "No se reconoció ningún comando."
                } else {
                    processRecognizedSpeech(transcript)
                }
            },
            onFailure = { error ->
                foregroundSpeech = null
                recognitionRequest.cancel()
                voiceMathSession.clear()
                voiceConsent.cancel()
                statusText.text = "El reconocedor Android terminó con error " + error +
                    ". Pulsa HABLAR para intentar nuevamente."
            },
            onStatus = { message -> statusText.text = message }
        )
        foregroundSpeech = recognizer
        if (recognizer.start(intent)) {
            statusText.text = "Preparando micrófono para una escucha puntual. " +
                "Android puede usar el servicio de reconocimiento configurado."
            return
        }
        foregroundSpeech = null
        recognizer.stop()
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
        } catch (_: RuntimeException) {
            recognitionRequest.cancel()
            voiceMathSession.clear()
            statusText.text = "Android rechazó el reconocimiento de voz."
        }
    }

    @Deprecated("Uses the platform Activity result API without additional dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == NOTE_PICKER_REQUEST) {
            notePickerOpen = false
            pendingPrivateNoteUri = if (resultCode == RESULT_OK &&
                data?.data?.scheme == ContentResolver.SCHEME_CONTENT) data.data else null
            if (pendingPrivateNoteUri == null) {
                statusText.text = "Nota no seleccionada; no se importó ningún archivo."
            } else consumePendingPrivateNote()
            return
        }
        if (requestCode == GGUF_PICKER_REQUEST) {
            val selected: Uri? = if (resultCode == RESULT_OK) data?.data else null
            if (selected != null && foregroundGate.canAcceptRecognitionResult()) {
                statusText.text = "Copiando y verificando modelo GGUF privado…"
                val accepted = localGgufInference.importModel(selected) { sha ->
                    if (!isDestroyed) statusText.text =
                        if (sha != null) "GGUF importado y SHA-256 registrado. " +
                            "Puedes hacer una pregunta sin Internet."
                        else "Importación fallida: archivo incompatible, " +
                            "sin acceso o sin espacio."
                }
                if (!accepted) statusText.text =
                    "Otra operación GGUF está activa. Inténtalo de nuevo."
            } else {
                statusText.text = "Modelo no seleccionado o sesión no autorizada."
            }
            return
        }
        if (screenConsentGate.owns(requestCode)) {
            val authorized = screenConsentGate.consume(requestCode, SystemClock.elapsedRealtime())
            if (!authorized) {
                // A delayed callback from an older session must not cancel the
                // user consent currently in flight for a different request code.
                statusText.text = "Resultado anterior de captura ignorado."
                return
            }
            if (!ScreenObservationReleaseGate.ENABLED) {
                screenConsentRequested = false
                screenConsentGate.cancel()
                ScreenObservationService.stopNow()
                statusText.text = ScreenObservationReleaseGate.UNAVAILABLE_MESSAGE
                return
            }
            val requestedByTap = screenConsentRequested
            screenConsentRequested = false
            val notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!authorized || !requestedByTap || resultCode != RESULT_OK || data == null ||
                !foregroundGate.canAcceptRecognitionResult() ||
                !ScreenNotificationGate.canBegin(Build.VERSION.SDK_INT, notificationsAllowed) ||
                !ScreenObservationService.authorizeOneStart()) {
                statusText.text = "Observación no autorizada; no se inició."
                return
            }
            val service = Intent(this, ScreenObservationService::class.java).apply {
                action = ScreenObservationService.ACTION_START
                putExtra(ScreenObservationService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenObservationService.EXTRA_RESULT_DATA, data)
            }
            try {
                startForegroundService(service)
                statusText.text = "Permiso recibido. Comprueba la notificación de captura visible."
            } catch (_: RuntimeException) {
                ScreenObservationService.stopNow()
                statusText.text = "Android no pudo iniciar la observación."
            }
            return
        }
        if (visionPickerRequest.owns(requestCode)) {
            val requested = visionPickerRequest.consume(requestCode, SystemClock.elapsedRealtime())
            if (requested && resultCode == RESULT_OK && foregroundGate.canAcceptRecognitionResult()) {
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
        processRecognizedSpeech(transcript)
    }

    private fun processRecognizedSpeech(transcript: String) {
        // Already passed the one-use microphone request + foreground gate.
        // Voice may ONLY propose an exact allowlisted routine; it cannot execute it.
        val proposedPlan = offlineRoutinePlanner.plan(transcript)
        val proposed = proposedPlan?.routine
        if (proposed != null) {
            voiceMathSession.clear()
            voiceConsent.cancel()
            statusText.text = "${proposedPlan.summary} " +
                "Revisa y autoriza en pantalla; aún no se ejecutó ninguna acción."
            spokenReplyEngine.speakRecognizedAnswerAfterTap(statusText.text.toString())
            AlertDialog.Builder(this)
                .setTitle("JARVIS escuchó una rutina")
                .setMessage(proposed.summary + "\\n\\nLa voz nunca concede permisos ni ejecuta " +
                    "acciones. ¿Quieres abrir su vista previa?")
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("REVISAR PLAN") { _, _ ->
                    try {
                        startActivity(
                            Intent(this, CompanionCenterActivity::class.java)
                                .putExtra(CompanionCenterActivity.EXTRA_ROUTINE_PREVIEW, proposed.name)
                        )
                    } catch (_: RuntimeException) {
                        statusText.text = "No pude abrir el plan de automatización."
                    }
                }.show()
            return
        }
        val action = parser.parse(transcript)
        if (action != VoiceAction.UNSUPPORTED) voiceMathSession.clear()
        // Fresh structured readings are kept only on the stack, never as audio
        // recordings or background messages.
        var freshHealth: DeviceHealthSnapshot? = null
        var freshGame: GameReadinessReport? = null
        when (action) {
            VoiceAction.READ_STATUS -> {
                if (policy.canExecute(DeviceOperation.READ_LOCAL_STATUS,
                        explicitUserGesture = false)) {
                    statusText.text = "JARVIS operativo en modo observador. " +
                        "Integraciones remotas y automatización aún no habilitadas."
                }
            }
            VoiceAction.READ_DEVICE_HEALTH -> {
                // One user-initiated local reading; the spoken version is bounded
                // by the structured DeviceHealthSpeechPolicy after recognition ends.
                val reading = DeviceHealthReader(this).read()
                statusText.text = DeviceHealthFormatter.format(reading)
                freshHealth = reading
            }
            VoiceAction.READ_GAME_READINESS -> {
                // Read-only advice, with no app launch or hardware changes.
                // The spoken version uses a conservative fixed recommendation.
                val reading = DeviceHealthReader(this).read()
                val report = GameReadinessAdvisor.assess(reading, gamingPreference)
                statusText.text = GameReadinessAdvisor.format(report)
                freshGame = report
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
                lockSessionByTap()
            }
            VoiceAction.UNSUPPORTED -> {
                // Zero-billing voice by default, regardless of a previous
                // cloud pairing. Spoken words can NEVER initiate a paid call.
                // Read-only math fallback: no Android actions or saved transcript.
                val localAnswer = voiceMathSession.answer(transcript,
                    SystemClock.elapsedRealtime())
                    ?: localDialogue.replyVoice(transcript)
                if (localAnswer != null) lastChatReply = localAnswer
                statusText.text = localAnswer ?:
                    "Orden no disponible. No se realizó ninguna acción."
            }
        }
        // Both the in-app and the external Android recognizer arrive here ONLY
        // after the one-use recognition gate and foreground check have passed.
        // Re-arm TTS for this explicit HABLAR tap, never for an unsolicited event.
        // Emergency STOP revokes the session and must not cause a spoken reply.
        if (action != VoiceAction.STOP_SESSION) {
            when {
                freshHealth != null ->
                    spokenReplyEngine.speakRecognizedHealthAfterTap(freshHealth)
                freshGame != null ->
                    spokenReplyEngine.speakRecognizedGameAfterTap(freshGame)
                else ->
                    spokenReplyEngine.speakRecognizedAnswerAfterTap(statusText.text.toString())
            }
            // Only a prior visible tap on ACTIVAR MEMORIA can permit a write.
            // A voice turn contributes an enum topic, never the ASR transcript.
            if (privateMemory.isEnabled()) {
                try {
                    if (!privateMemory.save(localDialogue.snapshotForStorage())) {
                        privateMemory.stop()
                        statusText.text = "Memoria cifrada desactivada: no se pudo guardar el tema."
                    }
                } catch (_: Exception) {
                    privateMemory.stop()
                    statusText.text = "Memoria cifrada no disponible; tema no guardado."
                }
            }
        }
    }

    private fun openTtsSettingsByTap() {
        // System screen names vary by OEM; fall back to general Android settings.
        try {
            startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            statusText.text = "Configura la síntesis de voz sin conexión en Android."
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                statusText.text = "En Ajustes busca 'Texto a voz' o 'Síntesis de voz'."
            } catch (_: RuntimeException) {
                statusText.text = "Android no pudo abrir los ajustes de voz."
            }
        } catch (_: SecurityException) {
            statusText.text = "Android bloqueó la apertura de ajustes de voz."
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
        if (value.startsWith("OMEGA ") && size >= 20f) {
            OmegaUiKit.sectionTitle(this, value)
        } else {
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
            }
        }

    private fun primaryButton(value: String, action: () -> Unit): Button =
        Button(this).apply {
            text = value
            isAllCaps = false
            textSize = 13.5f
            letterSpacing = .02f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = OmegaUiKit.buttonBackground(this@MainActivity, value)
            minHeight = dp(56)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            elevation = dp(2).toFloat()
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun consumePendingPrivateNote() {
        val uri = pendingPrivateNoteUri ?: return
        if (!foregroundGate.canAcceptRecognitionResult() ||
            !noteImportBusy.compareAndSet(false, true)) return
        pendingPrivateNoteUri = null
        val epoch = noteImportEpoch.get()
        statusText.text = "Leyendo nota local en memoria; sin Internet."
        Thread({
            val note = try {
                val stream = contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Nota inaccesible")
                PrivateKnowledgePack.readText(stream) {
                    noteImportEpoch.get() == epoch && !Thread.currentThread().isInterrupted
                }
            } catch (_: Exception) { null }
            runOnUiThread {
                try {
                    if (noteImportEpoch.get() == epoch &&
                        foregroundGate.canAcceptRecognitionResult() &&
                        !isFinishing && !isDestroyed) {
                        statusText.text = if (note == null) {
                            "No se importó: nota inválida, demasiado grande o cancelada."
                        } else {
                            try {
                                val fragments = localDialogue.importLocalNote(note)
                                "Nota local indexada: $fragments fragmentos. " +
                                    "Pregunta: busca en mis notas..."
                            } catch (_: IllegalArgumentException) {
                                "Nota sin párrafos válidos o supera el límite de memoria."
                            }
                        }
                    }
                } finally {
                    noteImportBusy.set(false)
                }
            }
        }, "jarvis-optional-private-note-import").start()
    }

    private companion object {
        const val NOTE_PICKER_REQUEST = 9103
        const val SPEECH_REQUEST = 7001
        const val GGUF_PICKER_REQUEST = 9102
        private const val VISION_PICKER_COUNTER_KEY = "jarvis.vision.picker.issued"
        private const val SCREEN_CONSENT_COUNTER_KEY = "jarvis.screen.consent.issued"
        private const val REQUEST_SCREEN_NOTIFICATIONS = 9004
        private const val REQUEST_MICROPHONE = 9005
        private const val REQUEST_PREVIEW_NOTIFICATIONS = 9010
    }
}
