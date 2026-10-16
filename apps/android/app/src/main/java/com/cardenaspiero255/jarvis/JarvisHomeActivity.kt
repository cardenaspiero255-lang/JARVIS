package com.cardenaspiero255.jarvis

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.cardenaspiero255.jarvis.core.JarvisHomeApp
import com.cardenaspiero255.jarvis.core.JarvisHomeCatalog
import java.util.Locale

/**
 * Reversible HOME launcher *on top of* Funtouch OS.
 *
 * The user, not JARVIS, chooses the default HOME app in Android settings.
 * Only explicitly tapped installed launcher activities are opened; no root,
 * shell commands, overlays, background capture, network or device privileges.
 */
class JarvisHomeActivity : Activity() {
    private val scarlet = Color.rgb(238, 48, 72)
    private val muted = Color.rgb(174, 181, 195)
    private var installedApps: List<JarvisHomeApp> = emptyList()
    private lateinit var appList: LinearLayout
    private lateinit var search: EditText
    private lateinit var catalogStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 11, 18)
        window.navigationBarColor = Color.rgb(9, 11, 18)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(20, 10, 19), Color.rgb(10, 12, 21), Color.rgb(6, 8, 14))
            )
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(column)
        }
        setContentView(scroll)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val identity = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        identity.addView(text("J A R V I S   O S", 26f, Color.WHITE, bold = true))
        identity.addView(text("HOME SHELL  /  V1  /  LOCAL", 10f, scarlet, bold = true))
        top.addView(identity, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(text("F U N T O U C H\nI N T A C T O", 9f, muted).apply {
            gravity = Gravity.END
            contentDescription = "Funtouch OS permanece instalado"
        })
        column.addView(top)

        column.addView(ReactorView().apply {
            contentDescription = "Núcleo visual de JARVIS OS. Es decorativo."
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)).apply {
            topMargin = dp(8)
        })
        column.addView(text("SISTEMA PERSONAL / SIN REEMPLAZAR ANDROID", 10f, scarlet, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = .10f
        })
        column.addView(text(
            "Tu asistente, tus aplicaciones y tus herramientas. Todo sobre Funtouch OS.",
            13f, muted
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(14), dp(12), dp(18))
        })

        val quickCard = card()
        quickCard.addView(text("CENTRO DE CONTROL", 12f, scarlet, true))
        quickCard.addView(text("JARVIS permanece bajo tu control.", 13f, muted).apply {
            setPadding(0, dp(6), 0, dp(4))
        })
        quickCard.addView(action("ABRIR JARVIS  /  IA Y VOZ") {
            // The preview APK uses a different applicationId: prefer the
            // user's existing JARVIS app, preserving its conversation store.
            val existing = if (packageName != ORIGINAL_JARVIS_PACKAGE) {
                packageManager.getLaunchIntentForPackage(ORIGINAL_JARVIS_PACKAGE)
            } else null
            try {
                startActivity(existing ?: Intent(this, MainActivity::class.java))
            } catch (_: ActivityNotFoundException) {
                showMessage("JARVIS no está disponible en este momento.")
            } catch (_: SecurityException) {
                showMessage("Android impidió abrir JARVIS.")
            }
        })
        quickCard.addView(action("GAMEHUB ULTRA  /  JUEGOS") {
            val intent = packageManager.getLaunchIntentForPackage(GAMEHUB_PACKAGE)
            if (intent == null) {
                showMessage("GameHub Ultra no está instalado o no es visible.")
            } else {
                try {
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    showMessage("No se pudo abrir GameHub Ultra.")
                } catch (_: SecurityException) {
                    showMessage("Android no permite abrir esa aplicación.")
                }
            }
        })
        column.addView(quickCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) })

        val drawer = card()
        drawer.addView(text("APLICACIONES INSTALADAS", 12f, scarlet, true))
        drawer.addView(text("Solo mostramos apps que Android permite iniciar. Nada se ejecuta automáticamente.", 12f, muted).apply {
            setPadding(0, dp(8), 0, dp(10))
        })
        search = EditText(this).apply {
            hint = "Buscar aplicaciones..."
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(64))
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = surface(Color.rgb(29, 28, 41), Color.rgb(102, 44, 64), 14f)
            minHeight = dp(54)
            contentDescription = "Buscar entre las aplicaciones instaladas"
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    showApps(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        drawer.addView(search, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        catalogStatus = text("Cargando aplicaciones...", 11f, muted).apply {
            setPadding(0, dp(12), 0, dp(8))
        }
        drawer.addView(catalogStatus)
        appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        drawer.addView(appList)
        column.addView(drawer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) })

        val systemCard = card()
        systemCard.addView(text("FUNTOUCH OS SIGUE AQUÍ", 12f, scarlet, true))
        systemCard.addView(text(
            "JARVIS OS es una pantalla de inicio opcional, no otra ROM. " +
                "Para volver al inicio de Vivo, cambia la aplicación de inicio predeterminada.",
            12f, muted
        ).apply { setPadding(0, dp(10), 0, dp(4)) })
        systemCard.addView(action("ELEGIR PANTALLA DE INICIO") {
            launchSettings(Settings.ACTION_HOME_SETTINGS)
        })
        systemCard.addView(action("AJUSTES ORIGINALES DE ANDROID") {
            launchSettings(Settings.ACTION_SETTINGS)
        })
        column.addView(systemCard)
        column.addView(text("JARVIS OS V1  ·  SIN ROOT  ·  SIN BORRAR DATOS", 10f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, dp(4))
        })
    }

    override fun onResume() {
        super.onResume()
        if (::appList.isInitialized) loadInstalledApps()
    }

    private fun loadInstalledApps() {
        val request = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(
                request, PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            packageManager.queryIntentActivities(request, 0)
        }
        installedApps = resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            if (!activity.exported || !activity.enabled) return@mapNotNull null
            JarvisHomeApp(
                activity.packageName,
                activity.name,
                info.loadLabel(packageManager)?.toString().orEmpty()
                    .replace(Regex("[\\p{Cc}\\p{Cf}]"), " ").trim().take(72)
            )
        }
        showApps(search.text?.toString().orEmpty())
    }

    private fun showApps(query: String) {
        if (!::appList.isInitialized || !::catalogStatus.isInitialized) return
        appList.removeAllViews()
        val filtered = JarvisHomeCatalog.visible(installedApps, query, packageName, 60)
        catalogStatus.text = when {
            filtered.isEmpty() -> "No hay aplicaciones coincidentes."
            query.isBlank() -> "Mostrando hasta 60 aplicaciones. Busca para encontrar más."
            else -> "Coincidencias: " + filtered.size + " (máximo 60 visibles)"
        }
        filtered.forEach { app ->
            appList.addView(action(app.label) { openInstalledApp(app) })
        }
    }

    private fun openInstalledApp(app: JarvisHomeApp) {
        // Explicitly launch only a component discovered from PackageManager
        // and selected with an actual user tap.
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(app.packageName, app.activityClassName)
            )
        } catch (_: ActivityNotFoundException) {
            showMessage("La aplicación ya no está disponible.")
            loadInstalledApps()
        } catch (_: SecurityException) {
            showMessage("Android impidió abrir esta aplicación.")
        }
    }

    private fun launchSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (_: ActivityNotFoundException) {
            if (action != Settings.ACTION_SETTINGS) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    showMessage("Abre Ajustes desde Funtouch OS.")
                }
            } else {
                showMessage("Abre Ajustes desde Funtouch OS.")
            }
        }
    }

    private fun showMessage(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = surface(Color.rgb(21, 20, 32), Color.rgb(70, 39, 58), 22f)
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    private fun action(caption: String, action: () -> Unit): TextView =
        text(caption, 13f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(52)
            setPadding(dp(16), dp(10), dp(12), dp(10))
            background = surface(Color.rgb(55, 24, 41), Color.rgb(116, 40, 66), 14f)
            isClickable = true
            isFocusable = true
            contentDescription = caption
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(9) }
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun surface(fill: Int, border: Int, cornerDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(cornerDp).toFloat()
            setStroke(dp(1), border)
        }

    private fun dp(size: Int) = (size * resources.displayMetrics.density).toInt()
    private fun dp(size: Float) = size * resources.displayMetrics.density

    /** Static reactor artwork: no continuously running animation or battery drain. */
    private inner class ReactorView : View(this@JarvisHomeActivity) {
        private val brush = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) * .39f
            brush.style = Paint.Style.STROKE
            brush.strokeWidth = dp(2f)
            brush.color = Color.rgb(88, 27, 47)
            canvas.drawCircle(cx, cy, radius, brush)
            canvas.drawCircle(cx, cy, radius * .69f, brush)
            val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            brush.color = scarlet
            brush.strokeWidth = dp(4f)
            canvas.drawArc(rect, -82f, 112f, false, brush)
            canvas.drawArc(rect, 50f, 55f, false, brush)
            canvas.drawArc(rect, 144f, 105f, false, brush)
            brush.strokeWidth = dp(1f)
            for (index in 0 until 32) {
                val a = Math.toRadians(index * 11.25)
                val x1 = cx + kotlin.math.cos(a).toFloat() * radius * .76f
                val y1 = cy + kotlin.math.sin(a).toFloat() * radius * .76f
                val x2 = cx + kotlin.math.cos(a).toFloat() * radius * .85f
                val y2 = cy + kotlin.math.sin(a).toFloat() * radius * .85f
                brush.color = if (index % 4 == 0) scarlet else Color.rgb(100, 41, 65)
                canvas.drawLine(x1, y1, x2, y2, brush)
            }
            brush.style = Paint.Style.FILL
            brush.color = Color.rgb(36, 14, 26)
            canvas.drawCircle(cx, cy, radius * .49f, brush)
            brush.color = scarlet
            brush.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            brush.textSize = radius * .68f
            brush.textAlign = Paint.Align.CENTER
            canvas.drawText("J", cx, cy - (brush.ascent() + brush.descent()) / 2f, brush)
            brush.color = Color.rgb(224, 143, 154)
            brush.textSize = radius * .12f
            canvas.drawText("O M E G A", cx, cy + radius * .73f, brush)
        }
    }

    private companion object {
        const val GAMEHUB_PACKAGE = "com.cardenaspiero255.gamehubultra"
        const val ORIGINAL_JARVIS_PACKAGE = "com.cardenaspiero255.jarvis"
    }
}
