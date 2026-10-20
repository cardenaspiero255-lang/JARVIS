package com.cardenaspiero255.jarvis

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.cardenaspiero255.jarvis.core.VivoCheck
import com.cardenaspiero255.jarvis.core.VivoDiagnosticReport
import com.cardenaspiero255.jarvis.core.VivoReportedResult

/**
 * One-install, opt-in physical lab for a Vivo V25 Pro. Reuses REAL app services,
 * never starts audio from onCreate/onResume and never records audio or transcripts.
 * All acoustic correctness flags are EXPLICITLY marked by the owner.
 */
class VivoDiagnosticActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy {
        getSharedPreferences("jarvis_vivo_one_install_diag_v1", MODE_PRIVATE)
    }
    private lateinit var status: TextView
    private lateinit var snapshot: TextView
    private lateinit var resultColumn: LinearLayout
    private var localInference: LocalGgufInference? = null
    private var inferenceTimeout: Runnable? = null
    private var qwenGeneration = 0
    private val refresh = object : Runnable {
        override fun run() {
            status.text = HandsFreePreviewService.readDiagnostic(this@VivoDiagnosticActivity)
            ui.postDelayed(this, 1500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent unsolicited screen previews of owner-related diagnostics.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 44)
            setBackgroundColor(Color.rgb(9, 12, 20))
        }
        fun label(text: String, size: Float = 14f): TextView =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(Color.WHITE)
                setPadding(4, 12, 4, 12)
            }
        fun button(text: String, action: () -> Unit): Button =
            Button(this).apply {
                this.text = text
                isAllCaps = false
                setOnClickListener { action() }
            }

        root.addView(label("JARVIS · LABORATORIO VIVO V25 PRO", 23f))
        root.addView(label(
            "Una instalación. Pruebas reales iniciadas SOLAMENTE al pulsar cada botón. " +
            "Nada se activa al abrir esta pantalla. NO_CERTIFICADO hasta medir en el teléfono."
        ))
        root.addView(label(
            "No se guardan micrófono, transcripciones, respuestas Qwen, nombres ni " +
            "identificadores únicos. El informe diferencia mediciones de lo que tú observes."
        ))
        snapshot = label("Lectura pendiente; pulsa ACTUALIZAR.", 13f)
        root.addView(snapshot)
        root.addView(button("1 · ACTUALIZAR batería, RAM, modelo, permisos y estado") {
            updateSnapshot()
        })

        root.addView(label("PRUEBAS FÍSICAS (permiso y pulsación explícitos)", 18f))
        root.addView(button("2 · Micrófono 4 s + voz TTS audible") {
            startLocalSession(HandsFreePreviewService.ACTION_DIAGNOSE)
        })
        root.addView(button("3 · Decir «Jarvis, dime la hora» (Vosk 60 s)") {
            startLocalSession(HandsFreePreviewService.ACTION_START_NATIVE)
        })
        root.addView(button("4 · Pantalla bloqueada y micrófono (hasta 30 min)") {
            AlertDialog.Builder(this)
                .setTitle("Continuidad al bloquear pantalla")
                .setMessage("Abre un servicio visible de micrófono durante un máximo de " +
                    "30 minutos. Bloquea la pantalla unos segundos y vuelve para " +
                    "leer el contador de muestras. Android puede interrumpir la sesión.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("ACTIVAR") { _, _ ->
                    startLocalSession(HandsFreePreviewService.ACTION_START_LONG_NATIVE)
                }.show()
        })
        root.addView(button("5 · Qwen local: respuesta + latencia (sin nube)") {
            testLocalQwenByTap()
        })
        root.addView(button("6 · Voz del propietario: registro y prueba consentidos") {
            startActivity(Intent(this, OwnerVoiceEnrollmentActivity::class.java))
        })
        root.addView(button("7 · Preguntas habladas Vosk → GGUF → TTS (hasta 30 min)") {
            AlertDialog.Builder(this)
                .setTitle("Voz completa, solo local")
                .setMessage("Requiere haber importado un GGUF privado compatible. " +
                    "Usa Vosk y TTS locales; todavía NO certifica la voz del dueño. " +
                    "Comienza solo por esta autorización, con notificación DETENER.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("ACTIVAR") { _, _ ->
                    startLocalSession(HandsFreePreviewService.ACTION_START_LONG_NATIVE_QA)
                }.show()
        })
        root.addView(button("DETENER MICRÓFONO Y CANCELAR QWEN") { stopAllByTap() })
        status = label(HandsFreePreviewService.readDiagnostic(this), 13f)
        root.addView(status)

        root.addView(label("RESULTADOS QUE TÚ OBSERVASTE (NO son certificaciones)", 18f))
        resultColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(resultColumn)
        redrawReportedResults()
        root.addView(button("ACTUALIZAR PRUEBAS Y CONTADOR DE PANTALLA APAGADA") {
            updateSnapshot()
        })
        root.addView(button("COMPARTIR INFORME SIN AUDIO NI DATOS PRIVADOS") {
            val report = collectReport().toShareText()
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, report)
            }, "Compartir diagnóstico de JARVIS"))
        })
        root.addView(button("BORRAR MARCAS MANUALES Y LATENCIA") {
            AlertDialog.Builder(this)
                .setTitle("Borrar resultados locales")
                .setMessage("Borra las marcas manuales y la medición Qwen de este laboratorio. " +
                    "No afecta los perfiles biométricos ni los modelos importados.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("BORRAR") { _, _ ->
                    prefs.edit().clear().apply()
                    redrawReportedResults()
                    updateSnapshot()
                }.show()
        })
        setContentView(ScrollView(this).apply { addView(root) })
        updateSnapshot()
    }

    private fun permissionReady(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            status.text = "Permiso de micrófono pendiente: vuelve a pulsar después de concederlo."
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 8221)
            return false
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            status.text = "Permiso de notificaciones pendiente; es necesario un DETENER visible."
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 8222)
            return false
        }
        return true
    }

    private fun startLocalSession(action: String) {
        if (HandsFreePreviewService.isRunning) {
            status.text = "Ya hay una sesión activa. Primero pulsa DETENER."
            return
        }
        if (!permissionReady()) return
        if (action == HandsFreePreviewService.ACTION_START_LONG_NATIVE_QA &&
            !LocalGgufModelStore(this).hasModel()) {
            status.text = "MODELO_FALTANTE: importa primero un GGUF desde la pantalla principal."
            return
        }
        try {
            startForegroundService(Intent(this, HandsFreePreviewService::class.java).apply {
                this.action = action
                putExtra(HandsFreePreviewService.EXTRA_TAPPED, true)
            })
            status.text = "Sesión solicitada. Comprueba ASR_NATIVE_READY y el " +
                "resultado; NO se considera éxito solo por iniciar."
        } catch (_: RuntimeException) {
            status.text = "Android/Funtouch impidió iniciar la sesión. " +
                "Comprueba permiso y ejecución en primer plano."
        }
    }

    private fun stopAllByTap() {
        qwenGeneration++
        inferenceTimeout?.let(ui::removeCallbacks)
        inferenceTimeout = null
        localInference?.cancelActive()
        localInference?.close()
        localInference = null
        prefs.edit().putString("qwen", "CANCELADO_POR_USUARIO")
            .remove("qwen_ms").apply()
        try {
            startService(Intent(this, HandsFreePreviewService::class.java).apply {
                action = HandsFreePreviewService.ACTION_STOP
            })
        } catch (_: RuntimeException) { /* Already stopped. */ }
        status.text = "DETENER solicitado. Ningún resultado marcado como aprobado."
    }

    private fun testLocalQwenByTap() {
        if (localInference != null) {
            status.text = "Qwen ya está procesando. DETENER cancela la prueba."
            return
        }
        if (!LocalGgufModelStore(this).hasModel()) {
            prefs.edit().putString("qwen", "GGUF_NO_IMPORTADO")
                .remove("qwen_ms").apply()
            status.text = "GGUF_NO_IMPORTADO: importa un archivo GGUF desde la APK. " +
                "El modelo del emulador CI NO viene incluido."
            return
        }
        val engine = LocalGgufInference(LocalGgufModelStore(this))
        localInference = engine
        val generation = ++qwenGeneration
        val began = SystemClock.elapsedRealtime()
        prefs.edit().putString("qwen", "EN_PROCESO").remove("qwen_ms").apply()
        status.text = "Qwen local ejecutándose; no se guarda el texto de la respuesta."
        val question = "En una frase, ¿qué es un procesador?"
        val accepted = engine.ask(question) { answer ->
            if (generation != qwenGeneration) return@ask
            val elapsed = SystemClock.elapsedRealtime() - began
            inferenceTimeout?.let(ui::removeCallbacks)
            inferenceTimeout = null
            val errors = listOf("Modelo GGUF", "Motor nativo",
                "La inferencia local falló", "El motor nativo", "No se generó")
            val succeeded = answer.isNotBlank() && errors.none { answer.startsWith(it) }
            prefs.edit().putString("qwen", if (succeeded) "TEXTO_NO_VACIO_NO_CERTIFICADO"
                else "ERROR_INFERENCIA")
                .putLong("qwen_ms", elapsed).apply()
            status.text = if (succeeded)
                "QWEN_LOCAL_TEXT: respuesta no vacía en ${elapsed} ms. " +
                    "La exactitud semántica aún debe comprobarse por separado."
            else "QWEN_ERROR en ${elapsed} ms. Revisa si el GGUF funciona."
            localInference = null
            engine.close()
        }
        if (!accepted) {
            localInference = null
            engine.close()
            prefs.edit().putString("qwen", "SOLICITUD_RECHAZADA").apply()
            status.text = "La solicitud local fue rechazada; no se tomó ninguna medición."
            return
        }
        inferenceTimeout = Runnable {
            if (generation == qwenGeneration && localInference === engine) {
                qwenGeneration++
                engine.cancelActive()
                engine.close()
                localInference = null
                prefs.edit().putString("qwen", "TIMEOUT_240S")
                    .putLong("qwen_ms", SystemClock.elapsedRealtime() - began).apply()
                status.text = "QWEN_TIMEOUT: se solicitó cancelación al motor nativo."
            }
        }.also { ui.postDelayed(it, 240_000L) }
    }

    private fun redrawReportedResults() {
        resultColumn.removeAllViews()
        for (check in VivoCheck.entries) {
            val current = readReportedResult(check)
            resultColumn.addView(TextView(this).apply {
                text = "${check.name} · USER_REPORTED: $current"
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(4, 18, 4, 4)
            })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun option(text: String, value: VivoReportedResult) {
                row.addView(Button(this).apply {
                    this.text = text
                    isAllCaps = false
                    textSize = 11f
                    setOnClickListener {
                        prefs.edit().putString(check.name, value.name).apply()
                        redrawReportedResults()
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            option("No probado", VivoReportedResult.NO_PROBADO)
            option("Funciona", VivoReportedResult.FUNCIONA)
            option("Falló", VivoReportedResult.FALLA)
            resultColumn.addView(row)
        }
    }

    private fun readReportedResult(check: VivoCheck): VivoReportedResult =
        try {
            VivoReportedResult.valueOf(prefs.getString(check.name, null)
                ?: VivoReportedResult.NO_PROBADO.name)
        } catch (_: IllegalArgumentException) {
            VivoReportedResult.NO_PROBADO
        }

    private fun collectReport(): VivoDiagnosticReport {
        val health = DeviceHealthReader(this).read()
        val mem = Debug.MemoryInfo()
        try { Debug.getMemoryInfo(mem) } catch (_: RuntimeException) { }
        val audio = getSharedPreferences("jarvis_handsfree_diag_v1", MODE_PRIVATE)
        val confirmedScreenOff = audio.getString("lockscreen_result", "") ==
            "SCREEN_OFF_AUDIO_OBSERVED"
        val samples = audio.getLong("lockscreen_off_samples", 0L).coerceAtLeast(0L)
        return VivoDiagnosticReport(
            model = Build.MODEL,
            sdk = Build.VERSION.SDK_INT,
            batteryPercent = health.batteryPercent,
            thermalStatus = health.thermalStatus,
            availableRamMib = health.availableMemoryBytes?.div(1024L * 1024L),
            pssKib = mem.totalPss,
            microphoneGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
            notificationsGranted = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
            ggufImported = LocalGgufModelStore(this).hasModel(),
            screenOffSamples = samples,
            screenOffObserved = confirmedScreenOff && samples > 0,
            qwenRun = prefs.getString("qwen", "NO_PROBADO") ?: "NO_PROBADO",
            qwenLatencyMs = if (prefs.contains("qwen_ms")) prefs.getLong("qwen_ms", 0L)
                else null,
            results = VivoCheck.entries.associateWith(::readReportedResult)
        )
    }

    private fun updateSnapshot() {
        val report = collectReport().toShareText()
        snapshot.text = report
        status.text = HandsFreePreviewService.readDiagnostic(this)
    }

    override fun onResume() {
        super.onResume()
        ui.removeCallbacks(refresh)
        ui.post(refresh)
        if (::snapshot.isInitialized) updateSnapshot()
    }

    override fun onPause() {
        ui.removeCallbacks(refresh)
        // An explicitly started foreground voice service may remain active
        // while the user locks the display; Qwen direct probe does not.
        if (localInference != null) {
            qwenGeneration++
            localInference?.cancelActive()
            localInference?.close()
            localInference = null
            inferenceTimeout?.let(ui::removeCallbacks)
            inferenceTimeout = null
            prefs.edit().putString("qwen", "CANCELADO_AL_SALIR")
                .remove("qwen_ms").apply()
        }
        super.onPause()
    }

    override fun onDestroy() {
        ui.removeCallbacks(refresh)
        inferenceTimeout?.let(ui::removeCallbacks)
        localInference?.cancelActive()
        localInference?.close()
        localInference = null
        super.onDestroy()
    }
}
