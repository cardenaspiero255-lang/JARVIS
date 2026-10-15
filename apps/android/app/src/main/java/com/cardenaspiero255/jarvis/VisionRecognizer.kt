package com.cardenaspiero255.jarvis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.cardenaspiero255.jarvis.core.BoundedImageInputStream
import com.cardenaspiero255.jarvis.core.VisionImagePolicy
import com.cardenaspiero255.jarvis.core.VisionRequestGate
import com.cardenaspiero255.jarvis.core.ButtonHintDetector
import com.cardenaspiero255.jarvis.core.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Strictly user-triggered, on-device Latin OCR for user-selected images.
 *
 * No screen capture, continuous observation, permission elevation, file
 * persistence, network calls or interpretation as executable commands.
 */
class VisionRecognizer(
    context: Context,
    private val showStatus: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val policy = VisionImagePolicy()
    private val buttonDetector = ButtonHintDetector()
    private val resultGate = VisionRequestGate()
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var recognizer: TextRecognizer? = null
    @Volatile private var closed = false

    /** Called only after the user selects one image in Android's picker. */
    fun inspectSelectedImage(uri: Uri) {
        resultGate.cancel()
        if (closed || uri.scheme != "content") {
            display("Imagen no disponible. Debes elegirla con el selector de Android.")
            return
        }
        val requestId = resultGate.begin()
        displayFor(requestId, "Analizando texto localmente en la imagen seleccionada…")
        worker.execute {
            var image: Bitmap? = null
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(BoundedImageInputStream(input), null, bounds)
                } ?: throw IOException("unreadable content URI")
                if (!policy.acceptDimensions(bounds.outWidth, bounds.outHeight)) {
                    displayFor(requestId, "Imagen demasiado grande o inválida para analizar con seguridad.")
                    return@execute
                }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = policy.sampleSize(bounds.outWidth, bounds.outHeight)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                image = appContext.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(BoundedImageInputStream(input), null, options)
                } ?: throw IOException("invalid bitmap")
                val bitmap = image ?: throw IOException("invalid bitmap")
                image = null // Task callback owns the sampled bitmap.
                main.post {
                    if (closed || !resultGate.accepts(requestId)) {
                        bitmap.recycle()
                        return@post
                    }
                    try {
                        val engine = recognizer ?:
                            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also {
                                recognizer = it
                            }
                        engine.process(InputImage.fromBitmap(bitmap, 0))
                            .addOnSuccessListener { result ->
                                val lines = result.textBlocks.flatMap { block -> block.lines }
                                    .mapNotNull { line ->
                                        line.boundingBox?.let { bounds ->
                                            OcrLine(line.text, bounds.left, bounds.top,
                                                bounds.width(), bounds.height())
                                        }
                                    }
                                val hints = buttonDetector.detect(lines, bitmap.width, bitmap.height)
                                val details = buttonDetector.describe(hints)
                                displayFor(requestId,
                                    policy.renderPreview(result.text) + "\n" + details)
                            }
                            .addOnFailureListener {
                                displayFor(requestId, "No se pudo reconocer el texto de esta imagen.")
                            }
                            .addOnCompleteListener { bitmap.recycle() }
                    } catch (_: RuntimeException) {
                        bitmap.recycle()
                        displayFor(requestId, "El motor local de visión no está disponible.")
                    }
                }
            } catch (_: IOException) {
                displayFor(requestId, "No se pudo leer la imagen seleccionada.")
            } catch (_: SecurityException) {
                displayFor(requestId, "Android no concedió acceso a la imagen.")
            } catch (_: RuntimeException) {
                displayFor(requestId, "No se pudo analizar la imagen.")
            } catch (_: OutOfMemoryError) {
                displayFor(requestId, "No hay memoria suficiente para procesar la imagen.")
            } finally {
                image?.recycle()
            }
        }
    }

    fun cancel() {
        resultGate.cancel()
    }

    fun close() {
        if (closed) return
        cancel()
        closed = true
        worker.shutdownNow()
        try { recognizer?.close() } catch (_: RuntimeException) { /* fail closed */ }
        recognizer = null
    }

    private fun display(text: String) {
        main.post { if (!closed) showStatus(text) }
    }

    private fun displayFor(requestId: Long, text: String) {
        main.post {
            if (!closed && resultGate.accepts(requestId)) showStatus(text)
        }
    }
}
