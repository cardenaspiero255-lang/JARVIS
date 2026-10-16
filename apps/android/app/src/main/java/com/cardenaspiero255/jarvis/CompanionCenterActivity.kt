package com.cardenaspiero255.jarvis

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.cardenaspiero255.jarvis.core.CompanionConnectionPolicy
import com.cardenaspiero255.jarvis.core.CompanionEvidence
import com.cardenaspiero255.jarvis.core.CompanionKind
import com.cardenaspiero255.jarvis.core.CompanionState
import com.cardenaspiero255.jarvis.core.SafeAutomationEngine
import com.cardenaspiero255.jarvis.core.SafeAutomationOutcome
import com.cardenaspiero255.jarvis.core.SafeRoutine
import rikka.shizuku.Shizuku

/**
 * Consent-first companion control centre. Discovery and opening companions are
 * the only actions here; detecting a package never executes its privileged API.
 */
class CompanionCenterActivity : Activity() {
    private val policy = CompanionConnectionPolicy()
    private lateinit var adapters: CompanionActionAdapters
    private lateinit var automation: SafeAutomationEngine
    private val darkSurface = Color.rgb(10, 12, 20)
    private val accent = Color.rgb(239, 59, 81)
    private lateinit var content: LinearLayout
    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> redraw() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapters = CompanionActionAdapters(this)
        automation = SafeAutomationEngine(adapters)
        window.statusBarColor = darkSurface
        window.navigationBarColor = darkSurface
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(darkSurface)
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        setContentView(ScrollView(this).apply { addView(content) })
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        redraw()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) redraw()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        if (::automation.isInitialized) automation.stop()
        super.onDestroy()
    }

    private fun redraw() {
        if (!::content.isInitialized || isFinishing) return
        content.removeAllViews()
        addLine("CONEXIONES JARVIS OS", 23f, Color.WHITE, true)
        addLine("Sin root. Funtouch OS permanece intacto.", 13f, accent)
        addLine(
            "Detectar una aplicación NO significa que tenga permisos operativos. " +
                "Shizuku y Dhizuku comprueban aquí sus permisos de SDK. " +
                "OwnDroid requiere clave propia y Termux necesita permisos externos; " +
                "detectar una APK no demuestra que sus comandos funcionen.", 13f, Color.LTGRAY
        )
        CompanionKind.values().forEach { kind ->
            val companionPackage = when (kind) {
                CompanionKind.SHIZUKU -> "moe.shizuku.privileged.api"
                CompanionKind.DHIZUKU -> "com.rosan.dhizuku"
                CompanionKind.OWNDROID -> "com.bintianqi.owndroid"
                CompanionKind.TERMUX -> "com.termux"
            }
            val installed = isInstalled(companionPackage)
            val evidence = when (kind) {
                CompanionKind.SHIZUKU -> shizukuEvidence(installed)
                CompanionKind.DHIZUKU -> adapters.dhizukuEvidence(installed)
                else -> CompanionEvidence(installed)
            }
            val state = policy.state(kind, evidence)
            addLine(kind.name, 18f, Color.WHITE, true)
            addLine(when (state) {
                CompanionState.NOT_INSTALLED -> "No instalada o no visible para Android"
                CompanionState.NEEDS_START -> "Instalada; inicia el servicio correspondiente"
                CompanionState.NEEDS_PERMISSION -> "Servicio activo; falta autorización a JARVIS"
                CompanionState.NEEDS_SETUP -> "Detectada; integración privilegiada aún no configurada"
                CompanionState.READY -> "API autorizada; políticas administrativas no habilitadas"
            }, 12f, if (state == CompanionState.READY) Color.GREEN else Color.LTGRAY)

            if (policy.canOpenCompanion(installed, explicitTap = true)) {
                action("ABRIR " + kind.name) { openCompanion(companionPackage) }
            }
            if (kind == CompanionKind.SHIZUKU &&
                state == CompanionState.NEEDS_PERMISSION) {
                action("AUTORIZAR JARVIS EN SHIZUKU") {
                    try {
                        if (Shizuku.pingBinder()) Shizuku.requestPermission(1217)
                        else message("Primero inicia el servicio Shizuku.")
                    } catch (_: RuntimeException) {
                        message("Shizuku no está disponible.")
                    }
                }
            }
            if (kind == CompanionKind.DHIZUKU &&
                state == CompanionState.NEEDS_PERMISSION) {
                action("AUTORIZAR JARVIS EN DHIZUKU") {
                    if (!adapters.requestDhizukuPermission { redraw() }) {
                        message("Dhizuku no está activado o rechazó la solicitud.")
                    }
                }
            }
            if (kind == CompanionKind.TERMUX && installed) {
                action("TERMUX: PROBAR COMANDO LOCAL id") { confirmTermuxCheck() }
            }
            if (kind == CompanionKind.OWNDROID && installed) {
                action("OWNDROID: BLOQUEAR PANTALLA (CONFIRMAR)") { confirmOwnDroidLock() }
            }
        }
        addLine("MOTOR LOCAL DE AUTOMATIZACIÓN", 17f, Color.WHITE, true)
        addLine("Rutinas fijas, autorización de un solo uso, sin Tasker. " +
            "Las palabras reconocidas por voz nunca ejecutan por sí mismas.", 12f, Color.LTGRAY)
        action("ELEGIR RUTINA SEGURA") { selectRoutine() }
        action("PARADA DE EMERGENCIA  /  CANCELAR RUTINA") {
            automation.stop()
            message("Rutinas detenidas y autorización pendiente revocada.")
        }
        addLine(
            "No hay root ni cambios de Device Owner, borrado de datos, políticas " +
                "administrativas automáticas o shell arbitraria.",
            12f, accent
        )
    }

    private fun selectRoutine() {
        val choices = SafeRoutine.values()
        AlertDialog.Builder(this)
            .setTitle("Automatizaciones locales")
            .setItems(choices.map { it.name.replace('_', ' ') }.toTypedArray()) { _, index ->
                confirmRoutine(choices[index])
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun confirmRoutine(routine: SafeRoutine) {
        val preview = automation.prepare(routine)
        AlertDialog.Builder(this)
            .setTitle("Autorizar UNA ejecución")
            .setMessage(preview + "\\n\\nEsta autorización caduca al ejecutar o cancelar.")
            .setNegativeButton("CANCELAR") { _, _ -> automation.stop() }
            .setOnCancelListener { automation.stop() }
            .setPositiveButton("AUTORIZAR UNA VEZ") { _, _ ->
                val report = automation.confirmAndRun(routine, explicitTap = true)
                val status = when (report.outcome) {
                    SafeAutomationOutcome.COMPLETED -> "Acciones locales solicitadas"
                    SafeAutomationOutcome.FAILED -> "Detenida por falta de acceso"
                    SafeAutomationOutcome.STOPPED -> "Detenida por el usuario"
                    SafeAutomationOutcome.DENIED -> "Autorización no válida"
                }
                message("$status: ${report.completedSteps}/${report.totalSteps}.")
            }
            .show()
    }

    private fun confirmTermuxCheck() {
        AlertDialog.Builder(this)
            .setTitle("Prueba aislada de Termux")
            .setMessage("Se solicitará ÚNICAMENTE /data/data/com.termux/files/usr/bin/id, " +
                "sin argumentos ni scripts. Requiere permiso RUN_COMMAND y " +
                "allow-external-apps=true en Termux. El envío NO demuestra la ejecución.")
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("AUTORIZAR UNA VEZ") { _, _ ->
                if (adapters.requestTermuxIdentityCheck()) {
                    message("Solicitud enviada a Termux; resultado NO verificado.")
                } else {
                    message("Falta permiso o Termux rechazó iniciar la prueba.")
                }
            }
            .show()
    }

    private fun confirmOwnDroidLock() {
        val keyInput = EditText(this).apply {
            hint = "Clave API configurada en OwnDroid"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            maxLines = 1
        }
        AlertDialog.Builder(this)
            .setTitle("OwnDroid — bloqueo de pantalla")
            .setMessage("Escribe tu clave API. JARVIS no la guardará. " +
                "OwnDroid debe tener API habilitada. Es una petición administrativa.")
            .setView(keyInput)
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("SIGUIENTE") { _, _ ->
                val ephemeralKey = keyInput.text.toString()
                if (ephemeralKey.isBlank() || ephemeralKey.length > 256) {
                    message("Clave API no válida.")
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("¿Bloquear el dispositivo ahora?")
                        .setMessage("OwnDroid recibirá la orden LOCK. " +
                            "La pantalla puede bloquearse y necesitar tu PIN.")
                        .setNegativeButton("CANCELAR", null)
                        .setPositiveButton("CONFIRMAR BLOQUEO") { _, _ ->
                            if (adapters.sendExplicitOwnDroidLock(ephemeralKey)) {
                                message("Solicitud LOCK enviada; ejecución NO verificada.")
                            } else {
                                message("OwnDroid no aceptó el envío.")
                            }
                        }
                        .show()
                }
            }
            .show()
    }

    private fun shizukuEvidence(installed: Boolean): CompanionEvidence {
        if (!installed) return CompanionEvidence(false)
        val running = try { Shizuku.pingBinder() }
                      catch (_: RuntimeException) { false }
        val authorized = if (!running) false else try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: RuntimeException) { false }
        return CompanionEvidence(installed, running, authorized)
    }

    private fun isInstalled(packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(
                packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun openCompanion(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) startActivity(intent)
            else message("Android no ofrece una pantalla accesible de esa aplicación.")
        } catch (_: ActivityNotFoundException) {
            message("La aplicación ya no está disponible.")
        } catch (_: SecurityException) {
            message("Android bloqueó la apertura.")
        }
    }

    private fun action(title: String, onTap: () -> Unit) {
        val button = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 13f
            minHeight = dp(52)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(7), dp(14), dp(7))
            setBackgroundColor(Color.rgb(81, 26, 44))
            isClickable = true
            isFocusable = true
            setOnClickListener { onTap() }
        }
        content.addView(button, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8); bottomMargin = dp(12) })
    }

    private fun addLine(title: String, fontSize: Float, color: Int, bold: Boolean = false) {
        content.addView(TextView(this).apply {
            text = title
            textSize = fontSize
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(9), 0, dp(5))
        })
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun message(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
