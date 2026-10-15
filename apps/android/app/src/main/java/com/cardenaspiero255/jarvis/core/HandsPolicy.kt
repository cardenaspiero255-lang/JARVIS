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

enum class HandsDecision { ALLOW_HOME, DENY }

data class HandsContext(
    val serviceConnected: Boolean = false,
    val emergencyStopped: Boolean = true,
    val explicitTap: Boolean = false,
)

class HandsPolicy {
    fun evaluate(operation: HandsOperation, context: HandsContext): HandsDecision =
        if (operation == HandsOperation.GO_HOME &&
            context.serviceConnected &&
            !context.emergencyStopped &&
            context.explicitTap
        ) HandsDecision.ALLOW_HOME else HandsDecision.DENY
}
