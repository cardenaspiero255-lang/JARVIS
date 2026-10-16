package com.cardenaspiero255.jarvis

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.cardenaspiero255.jarvis.core.CompanionConnectionPolicy
import com.cardenaspiero255.jarvis.core.CompanionEvidence
import com.cardenaspiero255.jarvis.core.CompanionKind
import com.cardenaspiero255.jarvis.core.CompanionState
import rikka.shizuku.Shizuku

/**
 * Consent-first companion control centre. Discovery and opening companions are
 * the only actions here; detecting a package never executes its privileged API.
 */
class CompanionCenterActivity : Activity() {
    private val policy = CompanionConnectionPolicy()
    private val darkSurface = Color.rgb(10, 12, 20)
    private val accent = Color.rgb(239, 59, 81)
    private lateinit var content: LinearLayout
    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> redraw() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        super.onDestroy()
    }

    private fun redraw() {
        if (!::content.isInitialized || isFinishing) return
        content.removeAllViews()
        addLine("CONEXIONES JARVIS OS", 23f, Color.WHITE, true)
        addLine("Sin root. Funtouch OS permanece intacto.", 13f, accent)
        addLine(
            "Detectar una aplicación NO significa que tenga permisos operativos. " +
                "Solo Shizuku comprueba aquí su servicio y autorización real. " +
                "Dhizuku, OwnDroid y Termux requieren configuración y verificación " +
                "adicional antes de ejecutar cualquier comando.", 13f, Color.LTGRAY
        )
        CompanionKind.values().forEach { kind ->
            val companionPackage = when (kind) {
                CompanionKind.SHIZUKU -> "moe.shizuku.privileged.api"
                CompanionKind.DHIZUKU -> "com.rosan.dhizuku"
                CompanionKind.OWNDROID -> "com.bintianqi.owndroid"
                CompanionKind.TERMUX -> "com.termux"
            }
            val installed = isInstalled(companionPackage)
            val evidence = if (kind == CompanionKind.SHIZUKU) shizukuEvidence(installed)
                           else CompanionEvidence(installed)
            val state = policy.state(kind, evidence)
            addLine(kind.name, 18f, Color.WHITE, true)
            addLine(when (state) {
                CompanionState.NOT_INSTALLED -> "No instalada o no visible para Android"
                CompanionState.NEEDS_START -> "Instalada; inicia el servicio Shizuku"
                CompanionState.NEEDS_PERMISSION -> "Servicio activo; falta autorización a JARVIS"
                CompanionState.NEEDS_SETUP -> "Detectada; integración privilegiada aún no configurada"
                CompanionState.READY -> "Shizuku conectado y permiso concedido; acciones aún bloqueadas"
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
        }
        addLine(
            "Privilegios de administración, scripts y ADB: deshabilitados " +
                "hasta que sus adaptadores tengan pruebas y autorización específica.",
            12f, accent
        )
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
