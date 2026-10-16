package com.cardenaspiero255.jarvis

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Tests preview UI without switching default HOME or modifying Android. */
@RunWith(AndroidJUnit4::class)
class JarvisHomeInstrumentedTest {
    private fun findLabel(view: View, caption: String): TextView? {
        if (view is TextView && view.text.toString() == caption) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val label = findLabel(view.getChildAt(index), caption)
                if (label != null) return label
            }
        }
        return null
    }

    @Test fun reversibleHomePreviewExposesSafeControls() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, JarvisHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as JarvisHomeActivity
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val root = activity.window.decorView
                assertNotNull(findLabel(root, "ABRIR JARVIS  /  IA Y VOZ"))
                assertNotNull(findLabel(root, "GAMEHUB ULTRA  /  JUEGOS"))
                assertNotNull(findLabel(root, "ELEGIR PANTALLA DE INICIO"))
                assertNotNull(findLabel(root, "AJUSTES ORIGINALES DE ANDROID"))
                assertNotNull(findLabel(root, "APLICACIONES INSTALADAS"))
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }
}
