package com.cardenaspiero255.jarvis.core

/** Versioned Spanish ASR asset. Files are verified before any native inference. */
class NativeSpanishModelSafety(private val root: String) {
    fun relative(archiveName: String): String? {
        if (archiveName.isBlank() || archiveName.contains('\\') ||
            archiveName.contains('\u0000') || archiveName.startsWith('/') ||
            Regex("^[a-zA-Z]:").containsMatchIn(archiveName)
        ) return null
        if (archiveName == "$root/") return ""
        if (!archiveName.startsWith("$root/")) return null
        val relative = archiveName.removePrefix("$root/")
        // ZIP directory entries legitimately end in one slash, but repeated
        // internal separators and traversal segments are never acceptable.
        val checked = relative.removeSuffix("/")
        if (checked.isEmpty() || checked.split('/').any {
                it.isEmpty() || it == "." || it == ".."
            }) return null
        return relative
    }

    fun acceptEntrySize(entryBytes: Long, expandedSoFar: Long): Boolean =
        entryBytes >= 0L && expandedSoFar >= 0L &&
            entryBytes <= MAX_EXPANDED_BYTES &&
            expandedSoFar <= MAX_EXPANDED_BYTES - entryBytes

    companion object {
        const val ROOT = "vosk-model-small-es-0.42"
        const val ASSET_NAME = "$ROOT.zip"
        const val SHA256 = "09b239888f633ef2f0b4e09736e3d9936acfd810bc65d53fad45261762c6511f"
        const val MAX_EXPANDED_BYTES = 350_000_000L
        const val MAX_ENTRIES = 1200
    }
}
