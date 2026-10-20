package com.cardenaspiero255.jarvis

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.cardenaspiero255.jarvis.core.GuidedTutorialSession
import com.cardenaspiero255.jarvis.core.TutorialTopic

/**
 * A fully offline, display-only tutorial. Steps NEVER run a command,
 * grant a permission, open a companion app or read microphone/screen.
 */
class GuidedTutorialActivity : Activity() {
    private val session = GuidedTutorialSession(TutorialTopic.OFFLINE_VOICE)
    private lateinit var selection: TextView
    private lateinit var stepTitle: TextView
    private lateinit var instructions: TextView
    private lateinit var expected: TextView
    private lateinit var progress: TextView
    private lateinit var back: Button
    private lateinit var next: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Color.rgb(9, 11, 19)
        window.navigationBarColor = Color.rgb(9, 11, 19)
        val restored = savedInstanceState?.getString("tutorial_topic")
        val topic = TutorialTopic.values().firstOrNull { it.name == restored }
            ?: TutorialTopic.OFFLINE_VOICE
        session.select(topic)
        val restoredPosition = (savedInstanceState?.getInt("tutorial_position") ?: 0)
            .coerceIn(0, session.guide.steps.lastIndex)
        repeat(restoredPosition) { session.next() }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
            setBackgroundColor(Color.rgb(9, 11, 19))
        }
        val scroll = ScrollView(this).apply { addView(panel) }
        setContentView(scroll)
        panel.addView(line("JARVIS · GUÍAS INTERACTIVAS", 21f, Color.WHITE, true))
        panel.addView(line(
            "Modo tutorial local. Solo muestra instrucciones: no concede permisos ni ejecuta órdenes.",
            13f, Color.LTGRAY
        ))
        selection = line("", 17f, Color.rgb(239, 82, 111), true)
        panel.addView(selection)
        panel.addView(button("ELEGIR GUÍA") {
            val topics = TutorialTopic.values()
            val labels = topics.map { com.cardenaspiero255.jarvis.core.GuidedTutorialCatalog.get(it).title }
            AlertDialog.Builder(this)
                .setTitle("Seleccionar una guía")
                .setItems(labels.toTypedArray()) { _, which ->
                    session.select(topics[which])
                    redraw()
                }
                .setNegativeButton("CANCELAR", null)
                .show()
        })
        progress = line("", 13f, Color.LTGRAY)
        panel.addView(progress)
        stepTitle = line("", 19f, Color.WHITE, true)
        panel.addView(stepTitle)
        instructions = line("", 16f, Color.rgb(222, 223, 228))
        panel.addView(instructions)
        panel.addView(line("QUÉ DEBERÍAS OBSERVAR", 12f, Color.rgb(239, 82, 111), true))
        expected = line("", 15f, Color.LTGRAY)
        panel.addView(expected)
        back = button("PASO ANTERIOR") { if (session.previous()) redraw() }
        next = button("SIGUIENTE PASO") { if (session.next()) redraw() }
        panel.addView(back)
        panel.addView(next)
        panel.addView(button("CERRAR GUÍA") { finish() })
        redraw()
    }

    private fun redraw() {
        selection.text = session.guide.title
        progress.text = "PASO " + session.progress()
        stepTitle.text = session.step.title
        instructions.text = session.step.instruction
        expected.text = session.step.expectedResult
        back.isEnabled = session.position > 0
        next.isEnabled = session.position < session.guide.steps.lastIndex
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("tutorial_topic", session.topic.name)
        outState.putInt("tutorial_position", session.position)
        super.onSaveInstanceState(outState)
    }

    private fun line(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(12), dp(4), dp(12))
        }

    private fun button(value: String, tap: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        setOnClickListener { tap() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
