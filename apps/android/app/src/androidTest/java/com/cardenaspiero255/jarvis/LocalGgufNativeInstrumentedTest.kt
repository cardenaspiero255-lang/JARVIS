package com.cardenaspiero255.jarvis

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real native JNI bridge smoke with a nonexistent model path, not fake tokens.
 * A separate properly-licensed GGUF fixture and physical performance run are
 * needed before claiming actual text generation end-to-end.
 */
@RunWith(AndroidJUnit4::class)
class LocalGgufNativeInstrumentedTest {
    @Test fun nativeLlamaLibraryLoadsAndRejectsAbsentModel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("jarvis_llm")
        val runtime = LocalGgufInference(LocalGgufModelStore(context))
        try {
            val method = LocalGgufInference::class.java.getDeclaredMethod(
                "generate", String::class.java, String::class.java,
                Int::class.javaPrimitiveType, AtomicBoolean::class.java
            )
            method.isAccessible = true
            val missing = java.io.File(context.filesDir, "absent.gguf")
            missing.delete()
            val result = method.invoke(runtime, missing.absolutePath,
                "Pregunta: hola\nRespuesta:", 3, AtomicBoolean(false)) as String
            assertTrue("Native model failure should be visible, not invented success",
                result.contains("No se pudo cargar el modelo GGUF"))
        } finally {
            runtime.close()
        }
    }

    @Test fun nativeLlamaCancellationBeforeModelLoadAvoidsExecution() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = LocalGgufInference(LocalGgufModelStore(context))
        try {
            val method = LocalGgufInference::class.java.getDeclaredMethod(
                "generate", String::class.java, String::class.java,
                Int::class.javaPrimitiveType, AtomicBoolean::class.java
            )
            method.isAccessible = true
            val stop = AtomicBoolean(true)
            val output = method.invoke(runtime,
                java.io.File(context.filesDir, "absent.gguf").absolutePath,
                "Pregunta: hola\nRespuesta:", 3, stop) as String
            assertEquals("", output)
        } finally {
            runtime.close()
        }
    }
}
