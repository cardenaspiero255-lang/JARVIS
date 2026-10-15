package com.cardenaspiero255.jarvis

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * OMEGA design system. Native Android widgets with readable contrast,
 * large touch targets, no network or imported design SDK.
 */
object OmegaUiKit {
    private val scarlet = Color.rgb(239, 48, 72)
    private val slate = Color.rgb(195, 204, 222)

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun hero(context: Context): LinearLayout {
        val d = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(45, 15, 29), Color.rgb(17, 20, 35), Color.rgb(11, 17, 26)))
        d.cornerRadius = dp(context, 26).toFloat()
        d.setStroke(dp(context, 1), Color.rgb(118, 40, 59))
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = d
            setPadding(dp(context, 14), dp(context, 18), dp(context, 14), dp(context, 18))
        }
        root.addView(ImageView(context).apply {
            setImageResource(R.drawable.jarvis_reference)
            contentDescription = "Símbolo JARVIS OMEGA"
            layoutParams = LinearLayout.LayoutParams(dp(context, 72), dp(context, 72))
        })
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 8), 0, 0, 0)
        }
        column.addView(TextView(context).apply {
            text = "JARVIS"
            textSize = 29f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            letterSpacing = .08f
        })
        column.addView(TextView(context).apply {
            text = "O M E G A   /   A S I S T E N T E"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(scarlet)
        })
        column.addView(TextView(context).apply {
            text = "Local  ·  Seguro  ·  Bajo tu control"
            textSize = 11.5f
            setTextColor(slate)
        })
        root.addView(column, LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return root
    }

    fun quickActions(
        context: Context, onChat: () -> Unit, onVision: () -> Unit,
        onGame: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val actions = listOf(
            Triple("CHAT", "✦", onChat),
            Triple("VISIÓN", "◉", onVision),
            Triple("GAMEHUB", "▶", onGame)
        )
        for ((label, symbol, action) in actions) {
            val badge = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(34, 26, 43), Color.rgb(23, 25, 42)))
                .apply {
                    cornerRadius = dp(context, 14).toFloat()
                    setStroke(dp(context, 1), Color.rgb(122, 46, 70))
                }
            row.addView(TextView(context).apply {
                text = "$symbol $label"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(250, 226, 230))
                gravity = Gravity.CENTER
                background = badge
                minHeight = dp(context, 48)
                setOnClickListener { action() }
                contentDescription = "Ir a $label"
            }, LinearLayout.LayoutParams(0, dp(context, 48), 1f)
                .apply { marginStart = dp(context, 3); marginEnd = dp(context, 3) })
        }
        return row
    }

    fun createSection(context: Context, parent: LinearLayout): LinearLayout {
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(21, 24, 37), Color.rgb(13, 17, 27)))
        bg.cornerRadius = dp(context, 22).toFloat()
        bg.setStroke(dp(context, 1), Color.rgb(53, 56, 76))
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(dp(context, 14), dp(context, 14),
                dp(context, 14), dp(context, 18))
        }
        parent.addView(content, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(context, 20) })
        return content
    }

    fun statusSurface(context: Context): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.rgb(20, 29, 44))
            cornerRadius = dp(context, 18).toFloat()
            setStroke(dp(context, 1), Color.rgb(63, 75, 99))
        }

    fun sectionTitle(context: Context, value: String): TextView {
        val surface = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(51, 22, 39), Color.rgb(21, 25, 37)))
        surface.cornerRadius = dp(context, 15).toFloat()
        surface.setStroke(dp(context, 1), Color.rgb(102, 43, 61))
        return TextView(context).apply {
            text = value
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .03f
            setTextColor(Color.rgb(255, 112, 124))
            setPadding(dp(context, 16), dp(context, 15),
                dp(context, 12), dp(context, 15))
            background = surface
        }
    }

    fun buttonBackground(context: Context, caption: String): GradientDrawable {
        val sensitive = caption.contains("PRIVACIDAD") ||
            caption.contains("BORRAR HISTORIAL") ||
            caption.contains("DETENER")
        val colors = if (sensitive)
            intArrayOf(Color.rgb(71, 26, 40), Color.rgb(45, 22, 34))
        else intArrayOf(Color.rgb(193, 27, 52), Color.rgb(117, 25, 48))
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = dp(context, 15).toFloat()
            setStroke(dp(context, 1),
                if (sensitive) Color.rgb(129, 61, 77) else Color.rgb(213, 72, 90))
        }
    }
}
