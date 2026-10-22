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
        val voicePrefs = context.getSharedPreferences(
            "jarvis_handsfree_diag_v1", Context.MODE_PRIVATE)
        val voiceKeys = setOf("voice_gguf_success_count", "voice_gguf_last_latency_ms",
            "barge_in_question_count", "tts_started_count")
        val beforeVoice = voicePrefs.all.filterKeys { it in voiceKeys }
        try {
            assertTrue(prefs.edit()
                .putString("qwen", "TEXTO_NO_VACIO_NO_CERTIFICADO")
                .putLong("qwen_ms", 1234L)
                .putString("QWEN", "FUNCIONA")
                .putString("BARGE_IN", "FUNCIONA")
                .putString("ECHO_REJECTION", "FUNCIONA")
                .putString("stop_status", "STOP_SERVICIO_FINALIZADO")
                .putString("OWNER", "NO_PROBADO").commit())
            assertTrue(voicePrefs.edit().putInt("voice_gguf_success_count", 3)
                .putLong("voice_gguf_last_latency_ms", 5000L)
                .putInt("barge_in_question_count", 1)
                .putInt("tts_started_count", 2).commit())
            assertTrue(VivoGgufEvidenceResetPlan.invalidate(context))
            for (key in voiceKeys) assertTrue("Stale voice receipt: $key",
                !voicePrefs.contains(key))
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
            val voiceReset = voicePrefs.edit()
            for (key in voiceKeys) {
                when (val previous = beforeVoice[key]) {
                    is String -> voiceReset.putString(key, previous)
                    is Long -> voiceReset.putLong(key, previous)
                    is Int -> voiceReset.putInt(key, previous)
                    is Boolean -> voiceReset.putBoolean(key, previous)
                    else -> voiceReset.remove(key)
                }
            }
            assertTrue(voiceReset.commit())
        }
    }
}
