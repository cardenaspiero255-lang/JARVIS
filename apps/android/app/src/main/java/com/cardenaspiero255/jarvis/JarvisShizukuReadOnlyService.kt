package com.cardenaspiero255.jarvis

import android.os.Process
import com.cardenaspiero255.jarvis.core.ShizukuFixedProbe
import com.cardenaspiero255.jarvis.core.ShizukuFixedProbePolicy
import com.cardenaspiero255.jarvis.core.ShizukuRequestBudget
import java.util.concurrent.TimeUnit

/**
 * Only loaded by an explicit Shizuku UserService bind; not an Android exported service.
 * Linux UID must be 2000 (ADB/shell). Two fixed, read-only commands, no text input.
 */
class JarvisShizukuReadOnlyService : IJarvisShizukuReadOnly.Stub() {
    override fun remoteUid(): Int = Process.myUid()

    override fun readProbe(fixedCode: Int): String {
        if (!ShizukuFixedProbePolicy.isAllowedUid(Process.myUid())) return "ERROR:IDENTITY"
        val probe = ShizukuFixedProbe.fromCode(fixedCode) ?: return "ERROR:NOT_ALLOWED"
        var command: java.lang.Process? = null
        return try {
            command = ProcessBuilder(probe.command).start()
            if (!command.waitFor(ShizukuRequestBudget.PROCESS_WAIT_MS, TimeUnit.MILLISECONDS)) {
                command.destroyForcibly()
                "ERROR:TIMEOUT"
            } else if (command.exitValue() != 0) {
                "ERROR:EXIT"
            } else {
                val output = command.inputStream.bufferedReader().use { it.readText().trim() }
                if (ShizukuFixedProbePolicy.isValid(probe, output)) output
                else "ERROR:INVALID_OUTPUT"
            }
        } catch (_: Exception) {
            "ERROR:UNAVAILABLE"
        } finally {
            command?.destroy()
        }
    }

    // Shizuku UserService-specific destroy transaction (not an Android Service callback).
    override fun destroy() {
        kotlin.system.exitProcess(0)
    }
}
