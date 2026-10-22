package com.cardenaspiero255.jarvis

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cardenaspiero255.jarvis.core.VivoGgufEvidenceResetPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android SharedPreferences durability check. No model, microphone,
 * audio, network or Activity is involved; the original QA state is restored.
 */
@RunWith(AndroidJUnit4::class)
class VivoGgufEvidencePersistenceInstrumentedTest {
    @Test fun aModelChangeResetSurvivesLosingTheImportActivityCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "jarvis_vivo_one_install_diag_v1"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val keys = VivoGgufEvidenceResetPlan.changesForModelMutation().keys +
            setOf("stop_status", "OWNER")
        val before = prefs.all.filterKeys { it in keys }
        try {
            assertTrue(prefs.edit()
                .putString("qwen", "TEXTO_NO_VACIO_NO_CERTIFICADO")
                .putLong("qwen_ms", 1234L)
                .putString("QWEN", "FUNCIONA")
                .putString("BARGE_IN", "FUNCIONA")
                .putString("ECHO_REJECTION", "FUNCIONA")
                .putString("stop_status", "STOP_SERVICIO_FINALIZADO")
                .putString("OWNER", "NO_PROBADO").commit())
            assertTrue(VivoGgufEvidenceResetPlan.invalidate(context))
            val reloaded = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            assertEquals("NO_PROBADO", reloaded.getString("qwen", "not reset"))
            assertTrue(!reloaded.contains("qwen_ms"))
            for (key in listOf("QWEN", "BARGE_IN", "ECHO_REJECTION")) {
                assertEquals("NO_PROBADO", reloaded.getString(key, null))
            }
            assertEquals("STOP_SERVICIO_FINALIZADO",
                reloaded.getString("stop_status", null))
            assertEquals("NO_PROBADO", reloaded.getString("OWNER", null))
        } finally {
            val reset = prefs.edit()
            for (key in keys) {
                when (val previous = before[key]) {
                    is String -> reset.putString(key, previous)
                    is Long -> reset.putLong(key, previous)
                    is Int -> reset.putInt(key, previous)
                    is Boolean -> reset.putBoolean(key, previous)
                    else -> reset.remove(key)
                }
            }
            assertTrue(reset.commit())
        }
    }
}
