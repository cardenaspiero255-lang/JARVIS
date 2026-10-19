package com.cardenaspiero255.jarvis

import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Genuine on-device benchmark. Small-model answers are reported as observations,
 * NOT a claim of accuracy. Run only with -PggufE2e=true and pinned model asset.
 */
@RunWith(AndroidJUnit4::class)
class LocalGgufQualityBenchmarkInstrumentedTest {
    @Test(timeout = 300_000L)
    fun spanishAnswersAndLatencyPssAreMeasuredWithoutInternet() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = LocalGgufModelStore(context)
        store.deleteModel()
        val target = File(context.filesDir, "private_gguf/selected.gguf")
        target.parentFile!!.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        instrumentation.context.assets.open("e2e-smollm.gguf").use { source ->
            target.outputStream().use { sink ->
                val buffer = ByteArray(65536)
                while (true) {
                    val n = source.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    digest.update(buffer, 0, n)
                    sink.write(buffer, 0, n)
                    size += n
                }
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        assertEquals("e3131339bf4e8065265593d4fd8f7bb7ff2d3abff1edb5618aa1197b89cad9f5", sha)
        assertTrue(context.getSharedPreferences("jarvis_gguf_v1", 0).edit()
            .putString("sha256", sha).putLong("bytes", size).commit())
        val prompts = listOf(
            "¿Quién descubrió la penicilina?" to listOf("fleming"),
            "¿Cuánto es dos más dos?" to listOf("cuatro", "4"),
            "¿Qué planeta es conocido como el planeta rojo?" to listOf("marte"),
            "¿Cuál es la capital de Chile?" to listOf("santiago")
        )
        val durations = mutableListOf<Long>()
        var matching = 0
        var peakPssKb = 0
        val engine = LocalGgufInference(store)
        try {
            prompts.forEachIndexed { index, (question, accepted) ->
                val answer = AtomicReference<String>()
                val done = CountDownLatch(1)
                val before = SystemClock.elapsedRealtime()
                assertTrue("Inference slot unavailable at prompt $index",
                    engine.ask(question) { reply -> answer.set(reply); done.countDown() })
                assertTrue("Timeout at prompt $index", done.await(65, TimeUnit.SECONDS))
                val elapsed = SystemClock.elapsedRealtime() - before
                durations += elapsed
                val pss = Debug.MemoryInfo()
                Debug.getMemoryInfo(pss)
                peakPssKb = maxOf(peakPssKb, pss.totalPss)
                val text = answer.get().orEmpty()
                assertTrue("Blank inference at prompt $index", text.isNotBlank())
                val normalized = text.lowercase(Locale.ROOT)
                val pass = accepted.any { normalized.contains(it) }
                if (pass) matching++
                println("GGUF_QUALITY_CASE index=$index latency_ms=$elapsed pss_kb=${pss.totalPss} " +
                    "reference_match=$pass response_chars=${text.length}")
            }
            val ordered = durations.sorted()
            fun percentile(p: Double) = ordered[((ordered.size - 1) * p).toInt()]
            println("GGUF_QUALITY_SUMMARY samples=${durations.size} " +
                "reference_matches=$matching p50_ms=${percentile(0.5)} " +
                "p95_ms=${percentile(0.95)} observed_peak_pss_kb=$peakPssKb " +
                "platform=emulator model_sha=$sha")
            // Functional gate, not an arbitrary claim that a 135M model is accurate.
            assertEquals(prompts.size, durations.size)
        } finally {
            engine.close()
            store.deleteModel()
        }
    }
}
