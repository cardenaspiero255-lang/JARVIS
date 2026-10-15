package com.cardenaspiero255.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.cardenaspiero255.jarvis.core.ButtonHintDetector
import com.cardenaspiero255.jarvis.core.OcrLine
import com.cardenaspiero255.jarvis.core.ScreenObservationPolicy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground-only Android MediaProjection worker.
 *
 * The owner explicitly authorizes EACH session through the operating system.
 * OCR is local, sampled at most once per three seconds for ten minutes;
 * only a count of possible buttons is kept in RAM. There are no screenshots,
 * transcription logs, files, network calls or automated taps.
 */
class ScreenObservationService : Service() {
    private val policy = ScreenObservationPolicy()
    private val shuttingDown = AtomicBoolean(true)
    private val processing = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var reader: ImageReader? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recognizer: TextRecognizer? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            terminate(stopProjection = false)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            terminate(stopProjection = true)
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || !shuttingDown.get()) {
            return START_NOT_STICKY
        }
        val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        }
        if (code != android.app.Activity.RESULT_OK || permission == null ||
            !policy.start(true, SystemClock.elapsedRealtime())) {
            stopSelf()
            return START_NOT_STICKY
        }

        shuttingDown.set(false)
        live = this
        liveHintCount = 0
        try {
            createNotificationChannel()
            val notification = activeNotification()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            // Even if no frames arrive, the session expires after ten minutes.
            mainHandler.postDelayed({
                if (!shuttingDown.get()) terminate(stopProjection = true)
            }, ScreenObservationPolicy.MAX_SESSION_MILLIS)
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val activeProjection = manager.getMediaProjection(code, permission)
                ?: throw IllegalStateException("projection permission unavailable")
            projection = activeProjection
            activeProjection.registerCallback(projectionCallback, mainHandler)
            val metrics = resources.displayMetrics
            val w = metrics.widthPixels
            val h = metrics.heightPixels
            if (w <= 0 || h <= 0 || w > 3200 || h > 3200 ||
                w.toLong() * h.toLong() > 6_000_000L) {
                throw IllegalStateException("unsupported display dimensions")
            }
            thread = HandlerThread("jarvis-omega-screen-observer").also { it.start() }
            val frameHandler = Handler(thread!!.looper)
            val imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            reader = imageReader
            imageReader.setOnImageAvailableListener({ source ->
                val image = try { source.acquireLatestImage() } catch (_: RuntimeException) { null }
                if (image != null) {
                    try {
                        val now = SystemClock.elapsedRealtime()
                        if (!policy.isActive(now)) {
                            mainHandler.post { terminate(stopProjection = true) }
                        } else if (policy.sample(now) && processing.compareAndSet(false, true)) {
                            readFrameForLocalOcr(image)
                        }
                    } finally {
                        image.close()
                    }
                }
            }, frameHandler)
            virtualDisplay = activeProjection.createVirtualDisplay(
                "JARVIS OMEGA user-approved screen observation",
                w, h, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface, null, frameHandler
            ) ?: throw IllegalStateException("virtual display unavailable")
        } catch (_: Exception) {
            terminate(stopProjection = true)
        } catch (_: OutOfMemoryError) {
            terminate(stopProjection = true)
        }
        return START_NOT_STICKY
    }

    private fun readFrameForLocalOcr(image: android.media.Image) {
        var owned: Bitmap? = null
        var submitted = false
        try {
            val plane = image.planes.firstOrNull() ?: return
            if (plane.pixelStride != 4 || image.width <= 0 || image.height <= 0) return
            val paddedWidth = plane.rowStride / plane.pixelStride
            if (paddedWidth < image.width || paddedWidth > 3276) return
            val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
            try {
                padded.copyPixelsFromBuffer(plane.buffer)
                val clipped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                val maxSide = maxOf(image.width, image.height)
                owned = if (maxSide > 1024) {
                    val ratio = 1024.0 / maxSide
                    val small = Bitmap.createScaledBitmap(clipped,
                        maxOf(1, (clipped.width * ratio).toInt()),
                        maxOf(1, (clipped.height * ratio).toInt()), true)
                    clipped.recycle()
                    small
                } else clipped
            } finally {
                padded.recycle()
            }
            val bitmap = owned ?: return
            owned = null  // ML Kit owns this bitmap until the completion callback.
            val engine = recognizer ?: TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
            ).also { recognizer = it }
            engine.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    if (!shuttingDown.get()) {
                        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                            line.boundingBox?.let { bounds ->
                                OcrLine(line.text, bounds.left, bounds.top,
                                    bounds.width(), bounds.height())
                            }
                        }
                        liveHintCount = ButtonHintDetector().detect(
                            lines, bitmap.width, bitmap.height
                        ).size
                    }
                }
                .addOnCompleteListener {
                    bitmap.recycle()
                    processing.set(false)
                }
            submitted = true
        } catch (_: Exception) {
            // Untrusted screen content must not kill the rest of JARVIS.
        } catch (_: OutOfMemoryError) {
        } finally {
            owned?.recycle()
            if (!submitted) processing.set(false)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "JARVIS: observación de pantalla", NotificationManager.IMPORTANCE_LOW
        ))
    }

    private fun activeNotification(): Notification {
        val stopIntent = Intent(this, ScreenObservationService::class.java).apply {
            action = ACTION_STOP
        }
        val stop = PendingIntent.getService(this, 40, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("JARVIS observa tu pantalla")
            .setContentText("OCR local, máximo 10 minutos. Pulsa DETENER.")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_media_pause, "DETENER OBSERVACIÓN", stop
            ).build())
            .build()
    }

    private fun terminate(stopProjection: Boolean) {
        if (!shuttingDown.compareAndSet(false, true)) return
        policy.stop()
        if (live === this) live = null
        liveHintCount = 0
        try { reader?.setOnImageAvailableListener(null, null) } catch (_: Exception) {}
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { reader?.close() } catch (_: Exception) {}
        reader = null
        try { recognizer?.close() } catch (_: Exception) {}
        recognizer = null
        thread?.quitSafely()
        thread = null
        val current = projection
        projection = null
        try { current?.unregisterCallback(projectionCallback) } catch (_: Exception) {}
        if (stopProjection) try { current?.stop() } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        terminate(stopProjection = true)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.cardenaspiero255.jarvis.SCREEN_START"
        const val ACTION_STOP = "com.cardenaspiero255.jarvis.SCREEN_STOP"
        const val EXTRA_RESULT_CODE = "screen_result_code"
        const val EXTRA_RESULT_DATA = "screen_result_data"
        private const val CHANNEL_ID = "jarvis_omega_screen_capture"
        private const val NOTIFICATION_ID = 1241

        @Volatile private var live: ScreenObservationService? = null
        @Volatile private var liveHintCount: Int = 0

        fun status(): String =
            if (live == null) "Observación de pantalla detenida."
            else "Observación activa: " + liveHintCount +
                " posibles botones observados recientemente. Sin clics."
    }
}
