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


    /**
     * Every fixture says the EXACT same CI-only Spanish question. Accent,
     * prosody and speed vary; no ASR hypothesis is rewritten or supplied by
     * the test. Failure to decode the expected phrase across this fixed bank
     * fails the gate, and each hypothesis is preserved as public evidence.
     */
    private fun decodeStrictSpanishQuestion(): String {
        val instruments = InstrumentationRegistry.getInstrumentation()
        val context = instruments.targetContext
        val nativeFolder = NativeSpanishModelStore(context).ensureInstalled()
        val hypotheses = mutableListOf<String>()
        Model(nativeFolder.absolutePath).use { model ->
            for (index in 0 until 12) {
                val fixture = if (index == 0) "test-voice-16k-0.pcm"
                    else "test-voice-16k-$index.pcm"
                val text = StringBuilder()
                Recognizer(model, 16_000.0f).use { recognizer ->
                    instruments.context.assets.open(fixture).use { stream ->
                        val chunk = ByteArray(4096)
                        while (true) {
                            val count = stream.read(chunk)
                            if (count < 0) break
                            if (count == 0) continue
                            if (recognizer.acceptWaveForm(chunk, count)) {
                                val part = JSONObject(recognizer.result).optString("text", "")
                                if (part.isNotBlank()) text.append(part).append(' ')
                            }
                        }
                        text.append(JSONObject(recognizer.finalResult).optString("text", ""))
                    }
                }
                val recognized = text.toString().trim()
                val question = OfflineVoiceQuestionPolicy.extract(recognized, true)
                val normalized = java.text.Normalizer.normalize(
                    question.orEmpty().lowercase(java.util.Locale.ROOT),
                    java.text.Normalizer.Form.NFD
                ).replace(Regex("""\p{M}+"""), "").trim().replace(Regex("""\s+"""), " ")
                println("VOICE_CI_SYNTHETIC_VOSK_HYPOTHESIS index=$index text=${recognized.take(160)}")
                if (normalized == "cual es la capital de chile") {
                    println("VOICE_ASR_FIXTURE_MATCH index=$index real_vosk=true")
                    return requireNotNull(question)
                }
                hypotheses.add("$index:${recognized.take(90)}")
            }
        }
        throw AssertionError(
            "ASR_FIXTURE_MISRECOGNIZED: none of 12 deterministic audio renditions " +
                "decoded exact 'cual es la capital de chile'; " +
                hypotheses.joinToString(separator = " | ").take(1200)
        )
    }

    @Test(timeout = 150_000L)
    fun syntheticSpanishPcmIsAccuratelyRecognizedOffline() {
        val question = decodeStrictSpanishQuestion()
        val normalized = java.text.Normalizer.normalize(
            question.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD
        ).replace(Regex("""\p{M}+"""), "").trim().replace(Regex("""\s+"""), " ")
        assertEquals("cual es la capital de chile", normalized)
    }

    // Android may advertise es-* voices with absent assets. An enumerated
    // "offline" voice is NOT proof that any synthesis can complete offline.
    // Force the verified, CI-installed eSpeak engine, never Google/cloud.
    private fun requireOfflineSpanishSynthesizer(): TextToSpeech {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ready = CountDownLatch(1)
        val status = AtomicInteger(TextToSpeech.ERROR)
        val synth = TextToSpeech(context, { outcome ->
            status.set(outcome)
            ready.countDown()
        }, "com.reecedunn.espeak")
        try {
            assertTrue("Pinned offline engine did not initialize", ready.await(30, TimeUnit.SECONDS))
            assertEquals("Offline engine unavailable", TextToSpeech.SUCCESS, status.get())
            assertEquals("CI TTS default unexpectedly differs", "com.reecedunn.espeak",
                synth.defaultEngine)
            assertTrue("Offline eSpeak TTS engine is not installed",
                synth.engines.any { it.name == "com.reecedunn.espeak" })
            val voice = synth.voices?.firstOrNull {
                it.locale.language == "es" &&
                    !it.isNetworkConnectionRequired &&
                    it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            }
            assertNotNull("Spanish TTS voice assets not installed locally", voice)
            assertEquals("Offline Spanish voice selection failed", TextToSpeech.SUCCESS,
                synth.setVoice(voice!!))
            return synth
        } catch (failure: Throwable) {
            synth.shutdown()
            throw failure
        }
    }

    private fun requireRealOfflineWave(synth: TextToSpeech, phrase: String, name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wav = File(context.cacheDir, name)
        wav.delete()
        val finished = CountDownLatch(1)
        val speechError = AtomicInteger(0)
        val code = AtomicInteger(0)
        synth.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { finished.countDown() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                speechError.incrementAndGet()
                finished.countDown()
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                code.set(errorCode)
                speechError.incrementAndGet()
                finished.countDown()
            }
        })
        try {
            assertEquals("Native synthesizeToFile request was rejected", TextToSpeech.SUCCESS,
                synth.synthesizeToFile(phrase.take(300), Bundle(), wav, name))
            assertTrue("Offline engine never finished WAV synthesis", finished.await(50, TimeUnit.SECONDS))
            assertEquals("Offline engine could not synthesize, code=${code.get()}",
                0, speechError.get())
            assertTrue("Offline WAV has no real audio frames", wav.length() > 512L)
            val header = ByteArray(12)
            wav.inputStream().use {
                assertEquals("Truncated audio header", 12, it.read(header))
            }
            assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(header, 8, 4, Charsets.US_ASCII))
            println("TTS_OFFLINE_PREFLIGHT_PASS engine=com.reecedunn.espeak " +
                "size_bytes=${wav.length()} source=android14 no_network=true")
        } finally {
            wav.delete()
        }
    }

    @Test(timeout = 110_000L)
    fun synthesizesRealOfflineSpanishWavBeforeModelLoad() {
        val synth = requireOfflineSpanishSynthesizer()
        try {
            requireRealOfflineWave(synth, "Esta es una prueba de voz española sin internet.",
                "jarvis-spanish-offline-preflight.wav")
        } finally {
            synth.stop()
            synth.shutdown()
        }
    }

    @Test(timeout = 960_000L)
    fun realPcmVoskQwenAndOfflineTtsSynthesizesWave() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val verifiedQuestion = decodeStrictSpanishQuestion()
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
            assertTrue(inference.ask(verifiedQuestion) {
                result.set(it)
                completed.countDown()
            })
            assertTrue("Local Qwen inference timed out", completed.await(330, TimeUnit.SECONDS))
            val response = result.get().orEmpty().trim()
            assertTrue(response.isNotBlank())
            assertFalse(response.contains("La inferencia local falló"))
            assertFalse(response.contains("No se pudo cargar el modelo"))

            val synth = requireOfflineSpanishSynthesizer()
            try {
                requireRealOfflineWave(synth, response,
                    "jarvis-qwen-real-generated-answer.wav")
                println("VOICE_PCM_VOSK_GGUF_TTS_PASS device=android14 " +
                    "source=synthetic_pcm wake_pre_authorized=simulated " +
                    "physical_mic=false private_audio=false")
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
