package com.cardenaspiero255.jarvis.core

/** Every process starts with memory access disabled until a visible tap. */
class ConversationMemoryPolicy {
    @Volatile private var consent = false

    fun enable(explicitUserTap: Boolean): Boolean {
        if (explicitUserTap) consent = true
        return consent
    }

    fun canReadOrWrite(): Boolean = consent
    fun stop() { consent = false }
}
