package com.cardenaspiero255.jarvis.core

import java.net.URI
import java.util.Locale

/**
 * Pure, security-sensitive AI connection policy. The general model can only
 * return text; it cannot authorize an Android action, screen capture, or tool.
 */
object AiGatewayPolicy {
    // Public service address, not a credential. Safe to include in the APK.
    // The OpenAI API key remains on Vercel and the device token in Keystore.
    const val DEFAULT_ENDPOINT = "https://jarvis-ai-gateway.vercel.app/v1/chat"

    fun officialEndpointAllowed(raw: String?): Boolean =
        raw == DEFAULT_ENDPOINT && endpointAllowed(raw)

    fun endpointAllowed(raw: String?): Boolean {
        if (raw.isNullOrBlank() || raw.length > 220 || raw != raw.trim()) return false
        return try {
            val uri = URI(raw)
            val host = uri.host?.lowercase(Locale.ROOT) ?: return false
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.rawUserInfo == null && uri.rawQuery == null &&
                uri.rawFragment == null && uri.port in listOf(-1, 443) &&
                uri.rawPath == "/v1/chat" &&
                host != "localhost" && !host.endsWith(".local") &&
                !host.matches(Regex("""[0-9.]+""")) &&
                !host.contains(":") && host.contains(".")
        } catch (_: Exception) { false }
    }

    fun tokenAllowed(token: String?): Boolean =
        token != null && token.length in 32..256 &&
            token.none { it.isWhitespace() || Character.isISOControl(it) } &&
            !token.startsWith("sk-") && !token.startsWith("sk-proj-")

    fun allowCloudRequest(paired: Boolean, voiceConsent: Boolean,
                          fromVoice: Boolean): Boolean =
        paired && (!fromVoice || voiceConsent)

    fun spokenModelText(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        // TTS is a readout only: no markdown, SSML or hidden control chars.
        val safe = raw.replace(Regex("""[\p{Cc}\p{Cf}]+"""), " ")
            .replace(Regex("""[*_#\x60<>]+"""), "")
            .replace(Regex("""\s+"""), " ").trim().take(1200)
        return safe.takeIf { it.isNotBlank() }
    }
}
