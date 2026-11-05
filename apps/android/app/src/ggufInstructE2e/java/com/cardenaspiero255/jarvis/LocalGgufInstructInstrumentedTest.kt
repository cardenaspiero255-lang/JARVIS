package com.cardenaspiero255.jarvis

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Experimental quality PROXY on actual Instruct weights, not a certificate. */
@RunWith(AndroidJUnit4::class)
class LocalGgufInstructInstrumentedTest {
    private companion object {
        const val SHA =
            "ed5fa30c487b282ec156c29062f1222e5c20875a944ac98289dbd242e947f747"
    }

    @Test(timeout = 600_000L)
    fun pinnedInstructionWeightsAnswerFourSpanishQuestionsOffline() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = LocalGgufModelStore(context)
        store.deleteModel()
        val selected = File(context.filesDir, "private_gguf/selected.gguf")
        assertTrue(selected.parentFile!!.mkdirs() || selected.parentFile!!.isDirectory)
        val sha = MessageDigest.getInstance("SHA-256")
        var total = 0L
        instrumentation.context.assets.open("instruct-smollm.gguf").use { input ->
            selected.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    total += n
                    assertTrue(total <= 125_000_000L)
                    sha.update(buffer, 0, n)
                    output.write(buffer, 0, n)
                }
            }
        }
        assertTrue(total >= 90_000_000L)
        val verifiedSha = sha.digest().joinToString("") { "%02x".format(it) }
        assertEquals("Exact instruction weights required", SHA, verifiedSha)
        assertTrue(context.getSharedPreferences("jarvis_gguf_v1", 0).edit()
            .putString("sha256", verifiedSha).putLong("bytes", total).commit())
        assertNotNull(store.verifiedFile())

        val testCases = listOf(
            Triple("chile_capital", "¿Cuál es la capital de Chile?",
                Regex("santiago", RegexOption.IGNORE_CASE)),
            Triple("arithmetic", "¿Cuánto es dos más dos?",
                Regex("cuatro|\\b4\\b", RegexOption.IGNORE_CASE)),
            Triple("red_planet", "¿Qué planeta es el planeta rojo?",
                Regex("marte", RegexOption.IGNORE_CASE)),
            Triple("computer_processor", "¿Qué es un procesador?",
                Regex("procesador|cpu|chip|instrucciones", RegexOption.IGNORE_CASE))
        )
        val inference = LocalGgufInference(store)
        System.loadLibrary("jarvis_llm")
        val before = inference.nativeCacheCounters()
        var matches = 0
        try {
            for ((i, case) in testCases.withIndex()) {
                val answer = AtomicReference<String>()
                val completed = CountDownLatch(1)
                val started = SystemClock.elapsedRealtime()
                assertTrue(inference.ask(case.second) {
                    answer.set(it)
                    completed.countDown()
                })
                assertTrue("Real GGUF inference timed out for ${case.first}",
                    completed.await(110L, TimeUnit.SECONDS))
                val response = answer.get().orEmpty().trim()
                assertTrue("Real inference returned no text", response.isNotBlank())
                val errors = listOf(
                    "No se pudo cargar el modelo", "La inferencia local falló",
                    "No se generó una respuesta", "Memoria insuficiente",
                    "Motor nativo", "Error de tokenización")
                assertFalse("Inference failed", errors.any { response.contains(it) })
                val matched = case.third.containsMatchIn(response)
                if (matched) matches++
                println("INSTRUCT_QUALITY_CASE index=$i id=${case.first} " +
                    "matched=$matched duration_ms=" +
                    (SystemClock.elapsedRealtime() - started) +
                    " chars=${response.length}")
            }
            val after = inference.nativeCacheCounters()
            assertEquals("Instruct GGUF should load one native weight set", 1L,
                after[0] - before[0])
            assertTrue("Instruct GGUF should reuse verified weights", after[1] - before[1] >= 3L)
            println("INSTRUCT_QUALITY_SUMMARY matched=$matches total=4 " +
                "semantic_certified=false device=emulator")
        } finally {
            inference.close()
            store.deleteModel()
        }
    }
}
