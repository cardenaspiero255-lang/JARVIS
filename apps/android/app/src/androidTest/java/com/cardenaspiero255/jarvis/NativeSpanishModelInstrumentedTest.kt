package com.cardenaspiero255.jarvis

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cardenaspiero255.jarvis.core.NativeSpanishModelSafety
import com.cardenaspiero255.jarvis.core.OfflineCommandGrammarPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/** Real Android 14 emulator smoke for bundled model and x86_64 Vosk runtime.
 *
 * Does not grant RECORD_AUDIO, record sound, use network, or claim this test
 * proves microphone behavior on the Vivo V25 Pro / Funtouch OS.
 */
@RunWith(AndroidJUnit4::class)
class NativeSpanishModelInstrumentedTest {
    @Test fun embeddedSpanishModelUnpacksVerifiesAndInitializesNativeRecognizer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = NativeSpanishModelStore(context).ensureInstalled()
        assertTrue(File(folder, "am/final.mdl").isFile)
        assertTrue(File(folder, "conf/model.conf").isFile)
        val stamp = File(folder.parentFile, ".asset_sha256")
        assertEquals(NativeSpanishModelSafety.SHA256, stamp.readText())
        Model(folder.absolutePath).use { model ->
            Recognizer(model, 16_000.0f).use { recognizer ->
                assertTrue(recognizer.toString().isNotEmpty())
            }
            // Validate the actual verified Spanish model's native grammar path,
            // but only when its vocabulary has every word used by the policy.
            val grammar = OfflineCommandGrammarPolicy().fromVocabulary(
                File(folder, "graph/words.txt"))
            if (grammar != null) {
                Recognizer(model, 16_000.0f, grammar).use { constrained ->
                    assertTrue(constrained.toString().isNotEmpty())
                }
            }
        }
    }
}
