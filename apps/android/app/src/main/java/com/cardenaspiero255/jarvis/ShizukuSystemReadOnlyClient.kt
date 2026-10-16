package com.cardenaspiero255.jarvis

import android.app.Activity
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.cardenaspiero255.jarvis.core.ShizukuFixedProbe
import com.cardenaspiero255.jarvis.core.ShizukuFixedProbePolicy
import java.util.concurrent.Executors
import rikka.shizuku.Shizuku

/**
 * One explicitly tapped, foreground-only remote query. Real data comes from a
 * Shizuku UserService, not a local app-level shell nor the Shizuku manager UID.
 */
internal class ShizukuSystemReadOnlyClient(private val activity: Activity) {
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var closed = false
    private var generation = 0
    private var pending: ((String) -> Unit)? = null
    private var args: Shizuku.UserServiceArgs? = null
    private var connection: ServiceConnection? = null
    private var timeout: Runnable? = null

    fun request(callback: (String) -> Unit) = requestInternal(false, callback)

    fun requestAdvanced(callback: (String) -> Unit) = requestInternal(true, callback)

    private fun requestInternal(advanced: Boolean, callback: (String) -> Unit) {
        cancel()
        if (closed) return
        val requestId = generation
        val preflight = try {
            when {
                !Shizuku.pingBinder() -> "Shizuku está detenido. Inicia su servicio primero."
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                    "JARVIS necesita la autorización SDK de Shizuku."
                Shizuku.getVersion() < 13 ->
                    "La versión de API Shizuku no admite este servicio de diagnóstico."
                !ShizukuFixedProbePolicy.isAllowedUid(Shizuku.getUid()) ->
                    "Se esperaba un servidor ADB/shell UID 2000. No se inicia la prueba."
                else -> null
            }
        } catch (_: RuntimeException) {
            "No se pudo comprobar el estado de Shizuku."
        }
        if (preflight != null) {
            callback(preflight)
            return
        }
        pending = callback
        val newArgs = Shizuku.UserServiceArgs(
            ComponentName(activity.packageName, JarvisShizukuReadOnlyService::class.java.name)
        ).daemon(false).tag("jarvis_privileged_readonly_v1").version(1)
            .processNameSuffix("readonly")
        args = newArgs
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (closed || pending == null) return
                if (!binder.pingBinder()) {
                    complete("El UserService de Shizuku devolvió un Binder inactivo.", requestId)
                    return
                }
                executor.execute {
                    val report = try {
                        val remote = IJarvisShizukuReadOnly.Stub.asInterface(binder)
                        val uid = remote.remoteUid()
                        if (!ShizukuFixedProbePolicy.isAllowedUid(uid)) {
                            "El proceso remoto tiene UID $uid, no ADB/shell. Prueba rechazada."
                        } else {
                            val release = remote.readProbe(ShizukuFixedProbe.ANDROID_RELEASE.code)
                            val systemPackage = remote.readProbe(
                                ShizukuFixedProbe.ANDROID_SYSTEM_PACKAGE.code)
                            if (ShizukuFixedProbePolicy.complete(uid, release, systemPackage)) {
                                "Consulta ADB/shell verificada (UserService remoto UID $uid).\n" +
                                    "Versión Android: $release\n" +
                                    "Paquete de sistema: $systemPackage\n" +
                                    "No se modificaron configuraciones ni permisos." +
                                    if (advanced) advancedReport(remote) else ""
                            } else {
                                "El UserService respondió, pero una de las consultas fue " +
                                    "rechazada o no pudo validarse. No se acredita la operación."
                            }
                        }
                    } catch (_: Exception) {
                        "Falló la consulta mediante Binder. No se acredita la operación."
                    }
                    main.post { complete(report, requestId) }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                complete("El UserService de Shizuku se desconectó; prueba no verificada.", requestId)
            }
        }
        connection = conn
        val timer = Runnable {
            complete("Tiempo de espera agotado al consultar Shizuku.", requestId)
        }
        timeout = timer
        main.postDelayed(timer, 12_000L)
        try {
            Shizuku.bindUserService(newArgs, conn)
        } catch (_: RuntimeException) {
            complete("Shizuku rechazó iniciar el servicio remoto; revisa sus permisos.", requestId)
        }
    }

    /** Extra read-only checks; each result must pass its own strict validator. */
    private fun advancedReport(remote: IJarvisShizukuReadOnly): String {
        val probes = listOf(
            ShizukuFixedProbe.ZYGOTE_SERVICE,
            ShizukuFixedProbe.DISPLAY_SERVICE,
            ShizukuFixedProbe.SYSTEM_SERVER_PID,
            ShizukuFixedProbe.SETTINGS_PACKAGE,
            ShizukuFixedProbe.WINDOW_ANIMATION_SCALE
        )
        val results = probes.map { probe ->
            val value = try { remote.readProbe(probe.code) } catch (_: Exception) { "" }
            if (ShizukuFixedProbePolicy.isValid(probe, value))
                "${probe.label}: ${value.take(120)}"
            else "${probe.label}: no verificable"
        }
        return "\\n\\nDiagnóstico avanzado (solo lectura):\\n" + results.joinToString("\\n") +
            "\\nLa disponibilidad depende de Android/Funtouch OS; no se aplicaron cambios."
    }

    private fun complete(message: String, requestId: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { complete(message, requestId) }
            return
        }
        if (requestId != generation) return
        val cb = pending ?: return
        cancel()
        if (!closed) cb(message)
    }

    fun cancel() {
        generation++
        timeout?.let { main.removeCallbacks(it) }
        timeout = null
        val currentArgs = args
        val currentConn = connection
        args = null
        connection = null
        pending = null
        if (currentArgs != null && currentConn != null) {
            try {
                Shizuku.unbindUserService(currentArgs, currentConn, true)
            } catch (_: RuntimeException) {
                // Server may have died already; nothing is trusted on disconnect.
            }
        }
    }

    fun close() {
        closed = true
        cancel()
        executor.shutdownNow()
    }
}
