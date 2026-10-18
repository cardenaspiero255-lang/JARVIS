package com.cardenaspiero255.jarvis

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import com.cardenaspiero255.jarvis.core.OwnerVoiceCapturePolicy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional foreground-only enrollment. Three explicit taps capture 2.5s of
 * live PCM each; raw audio is kept in RAM until enrollment ends or cancels.
 * No auto-start, cloud, saved recordings or privilege unlock.
 */
class OwnerVoiceEnrollmentActivity : Activity() {
    private val rule = OwnerVoiceCapturePolicy()
    private val main = Handler(Looper.getMainLooper())
    private val recordings = ArrayList<LocalVoiceEnrollmentRecording>(3)
    private val running = AtomicBoolean(false)
    private var visible = false
    private var stopped = false
    @Volatile private var currentRecorder: AudioRecord? = null
    @Volatile private var worker: Thread? = null
    private lateinit var consent: CheckBox
    private lateinit var status: TextView
    private lateinit var record: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 48)
            setBackgroundColor(Color.rgb(10, 12, 20))
        }
        fun label(text: String, size: Float): TextView = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(0, 12, 0, 12)
        }
        root.addView(label("JARVIS · Registrar mi voz", 22f))
        root.addView(label(
            "Tres grabaciones distintas de 2,5 segundos, solo mientras esta pantalla esté visible. " +
                "Di «Jarvis, soy yo» con voz normal. Las muestras se eliminan después de la " +
                "inscripción o al cancelar. Se guarda únicamente una plantilla cifrada. " +
                "Este perfil NO autentica todavía ni impide ataques por grabación.",
            15f
        ))
        consent = CheckBox(this).apply {
            text = "Autorizo tres capturas locales para crear mi perfil de voz"
            setTextColor(Color.WHITE)
            isChecked = false
            setOnCheckedChangeListener { _, checked ->
                if (!checked) cancelCapture("Consentimiento retirado; no se conserva audio.")
                updateState()
            }
        }
        root.addView(consent)
        status = label("Muestras: 0/3. Consentimiento opcional.", 16f)
        root.addView(status)
        record = Button(this).apply {
            text = "GRABAR MUESTRA 1/3"
            setOnClickListener { startNextCapture() }
        }
        root.addView(record)
        root.addView(Button(this).apply {
            text = "BORRAR PERFIL Y REVOCAR"
            setOnClickListener {
                cancelCapture("Capturas canceladas.")
                Thread {
                    val ok = try {
                        SherpaSpeakerEnrollmentEngine(applicationContext).revoke()
                        true
                    } catch (_: Exception) { false }
                    main.post {
                        status.text = if (ok)
                            "Perfil cifrado revocado y borrado."
                        else "No se pudo acreditar la revocación del perfil."
                    }
                }.start()
            }
        })
        root.addView(Button(this).apply {
            text = "CANCELAR Y SALIR"
            setOnClickListener { cancelCapture("Sin audio retenido."); finish() }
        })
        setContentView(root)
        updateState()
    }

    override fun onStart() {
        super.onStart()
        stopped = false
        visible = true
        updateState()
    }

    override fun onStop() {
        visible = false
        cancelCapture("Sesión detenida al salir de la pantalla.")
        super.onStop()
    }

    override fun onDestroy() {
        cancelCapture("Sesión cerrada.")
        super.onDestroy()
    }

    private fun updateState() {
        if (!::record.isInitialized) return
        record.isEnabled = visible && consent.isChecked && !running.get() &&
            recordings.size < rule.requiredSamples
        record.text = if (running.get()) "GRABANDO / PROCESANDO..." else
            "GRABAR MUESTRA ${minOf(recordings.size + 1, 3)}/3"
    }

    private fun startNextCapture() {
        if (running.get() || recordings.size >= rule.requiredSamples) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (consent.isChecked && visible) {
                status.text = "Autoriza el micrófono en Android; luego pulsa GRABAR otra vez."
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 902)
            }
            return
        }
        if (!rule.canCapture(consent.isChecked, true, visible, busy = false)) return
        if (!running.compareAndSet(false, true)) return
        updateState()
        val sampleNumber = recordings.size + 1
        status.text = "Grabando muestra $sampleNumber/3... Di «Jarvis, soy yo»."
        val thread = Thread({
            var mic: AudioRecord? = null
            var captured: ShortArray? = null
            var problem: String? = null
            try {
                val minBuffer = AudioRecord.getMinBufferSize(
                    16_000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                check(minBuffer > 0)
                mic = AudioRecord(MediaRecorder.AudioSource.MIC, 16_000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer * 2, 4096))
                currentRecorder = mic
                check(mic.state == AudioRecord.STATE_INITIALIZED)
                mic.startRecording()
                check(mic.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                val pcm = ShortArray(rule.captureSamples)
                var written = 0
                while (written < pcm.size && running.get() && !Thread.currentThread().isInterrupted) {
                    val n = mic.read(pcm, written, pcm.size - written, AudioRecord.READ_BLOCKING)
                    if (n <= 0) throw IllegalStateException("Micrófono: lectura $n")
                    written += n
                }
                if (running.get() && written == pcm.size && rule.validSample(pcm)) {
                    captured = pcm
                } else {
                    pcm.fill(0)
                    problem = "Grabación insuficiente, cancelada o silenciosa. Repite la muestra."
                }
            } catch (_: Exception) {
                problem = "No se pudo capturar voz: revisa permiso de micrófono y otras sesiones."
            } finally {
                try { mic?.stop() } catch (_: Exception) {}
                try { mic?.release() } catch (_: Exception) {}
                currentRecorder = null
            }
            val finalCaptured = captured
            val finalProblem = problem
            main.post {
                if (!running.get() || stopped || !visible || !consent.isChecked) {
                    finalCaptured?.fill(0)
                    return@post
                }
                if (finalCaptured != null) {
                    recordings.add(LocalVoiceEnrollmentRecording("capture-$sampleNumber", finalCaptured))
                }
                if (recordings.size == rule.requiredSamples) {
                    status.text = "Analizando tres muestras en el dispositivo. No cierres esta pantalla."
                    runLocalEnrollment()
                } else {
                    running.set(false)
                    status.text = finalProblem ?: "Muestra $sampleNumber/3 guardada solo en RAM."
                    updateState()
                }
            }
        }, "jarvis-owner-voice-capture")
        worker = thread
        thread.start()
    }

    private fun runLocalEnrollment() {
        val batch = recordings.toList()
        val thread = Thread({
            val result = try {
                SherpaSpeakerEnrollmentEngine(applicationContext)
                    .enroll(consentGranted = running.get() && visible && consent.isChecked, recordings = batch)
            } catch (_: Exception) { LocalVoiceEnrollmentOutcome.NATIVE_UNAVAILABLE }
            main.post {
                if (!stopped && visible) {
                    status.text = when (result) {
                        LocalVoiceEnrollmentOutcome.SAVED ->
                            "Perfil privado cifrado guardado. Reconocimiento de identidad no calibrado."
                        LocalVoiceEnrollmentOutcome.BAD_SAMPLES ->
                            "Muestras incompatibles; vuelve a comenzar el registro."
                        LocalVoiceEnrollmentOutcome.DENIED ->
                            "Inscripción cancelada o consentimiento retirado."
                        LocalVoiceEnrollmentOutcome.NATIVE_UNAVAILABLE ->
                            "Motor de identificación local no disponible."
                    }
                }
                clearRecordings()
                running.set(false)
                updateState()
            }
        }, "jarvis-owner-voice-extractor")
        worker = thread
        thread.start()
    }

    private fun clearRecordings() {
        recordings.forEach { it.samples16kPcm.fill(0) }
        recordings.clear()
    }

    private fun cancelCapture(message: String) {
        stopped = true
        running.set(false)
        try { currentRecorder?.stop() } catch (_: Exception) {}
        worker?.interrupt()
        clearRecordings()
        if (::status.isInitialized) status.text = message
        if (::record.isInitialized) updateState()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 902 && ::status.isInitialized) {
            status.text = if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                "Micrófono autorizado. Pulsa grabar para la muestra 1/3."
            else "Micrófono denegado. No se capturó ninguna muestra."
        }
    }
}
