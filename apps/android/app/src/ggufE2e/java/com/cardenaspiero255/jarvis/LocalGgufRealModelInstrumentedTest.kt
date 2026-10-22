package com.cardenaspiero255.jarvis

import android.os.Build
import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CI-only authentic GGUF generation + sample-based QA (not certification).
 * No weights, raw prompts, generated replies, audio or owner identifiers
 * are uploaded. Report contains only synthetic case IDs and measurements.
 */
@RunWith(AndroidJUnit4::class)
class LocalGgufRealModelInstrumentedTest {
    private companion object {
        const val SHA256 =
            "e3131339bf4e8065265593d4fd8f7bb7ff2d3abff1edb5618aa1197b89cad9f5"
    }

    private data class SpanishCase(
        val id: String, val question: String, val lexicalAnswer: Regex
    )

    @Test(timeout = 900_000L)
    fun genuinePinnedGgufProducesTextOffline() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = LocalGgufModelStore(context)
        store.deleteModel()
        val privateFile = File(context.filesDir, "private_gguf/selected.gguf")
        assertTrue(privateFile.parentFile!!.mkdirs() || privateFile.parentFile!!.isDirectory)
        val checksum = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        val inference = LocalGgufInference(store)
        // Process PSS includes native mmap allocations, unlike JVM heap alone.
        val basePss = maxOf(1L, Debug.getPss())
        val peakPss = AtomicLong(basePss)
        val peakJava = AtomicLong(1L)
        val sampling = AtomicBoolean(false)
        val sampler = Thread {
            while (sampling.get()) {
                peakPss.updateAndGet { old -> maxOf(old, Debug.getPss()) }
                val heap = (Runtime.getRuntime().totalMemory() -
                    Runtime.getRuntime().freeMemory()) / 1024L
                peakJava.updateAndGet { old -> maxOf(old, heap) }
                try { Thread.sleep(125L) }
                catch (_: InterruptedException) { break }
            }
        }.apply { isDaemon = true; name = "gguf-qa-pss-sampler" }

        try {
            // Only the separate, licensed test APK contains these weights.
            instrumentation.context.assets.open("e2e-smollm.gguf").use { input ->
                privateFile.outputStream().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (n == 0) continue
                        copied += n
                        assertTrue("Unexpected oversized GGUF", copied <= 120_000_000L)
                        checksum.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                }
            }
            val actual = checksum.digest().joinToString("") { "%02x".format(it) }
            assertEquals("Byte-for-byte authentic GGUF", SHA256, actual)
            assertEquals(101_016_128L, copied)
            assertTrue(context.getSharedPreferences("jarvis_gguf_v1", 0).edit()
                .putString("sha256", actual).putLong("bytes", copied).commit())
            assertNotNull("App-private model hash validation", store.verifiedFile())

            val answers = mutableListOf<String>()
            val durations = mutableListOf<Long>()
            // These are real JNI counters, not timing-based guesses.
            // System.loadLibrary is explicit here because this is a native E2E.
            System.loadLibrary("jarvis_llm")
            val nativeBefore = inference.nativeCacheCounters()
            assertEquals(3, nativeBefore.size)
            sampling.set(true)
            sampler.start()

            val answer = AtomicReference<String>()
            val completed = CountDownLatch(1)
            val start = SystemClock.elapsedRealtime()
            assertTrue("Model question must enter worker",
                inference.ask("Escribe una frase corta sobre una estrella.") { text ->
                    answer.set(text)
                    completed.countDown()
                })
            assertTrue("Genuine inference did not finish within 210s",
                completed.await(210L, TimeUnit.SECONDS))
            val result = checkedText(answer.get())
            answers.add(result)
            durations.add(maxOf(1L, SystemClock.elapsedRealtime() - start))
            val initialNativePerf = inference.nativeLastPerformance()
            assertEquals("JNI native timing format", 8, initialNativePerf.size)
            assertTrue("Native total time measured", initialNativePerf[0] > 0L)
            assertTrue("At least one actual decoded token", initialNativePerf[5] > 0L)
            assertTrue("Bounded CPU thread count", initialNativePerf[6] in 1L..4L)
            println(
                "GGUF_E2E_REAL_GENERATION_OK sha256=$actual bytes=$copied " +
                "generated_chars=${result.length} elapsed_ms=${durations[0]} offline=true"
            )

            // Tiny lexical rubric, not semantic/hallucination certification.
            // The prompt set and matching criteria are frozen in this test.
            val questions = listOf(
                SpanishCase("chile_capital", "¿Cuál es la capital de Chile?",
                    Regex("\\bsantiago\\b", RegexOption.IGNORE_CASE)),
                SpanishCase("arithmetic", "¿Cuánto es dos más dos?",
                    Regex("\\b(4|cuatro)\\b", RegexOption.IGNORE_CASE)),
                SpanishCase("red_planet", "¿Qué planeta se conoce como el planeta rojo?",
                    Regex("\\bmarte\\b", RegexOption.IGNORE_CASE)),
                SpanishCase("computer_processor", "¿Qué es un procesador?",
                    Regex("\\b(cpu|procesador|chip|instrucciones)\\b",
                        RegexOption.IGNORE_CASE))
            )
            val quality = JSONArray()
            for (item in questions) {
                val response = AtomicReference<String>()
                val done = CountDownLatch(1)
                val tick = SystemClock.elapsedRealtime()
                assertTrue("Prior inference must have released the JNI slot",
                    inference.ask(item.question) { text ->
                        response.set(text)
                        done.countDown()
                    })
                assertTrue("Model question timed out: ${item.id}",
                    done.await(210L, TimeUnit.SECONDS))
                val output = checkedText(response.get())
                answers.add(output)
                durations.add(maxOf(1L, SystemClock.elapsedRealtime() - tick))
                quality.put(JSONObject()
                    .put("id", item.id)
                    .put("keyword_matched", item.lexicalAnswer.containsMatchIn(output)))
            }

            sampling.set(false)
            sampler.join(2_000L)
            val nativeAfter = inference.nativeCacheCounters()
            val newLoads = nativeAfter[0] - nativeBefore[0]
            val warmHits = nativeAfter[1] - nativeBefore[1]
            assertEquals("One verified native load", 1L, newLoads)
            assertTrue("Repeated authentic queries hit the warm model", warmHits >= 4L)
            println("GGUF_WARM_MODEL_VERIFIED loads=$newLoads hits=$warmHits")
            val pssPeak = maxOf(basePss, peakPss.get(), Debug.getPss())
            val javaPeak = maxOf(1L, peakJava.get())
            val isEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
                Build.HARDWARE.contains("goldfish", ignoreCase = true)
            val device = JSONObject()
                .put("source", if (isEmulator) "emulator" else "physical_reported")
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("sdk_int", Build.VERSION.SDK_INT)
            val raw = JSONObject()
                .put("schema_version", 1)
                .put("device", device)
                .put("model", JSONObject().put("sha256", actual).put("bytes", copied))
                .put("inference", JSONObject()
                    .put("native_first_load_ms", initialNativePerf[1])
                    .put("native_first_prefill_ms", initialNativePerf[3])
                    .put("native_first_decode_ms", initialNativePerf[4])
                    .put("native_first_decoded_tokens", initialNativePerf[5])
                    .put("native_first_cpu_threads", initialNativePerf[6])
                    .put("direct_output_token_budget",
                        com.cardenaspiero255.jarvis.core.FastVoiceGgufPolicy.DIRECT_MAX_TOKENS)
                    .put("voice_output_token_budget",
                        com.cardenaspiero255.jarvis.core.FastVoiceGgufPolicy.VOICE_MAX_TOKENS)
                    .put("cold_request_ms", durations.first())
                    .put("subsequent_request_ms",
                        JSONArray(durations.drop(1).map { it.toInt() }))
                    .put("generated_characters",
                        JSONArray(answers.map { it.length }))
                    .put("baseline_pss_kib", basePss)
                    .put("peak_pss_kib", pssPeak)
                    .put("peak_java_heap_kib", javaPeak)
                    .put("warm_cache", JSONObject()
                        .put("native_model_loads", newLoads)
                        .put("native_cache_hits", warmHits)
                        .put("native_cache_evictions",
                            nativeAfter[2] - nativeBefore[2])
                        .put("verified_sha_revision", true)))
                .put("quality", JSONObject()
                    .put("method", "keyword_proxy_v1")
                    .put("cases", quality))
                .put("voice", JSONObject()
                    .put("gguf_voice_pipeline", "NOT_IMPLEMENTED")
                    .put("asr_audio_validated", false)
                    .put("tts_audio_validated", false)
                    .put("llm_tool_authority", false))
            val reportFile = File(context.filesDir, "gguf-qa-raw.json")
            reportFile.writeText(raw.toString(), Charsets.UTF_8)
            assertTrue("Metrics report must be persisted for CI evidence",
                reportFile.isFile && reportFile.length() > 200)
            // Android Gradle Test Runner can remove the app after success.
            // Its case-specific logcat preserves this compact, data-only JSON.
            // No raw user prompts, responses, audio or device serial are emitted.
            println("GGUF_QA_JSON_V1=" + raw.toString())
            println(
                "GGUF_QA_RAW_MEASUREMENTS_COLLECTED requests=${durations.size} " +
                "first_ms=${durations.first()} " +
                "peak_pss_kib=$pssPeak report=gguf-qa-raw.json"
            )
            // User-selected deletion is ordered AFTER native inference and
            // MUST release retained weights before removing the GGUF bytes.
            val deletionFinished = CountDownLatch(1)
            val deletedResult = AtomicReference<Boolean?>()
            assertTrue("Delete must be scheduled on the JNI executor",
                inference.deleteModel { deleted ->
                    deletedResult.set(deleted)
                    deletionFinished.countDown()
                })
            assertTrue("Native eviction/deletion timed out",
                deletionFinished.await(35L, TimeUnit.SECONDS))
            assertEquals("Deletion must report actual result", true, deletedResult.get())
            assertFalse("Deleted GGUF must not remain in private storage", privateFile.exists())
            val postDeleteCache = inference.nativeCacheCounters()
            assertTrue("Deleting model must unload native cached weights",
                postDeleteCache[2] > nativeAfter[2])
            println("GGUF_CACHE_EVICTION_VERIFIED deleted=true " +
                "evictions=${postDeleteCache[2] - nativeAfter[2]}")

        } finally {
            sampling.set(false)
            if (sampler.isAlive) sampler.join(2_000L)
            inference.close()
            store.deleteModel()
        }
    }

    private fun checkedText(value: String?): String {
        val text = value.orEmpty().trim()
        assertTrue("Real GGUF must return nonempty decoded text", text.isNotBlank())
        for (failure in listOf(
            "No se pudo cargar el modelo",
            "La inferencia local falló",
            "La generación local se interrumpió",
            "No se generó una respuesta",
            "Error de tokenización",
            "Memoria insuficiente",
            "Motor nativo",
            "La pregunta supera el contexto local máximo",
            "Inferencia local cancelada o fallida"
        )) {
            assertFalse("Native failure is not generated text: $failure",
                text.contains(failure))
        }
        return text
    }
}
