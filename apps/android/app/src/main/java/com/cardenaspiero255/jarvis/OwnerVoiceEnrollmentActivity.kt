package com.cardenaspiero255.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.ContentResolver
import android.net.Uri
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
    private var verifyingOnly = false
    private var filePickerOpen = false
    private var pendingWavs: List<Uri>? = null
    @Volatile private var visible = false
    @Volatile private var stopped = false
    private val enrollmentAllowed = AtomicBoolean(false)
    @Volatile private var currentRecorder: AudioRecord? = null
    @Volatile private var worker: Thread? = null
    private lateinit var consent: CheckBox
    private lateinit var status: TextView
    private lateinit var record: Button
    private lateinit var importButton: Button

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
            text = "Autorizo registrar mi perfil con tres audios locales, grabados o seleccionados"
            setTextColor(Color.WHITE)
            isChecked = false
            setOnCheckedChangeListener { _, checked ->
                enrollmentAllowed.set(checked && visible)
                if (!checked) cancelCapture("Consentimiento retirado; no se conserva audio.")
                else if (visible) stopped = false
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
        importButton = Button(this).apply {
            text = "IMPORTAR 3 WAV · 16 KHZ PCM16"
            setOnClickListener { selectThreeLocalWavs() }
        }
        root.addView(importButton)
        root.addView(label(
            "Importación opcional desde Archivos: selecciona tres WAV a la vez. " +
                "Si están en un ZIP, extráelos primero. Solo se leen en RAM; " +
                "no se copian, suben ni guardan las grabaciones.", 13f
        ))
        root.addView(Button(this).apply {
            text = "PROBAR MI VOZ · DIAGNÓSTICO"
            setOnClickListener {
                if (running.get() || recordings.isNotEmpty() || !visible || !consent.isChecked) {
                    status.text = "Finaliza o cancela el registro y acepta el consentimiento " +
                        "antes de probar la voz."
                } else {
                    verifyingOnly = true
                    startNextCapture()
                }
            }
        })
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
        enrollmentAllowed.set(consent.isChecked)
        updateState()
        consumeSelectedWavsIfReady()
    }

    override fun onStop() {
        visible = false
        enrollmentAllowed.set(false)
        // System document picker can stop this Activity, without touching PCM.
        // All other transitions cancel active import/capture as before.
        if (!filePickerOpen || running.get() || recordings.isNotEmpty()) {
            cancelCapture("Sesión detenida al salir de la pantalla.")
        }
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
        if (::importButton.isInitialized) {
            importButton.isEnabled = visible && consent.isChecked && !running.get() &&
                recordings.isEmpty() && !filePickerOpen
        }
    }

    private fun selectThreeLocalWavs() {
        if (!visible || !consent.isChecked || running.get() ||
            recordings.isNotEmpty() || filePickerOpen) return
        filePickerOpen = true
        pendingWavs = null
        status.text = "Selecciona exactamente tres archivos WAV PCM16 mono 16 kHz."
        updateState()
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivityForResult(intent, 903)
        } catch (_: Exception) {
            filePickerOpen = false
            status.text = "Selector de archivos de Android no disponible."
            updateState()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 903) return
        filePickerOpen = false
        if (resultCode != RESULT_OK || data == null || !consent.isChecked) {
            status.text = "Importación cancelada: sin archivos retenidos."
            updateState()
            return
        }
        val clip = data.clipData
        val selected = if (clip != null) {
            if (clip.itemCount == 3) (0 until clip.itemCount).map { clip.getItemAt(it).uri }
            else emptyList()
        } else listOfNotNull(data.data)
        if (selected.size != 3 || selected.distinct().size != 3 ||
            selected.any { it.scheme != ContentResolver.SCHEME_CONTENT }) {
            status.text = "Selecciona exactamente tres WAV locales diferentes."
            updateState()
            return
        }
        pendingWavs = selected
        consumeSelectedWavsIfReady()
    }

    private fun consumeSelectedWavsIfReady() {
        val selected = pendingWavs ?: return
        if (!visible || filePickerOpen) return
        pendingWavs = null
        if (!consent.isChecked || stopped || !enrollmentAllowed.get() ||
            recordings.isNotEmpty() || !running.compareAndSet(false, true)) {
            status.text = "Importación denegada; consentimiento o sesión no disponibles."
            return
        }
        updateState()
        status.text = "Validando los tres WAV en memoria; no cierres la pantalla."
        val thread = Thread({
            val decoded = ArrayList<LocalVoiceEnrollmentRecording>(3)
            val seen = HashSet<String>(3)
            var problem: String? = null
            try {
                for ((index, uri) in selected.withIndex()) {
                    check(running.get() && enrollmentAllowed.get() && visible &&
                        !stopped && !Thread.currentThread().isInterrupted) {
                        "Sesión de importación cancelada"
                    }
                    val stream = contentResolver.openInputStream(uri)
                        ?: throw IllegalArgumentException("WAV inaccesible")
                    val result = OwnerVoiceWavDecoder.decode(stream, isActive = {
                        running.get() && enrollmentAllowed.get() && visible &&
                            !stopped && !Thread.currentThread().isInterrupted
                    })
                    if (!seen.add(result.sha256)) {
                        result.samples.fill(0)
                        throw IllegalArgumentException("Grabación WAV duplicada")
                    }
                    decoded.add(LocalVoiceEnrollmentRecording("import-${index + 1}",
                        result.samples))
                }
            } catch (_: Exception) {
                problem = "WAV inválido, repetido, inaccesible o sesión cancelada. " +
                    "Solo PCM16 mono 16 kHz de 1,5 a 10 segundos."
            }
            main.post {
                if (!running.get() || !enrollmentAllowed.get() ||
                    stopped || !visible || !consent.isChecked) {
                    decoded.forEach { it.samples16kPcm.fill(0) }
                    return@post
                }
                if (problem != null || decoded.size != 3) {
                    decoded.forEach { it.samples16kPcm.fill(0) }
                    running.set(false)
                    status.text = problem ?: "No se consiguieron tres WAV válidos."
                    updateState()
                } else {
                    recordings.addAll(decoded)
                    status.text = "Tres WAV comprobados; ejecutando ERes2Net local."
                    runLocalEnrollment()
                }
            }
        }, "jarvis-owner-wav-import")
        worker = thread
        thread.start()
    }

    private fun startNextCapture() {
        if (running.get() || recordings.size >= rule.requiredSamples) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val wasVerifying = verifyingOnly
            verifyingOnly = false // Permission flow must not silently change the next tap mode.
            if (consent.isChecked && visible) {
                status.text = if (wasVerifying)
                    "Autoriza el micrófono y luego pulsa PROBAR MI VOZ otra vez."
                else "Autoriza el micrófono en Android; luego pulsa GRABAR otra vez."
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
                if (verifyingOnly) {
                    verifyingOnly = false
                    if (finalCaptured != null) {
                        status.text = "Analizando similitud con tu plantilla cifrada. " +
                            "No concede identidad ni permisos."
                        runLocalVerification(finalCaptured)
                    } else {
                        running.set(false)
                        status.text = finalProblem ?: "No se pudo capturar la voz."
                        updateState()
                    }
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
                    .enroll(
                        consentGranted = enrollmentAllowed.get(),
                        recordings = batch,
                        isActive = { running.get() && enrollmentAllowed.get() &&
                            !stopped && visible && !Thread.currentThread().isInterrupted }
                    )
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

    /** Independent spoken sample: no reusing enrollment audio or granting MATCHED. */
    private fun runLocalVerification(pcm: ShortArray) {
        val thread = Thread({
            val result = try {
                SherpaOwnerVoiceProbeEngine(applicationContext).probe(
                    pcm, isActive = { running.get() && enrollmentAllowed.get() &&
                        !stopped && visible && !Thread.currentThread().isInterrupted }
                )
            } catch (_: Exception) {
                OwnerVoiceProbeResult(OwnerVoiceProbeStatus.NATIVE_UNAVAILABLE)
            } finally {
                pcm.fill(0)
            }
            main.post {
                if (!stopped && visible && consent.isChecked) {
                    status.text = when (result.status) {
                        OwnerVoiceProbeStatus.NO_ENROLLMENT ->
                            "No hay plantilla inscrita. Registra primero tres muestras."
                        OwnerVoiceProbeStatus.BAD_AUDIO ->
                            "Muestra insuficiente o inválida. Intenta de nuevo."
                        OwnerVoiceProbeStatus.NATIVE_UNAVAILABLE ->
                            "No se pudo ejecutar la comparación de voz en este dispositivo."
                        OwnerVoiceProbeStatus.SIMILARITY_UNCALIBRATED ->
                            "Similitud local: " +
                                "%.3f".format(java.util.Locale.ROOT, result.cosineSimilarity ?: 0.0) +
                                ". NO identifica ni autentica al propietario; " +
                                "pendiente calibración y anti-replay."
                    }
                }
                running.set(false)
                updateState()
            }
        }, "jarvis-owner-voice-probe")
        worker = thread
        thread.start()
    }

    private fun clearRecordings() {
        recordings.forEach { it.samples16kPcm.fill(0) }
        recordings.clear()
    }

    private fun cancelCapture(message: String) {
        pendingWavs = null
        filePickerOpen = false
        verifyingOnly = false
        stopped = true
        running.set(false)
        enrollmentAllowed.set(false)
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
