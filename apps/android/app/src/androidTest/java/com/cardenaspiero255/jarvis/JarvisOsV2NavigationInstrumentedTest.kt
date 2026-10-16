package com.cardenaspiero255.jarvis

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** User-visible navigation contract; never chooses Android's default launcher. */
@RunWith(AndroidJUnit4::class)
class JarvisOsV2NavigationInstrumentedTest {
    private fun findText(root: View, label: String): TextView? {
        if (root is TextView && root.text.toString() == label) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findText(root.getChildAt(i), label)
                if (found != null) return found
            }
        }
        return null
    }

    @Test fun fourModulesHideOtherModulesUntilExplicitTap() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val activity = instrument.startActivitySync(
            Intent(instrument.targetContext, JarvisHomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ) as JarvisHomeActivity
        try {
            instrument.waitForIdleSync()
            instrument.runOnMainSync {
                val root = activity.window.decorView
                listOf("JARVIS", "APPS", "GAMING", "SISTEMA").forEach { module ->
                    assertTrue("Module must be visible: $module", findText(root, module)?.isShown == true)
                }
                assertTrue(findText(root, "ABRIR JARVIS  /  IA Y VOZ")?.isShown == true)
                assertTrue("Original JARVIS is still accessible",
                    findText(root, "JARVIS ORIGINAL  /  HISTORIAL ANTERIOR")?.isShown == true)
                assertFalse("Apps drawer must be collapsed", findText(root, "APLICACIONES INSTALADAS")?.isShown == true)
                assertFalse("System tools are opt-in", findText(root, "ELEGIR PANTALLA DE INICIO")?.isShown == true)
                assertFalse("Game controls must be collapsed", findText(root, "GAMEHUB ULTRA  /  JUEGOS")?.isShown == true)

                assertTrue("Apps tab clickable", findText(root, "APPS")!!.performClick())
                assertTrue("Apps drawer visible after tap", findText(root, "APLICACIONES INSTALADAS")?.isShown == true)
                assertFalse("Assistant panel closed", findText(root, "ABRIR JARVIS  /  IA Y VOZ")?.isShown == true)

                assertTrue(findText(root, "GAMING")!!.performClick())
                assertTrue(findText(root, "GAMEHUB ULTRA  /  JUEGOS")?.isShown == true)
                assertFalse(findText(root, "APLICACIONES INSTALADAS")?.isShown == true)

                assertTrue(findText(root, "SISTEMA")!!.performClick())
                assertTrue(findText(root, "ELEGIR PANTALLA DE INICIO")?.isShown == true)
                assertTrue(findText(root, "AJUSTES ORIGINALES DE ANDROID")?.isShown == true)

                assertTrue(findText(root, "JARVIS")!!.performClick())
                assertTrue(findText(root, "ABRIR JARVIS  /  IA Y VOZ")?.isShown == true)
            }
        } finally {
            instrument.runOnMainSync { activity.finish() }
            instrument.waitForIdleSync()
        }
    }

    @Test fun gamingLinkRevealsAdvisorWithoutRunningGamingActions() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val activity = instrument.startActivitySync(
            Intent(instrument.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("jarvis.omega.ui.panel", "JUEGOS Y RENDIMIENTO")
        ) as MainActivity
        try {
            instrument.waitForIdleSync()
            instrument.runOnMainSync {
                val root = activity.window.decorView
                assertTrue(findText(root, "EVALUAR ANTES DE JUGAR")?.isShown == true)
                assertFalse(findText(root, "ENVIAR MENSAJE")?.isShown == true)
                assertFalse(findText(root, "PERMITIR CAPTURAS 60 S — DEPURACIÓN")?.isShown == true)
            }
        } finally {
            instrument.runOnMainSync { activity.finish() }
            instrument.waitForIdleSync()
        }
    }

    @Test fun omegaAssistantStartsWithAdvancedControlsCollapsedButKeepsSafetyAction() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val activity = instrument.startActivitySync(
            Intent(instrument.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ) as MainActivity
        try {
            instrument.waitForIdleSync()
            instrument.runOnMainSync {
                val root = activity.window.decorView
                assertTrue("Emergency privacy lock always visible",
                    findText(root, "BLOQUEAR SESIÓN — PRIVACIDAD")?.isShown == true)
                assertTrue("Chat group affordance visible", findText(root, "CHAT Y MEMORIA")?.isShown == true)
                assertTrue("Game group affordance visible", findText(root, "JUEGOS Y RENDIMIENTO")?.isShown == true)
                assertFalse("One-tap erase not exposed initially",
                    findText(root, "BORRAR HISTORIAL Y CLAVE CIFRADA")?.isShown == true)
                assertFalse("Capture exception stays collapsed",
                    findText(root, "PERMITIR CAPTURAS 60 S — DEPURACIÓN")?.isShown == true)
                assertFalse("Chat send collapsed until entered",
                    findText(root, "ENVIAR MENSAJE")?.isShown == true)

                assertTrue(findText(root, "CHAT Y MEMORIA")!!.performClick())
                assertTrue(findText(root, "ENVIAR MENSAJE")?.isShown == true)
                assertFalse(findText(root, "PERMITIR CAPTURAS 60 S — DEPURACIÓN")?.isShown == true)
                assertTrue(findText(root, "JUEGOS Y RENDIMIENTO")!!.performClick())
                assertTrue(findText(root, "EVALUAR ANTES DE JUGAR")?.isShown == true)
                assertFalse(findText(root, "ENVIAR MENSAJE")?.isShown == true)
            }
        } finally {
            instrument.runOnMainSync { activity.finish() }
            instrument.waitForIdleSync()
        }
    }
}
