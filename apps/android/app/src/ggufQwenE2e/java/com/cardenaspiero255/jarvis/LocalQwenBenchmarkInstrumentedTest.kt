package com.cardenaspiero255.jarvis

import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cardenaspiero255.jarvis.core.OfflineVoiceQuestionPolicy
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** CPU Android14 emulator measurements with genuine OFFLINE Qwen Q4 weights.
 * Input is a scripted ASR TRANSCRIPT, not actual microphone evidence;
 * no spoken or biometric correctness is claimed.
 */
@RunWith(AndroidJUnit4::class)
class LocalQwenBenchmarkInstrumentedTest {
    private companion object {
        const val SHA =
            "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
        const val BYTES = 491_400_032L
    }

    @Test(timeout = 840_000L)
    fun officialQwenWeightsAnswerTwoBoundedSpanishTurnsOffline() {
        val instruments = InstrumentationRegistry.getInstrumentation()
        val context = instruments.targetContext
        val store = LocalGgufModelStore(context)
        store.deleteModel()
        val selected = File(context.filesDir, "private_gguf/selected.gguf")
        assertTrue(selected.parentFile!!.mkdirs() || selected.parentFile!!.isDirectory)
        val hash = MessageDigest.getInstance("SHA-256")
        var total = 0L
        instruments.context.assets.open("instruct-qwen.gguf").use { input ->
            selected.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    total += n.toLong()
                    assertTrue("Model too large", total <= BYTES)
                    hash.update(buffer, 0, n)
                    output.write(buffer, 0, n)
                }
            }
        }
        assertEquals(BYTES, total)
        val verified = hash.digest().joinToString("") { "%02x".format(it) }
        assertEquals(SHA, verified)
        assertTrue(context.getSharedPreferences("jarvis_gguf_v1", 0).edit()
            .putString("sha256", verified).putLong("bytes", total).commit())
        assertNotNull(store.verifiedFile())

        // Text simulates Vosk's final transcript, NOT a simulated LLM output.
        val transcripts = listOf(
            "Jarvis cuál es la capital de Chile",
            "Jarvis qué es un procesador"
        )
        val inference = LocalGgufInference(store)
        try {
            transcripts.forEachIndexed { index, utterance ->
                val question = OfflineVoiceQuestionPolicy.extract(utterance, false)
                assertNotNull("Question was not routed", question)
                val done = CountDownLatch(1)
                val answer = AtomicReference<String>()
                val start = SystemClock.elapsedRealtime()
                assertTrue("Decoder refused task", inference.ask(question!!) { result ->
                    answer.set(result)
                    done.countDown()
                })
                assertTrue("Qwen failed to finish in 330 seconds",
                    done.await(330, TimeUnit.SECONDS))
                val duration = SystemClock.elapsedRealtime() - start
                val generated = answer.get().orEmpty().trim()
                assertTrue("Model returned empty answer", generated.isNotEmpty())
                val errors = listOf("No se pudo cargar", "La inferencia local falló",
                    "Motor nativo", "La pregunta supera el contexto",
                    "Memoria insuficiente", "No se generó una respuesta")
                assertFalse("Native model failed", errors.any { generated.contains(it) })
                val pss = Debug.getPss()
                assertTrue(duration > 0)
                assertTrue(pss > 0)
                // Deliberately NEVER print private input/output or raw audio.
                println("QWEN_BENCH_CASE index=$index duration_ms=$duration " +
                    "chars=${generated.length} pss_kib=$pss")
            }
        } finally {
            inference.close()
            store.deleteModel()
        }
    }
}
