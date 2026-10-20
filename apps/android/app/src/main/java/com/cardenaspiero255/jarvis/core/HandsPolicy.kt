package com.cardenaspiero255.jarvis.core

/**
 * Narrow runtime authority boundary. Enabling an Android accessibility
 * service alone never gives an AI or background event permission to act.
 */
enum class HandsOperation {
    GO_HOME,
    CLICK_NODE,
    TYPE_TEXT,
    READ_SCREEN,
    LAUNCH_APP,
    GLOBAL_BACK
}

enum class HandsDecision { ALLOW_HOME, ALLOW_BACK, DENY }

data class HandsContext(
    val serviceConnected: Boolean = false,
    val emergencyStopped: Boolean = true,
    val explicitTap: Boolean = false,
)

class HandsPolicy {
    fun evaluate(operation: HandsOperation, context: HandsContext): HandsDecision {
        if (!context.serviceConnected || context.emergencyStopped || !context.explicitTap)
            return HandsDecision.DENY
        return when (operation) {
            HandsOperation.GO_HOME -> HandsDecision.ALLOW_HOME
            HandsOperation.GLOBAL_BACK -> HandsDecision.ALLOW_BACK
            else -> HandsDecision.DENY
        }
    }
}
