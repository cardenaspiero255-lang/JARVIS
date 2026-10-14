package com.cardenaspiero255.jarvis

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

import com.cardenaspiero255.jarvis.core.DeviceActionPolicy
import com.cardenaspiero255.jarvis.core.HandsStatus
import com.cardenaspiero255.jarvis.core.DeviceOperation
import com.cardenaspiero255.jarvis.core.VoiceAction
import com.cardenaspiero255.jarvis.core.VoiceCommandParser

/**
 * User-operated Android shell. No background microphone, accessibility
 * execution, remote API, or automatic device actions are enabled.
 */
class MainActivity : Activity() {
    private val policy = DeviceActionPolicy()
    private val parser = VoiceCommandParser()
    private lateinit var statusText: TextView
    private lateinit var handsStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
            setBackgroundColor(Color.rgb(10, 10, 15))
        }
        val heading = label("JARVIS OMEGA", 30f, Color.rgb(239, 43, 54)).apply {
            typeface = Typeface.DEFAULT_BOLD
        }
        layout.addView(heading)
        layout.addView(label("SSS+  |  NÚCLEO ANDROID 0.1", 12f,
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
        layout.addView(label(
            "Puedes consultar el estado mediante voz o abrir los ajustes de accesibilidad. " +
                "JARVIS todavía no controla otras aplicaciones.",
            14f, Color.rgb(164, 166, 174)
        ).apply { setPadding(0, dp(24), 0, dp(16)) })

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

        layout.addView(primaryButton("HABLAR CON JARVIS") { startSpeech() })
        layout.addView(primaryButton("ABRIR AJUSTES DE ACCESIBILIDAD") {
            openAccessibilitySettings(explicitUserGesture = true)
        })
        layout.addView(label(
            "Privacidad: el reconocimiento de voz lo realiza la aplicación de voz de Android. " +
                "Puede requerir conexión y usar un servicio externo. " +
                "JARVIS no graba de forma continua ni guarda transcripciones.",
            12f, Color.rgb(159, 161, 171)
        ).apply { setPadding(0, dp(24), 0, 0) })

        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(layout)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (::handsStatusText.isInitialized) refreshHandsStatus()
    }

    private fun handsStatus(): String = HandsStatus.present(
        OmegaHandsService.isConnected(),
        OmegaHandsService.isStopped(this)
    )

    private fun refreshHandsStatus() {
        handsStatusText.text = handsStatus()
    }

    private fun startSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla con JARVIS")
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, SPEECH_REQUEST)
        } catch (_: ActivityNotFoundException) {
            statusText.text = "No hay un proveedor de reconocimiento de voz disponible."
        } catch (_: SecurityException) {
            statusText.text = "El reconocimiento de voz no tiene permisos suficientes."
        }
    }

    @Deprecated("Uses the platform Activity result API without additional dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SPEECH_REQUEST) return
        if (resultCode != RESULT_OK) {
            statusText.text = "Escucha cancelada. No se ejecutó ninguna acción."
            return
        }
        val transcript = data?.getStringArrayListExtra(
            RecognizerIntent.EXTRA_RESULTS
        )?.firstOrNull()
        if (transcript.isNullOrBlank()) {
            statusText.text = "No se reconoció ningún comando."
            return
        }
        when (parser.parse(transcript)) {
            VoiceAction.READ_STATUS -> {
                if (policy.canExecute(DeviceOperation.READ_LOCAL_STATUS,
                        explicitUserGesture = false)) {
                    statusText.text = "JARVIS operativo en modo observador. " +
                        "Integraciones remotas y automatización aún no habilitadas."
                }
            }
            VoiceAction.OPEN_ACCESSIBILITY_SETTINGS ->
                openAccessibilitySettings(explicitUserGesture = true)
            VoiceAction.UNSUPPORTED ->
                statusText.text = "Orden no disponible. No se realizó ninguna acción."
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
    }
}
