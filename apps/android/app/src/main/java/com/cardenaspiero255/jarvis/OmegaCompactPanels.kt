package com.cardenaspiero255.jarvis

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A purely visual accordion around the EXISTING OMEGA controls.
 * Moving a view preserves its onClick listener and permission checks.
 * Opening a group never invokes a child action, Android intent or permission.
 */
internal class OmegaCompactPanels(
    private val context: Context,
    private val root: LinearLayout,
    private val onOpen: (View) -> Unit
) {
    private data class Group(
        val heading: LinearLayout,
        val content: LinearLayout,
        val caret: TextView
    )

    private val groups = linkedMapOf<String, Group>()
    private val accent = Color.rgb(237, 54, 79)
    private val secondary = Color.rgb(167, 176, 193)

    fun addGroup(title: String, views: List<View>) {
        require(title.isNotBlank())
        require(!groups.containsKey(title))
        val shell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = surface(22, 23, 37, 62, 40, 59)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val heading = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(56)
            isClickable = true
            isFocusable = true
            contentDescription = "Abrir o cerrar $title"
        }
        val name = TextView(context).apply {
            text = title
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            letterSpacing = .025f
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = title
        }
        val caret = TextView(context).apply {
            text = "＋"
            textSize = 22f
            setTextColor(accent)
            gravity = Gravity.CENTER
        }
        heading.addView(name, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        heading.addView(caret, LinearLayout.LayoutParams(dp(40), dp(48)))
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        // A group relocates the very same Android View instances, never clones
        // them or bypasses their existing callbacks and consent gates.
        views.forEach { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            content.addView(view, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })
        }
        heading.setOnClickListener { toggle(title) }
        name.setOnClickListener { toggle(title) }
        caret.setOnClickListener { toggle(title) }
        shell.addView(heading)
        shell.addView(content)
        root.addView(shell, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })
        groups[title] = Group(heading, content, caret)
    }

    fun show(title: String) {
        val chosen = groups[title] ?: return
        groups.forEach { (key, group) ->
            val selected = key == title
            group.content.visibility = if (selected) View.VISIBLE else View.GONE
            group.caret.text = if (selected) "−" else "＋"
            group.caret.setTextColor(if (selected) accent else secondary)
            group.heading.contentDescription = if (selected) "Cerrar $key" else "Abrir $key"
        }
        onOpen(chosen.heading)
    }

    private fun toggle(title: String) {
        val group = groups[title] ?: return
        if (group.content.visibility == View.VISIBLE) {
            group.content.visibility = View.GONE
            group.caret.text = "＋"
            group.heading.contentDescription = "Abrir $title"
        } else {
            show(title)
        }
    }

    private fun surface(r: Int, g: Int, b: Int, sr: Int, sg: Int, sb: Int) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(r, g, b), Color.rgb(14, 16, 27))
        ).apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.rgb(sr, sg, sb))
        }

    private fun dp(value: Int) =
        (value * context.resources.displayMetrics.density).toInt()
}
