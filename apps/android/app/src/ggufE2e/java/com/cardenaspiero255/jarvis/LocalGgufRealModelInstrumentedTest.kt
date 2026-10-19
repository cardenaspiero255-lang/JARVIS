package com.cardenaspiero255.jarvis

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

/**
 * This class is compiled ONLY with -PggufE2e=true and a verified genuine
 * Hugging Face GGUF in androidTest assets. It fails (never skips) if absent.
 *
 * Tests app store -> hash verification -> real llama.cpp JNI decoding ->
 * background callback, not just a missing-file smoke test.
 */
@RunWith(AndroidJUnit4::class)
class LocalGgufRealModelInstrumentedTest {
    private companion object {
        const val SHA256 =
            "e3131339bf4e8065265593d4fd8f7bb7ff2d3abff1edb5618aa1197b89cad9f5"
    }

    @Test(timeout = 240_000L)
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
        try {
            // Asset exists ONLY in the test APK of the dedicated workflow.
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
            assertTrue(copied > 40_000_000L)
            assertTrue(context.getSharedPreferences("jarvis_gguf_v1", 0).edit()
                .putString("sha256", actual).putLong("bytes", copied).commit())
            assertNotNull("App-private model hash validation", store.verifiedFile())

            val answer = AtomicReference<String>()
            val completed = CountDownLatch(1)
            val start = android.os.SystemClock.elapsedRealtime()
            assertTrue("Model question must enter worker",
                inference.ask("Escribe una frase corta sobre una estrella.") { text ->
                    answer.set(text)
                    completed.countDown()
                })
            assertTrue("Genuine inference did not finish within 210s",
                completed.await(210L, TimeUnit.SECONDS))
            val result = answer.get().orEmpty().trim()
            assertTrue("Generated output must contain real decoded tokens: $result",
                result.isNotBlank())
            for (failure in listOf(
                "No se pudo cargar el modelo",
                "La inferencia local falló",
                "La generación local se interrumpió",
                "No se generó una respuesta",
                "Error de tokenización",
                "Memoria insuficiente",
                "Motor nativo")) {
                assertFalse("Model generated a failure instead of tokens: $result",
                    result.contains(failure))
            }
            println(
                "GGUF_E2E_REAL_GENERATION_OK sha256=$actual bytes=$copied " +
                "generated_chars=${result.length} elapsed_ms=" +
                (android.os.SystemClock.elapsedRealtime() - start) +
                " offline=true"
            )
        } finally {
            inference.close()
            store.deleteModel()
        }
    }
}
