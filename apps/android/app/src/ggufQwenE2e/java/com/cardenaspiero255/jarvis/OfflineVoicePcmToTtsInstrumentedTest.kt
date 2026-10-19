package com.cardenaspiero255.jarvis

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cardenaspiero255.jarvis.core.OfflineVoiceQuestionPolicy
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Component E2E with real generated Spanish PCM, bundled Vosk, authentic
 * Qwen Android JNI, and real local TextToSpeech.synthesizeToFile. NO physical
 * microphone, owner Voice Match, playback speaker, or Funtouch claim.
 * A missing offline Spanish TTS voice is a FAILURE, never a skipped test.
 */
@RunWith(AndroidJUnit4::class)
class OfflineVoicePcmToTtsInstrumentedTest {
    private val sha = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"

    @Test(timeout = 960_000L)
    fun realPcmVoskQwenAndOfflineTtsSynthesizesWave() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val nativeFolder = NativeSpanishModelStore(context).ensureInstalled()
        val transcript = StringBuilder()
        Model(nativeFolder.absolutePath).use { model ->
            Recognizer(model, 16_000.0f).use { recognizer ->
                inst.context.assets.open("test-voice-16k.pcm").use { stream ->
                    val chunk = ByteArray(4096)
                    while (true) {
                        val count = stream.read(chunk)
                        if (count < 0) break
                        if (recognizer.acceptWaveForm(chunk, count)) {
                            val part = JSONObject(recognizer.result).optString("text", "")
                            if (part.isNotBlank()) transcript.append(part).append(' ')
                        }
                    }
                    transcript.append(JSONObject(recognizer.finalResult).optString("text", ""))
                }
            }
        }
        val question = OfflineVoiceQuestionPolicy.extract(transcript.toString(), false)
        assertNotNull("Vosk did not recognize the scripted Spanish wake+question", question)
        // Copy only test-verified weights into app-private storage for JNI.
        val store = LocalGgufModelStore(context)
        store.deleteModel()
        val file = File(context.filesDir, "private_gguf/selected.gguf")
        assertTrue(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        inst.context.assets.open("instruct-qwen.gguf").use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(65536)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    bytes += n
                    assertTrue(bytes <= 491_400_032L)
                    digest.update(buffer, 0, n)
                    output.write(buffer, 0, n)
                }
            }
        }
        assertEquals(491_400_032L, bytes)
        assertEquals(sha, digest.digest().joinToString("") { "%02x".format(it) })
        assertTrue(context.getSharedPreferences("jarvis_gguf_v1", 0).edit()
            .putString("sha256", sha).putLong("bytes", bytes).commit())

        val inference = LocalGgufInference(store)
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        try {
            assertTrue(inference.ask(question!!) {
                result.set(it)
                completed.countDown()
            })
            assertTrue("Local Qwen inference timed out", completed.await(330, TimeUnit.SECONDS))
            val response = result.get().orEmpty().trim()
            assertTrue(response.isNotBlank())
            assertFalse(response.contains("La inferencia local falló"))
            assertFalse(response.contains("No se pudo cargar el modelo"))

            val initialized = CountDownLatch(1)
            val initStatus = AtomicInteger(TextToSpeech.ERROR)
            val synth = TextToSpeech(context) { status ->
                initStatus.set(status)
                initialized.countDown()
            }
            try {
                assertTrue("Local Android TTS did not initialize",
                    initialized.await(25, TimeUnit.SECONDS))
                assertEquals("Android TTS initialization failed",
                    TextToSpeech.SUCCESS, initStatus.get())
                val offlineSpanish = synth.voices?.firstOrNull {
                    it.locale.language == "es" && !it.isNetworkConnectionRequired
                }
                assertNotNull("Offline Spanish voice missing on Android14 emulator",
                    offlineSpanish)
                assertEquals(TextToSpeech.SUCCESS, synth.setVoice(offlineSpanish!!))
                val wav = File(context.cacheDir, "jarvis-qwen-tts-ci.wav")
                wav.delete()
                val spoken = CountDownLatch(1)
                val spokenErrors = AtomicInteger(0)
                synth.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { spoken.countDown() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        spokenErrors.incrementAndGet(); spoken.countDown()
                    }
                })
                assertEquals(TextToSpeech.SUCCESS,
                    synth.synthesizeToFile(response.take(300), Bundle(), wav, "jarvis-qwen-e2e"))
                assertTrue("Offline TTS did not finish", spoken.await(55, TimeUnit.SECONDS))
                assertEquals("TTS did not synthesize a wave", 0, spokenErrors.get())
                assertTrue("TTS audio file empty", wav.length() > 44L)
                println("VOICE_PCM_VOSK_GGUF_TTS_PASS device=android14 " +
                    "source=synthetic_pcm physical_mic=false private_audio=false")
                wav.delete()
            } finally {
                synth.stop()
                synth.shutdown()
            }
        } finally {
            inference.close()
            store.deleteModel()
        }
    }
}
