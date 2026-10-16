package com.cardenaspiero255.jarvis.core

/** Do not confuse an installed companion app with an authorized, working API. */
enum class CompanionKind { SHIZUKU, DHIZUKU, OWNDROID, TERMUX }
enum class CompanionState { NOT_INSTALLED, NEEDS_START, NEEDS_PERMISSION, NEEDS_SETUP, READY }

data class CompanionEvidence(
    val installed: Boolean,
    val transportReady: Boolean = false,
    val authorized: Boolean = false,
)

class CompanionConnectionPolicy {
    fun state(kind: CompanionKind, evidence: CompanionEvidence): CompanionState {
        if (!evidence.installed) return CompanionState.NOT_INSTALLED
        if (kind != CompanionKind.SHIZUKU) {
            // Dhizuku needs Device Owner + API permission; OwnDroid needs an
            // authenticated API key; Termux needs two separate user opt-ins.
            // Installation is NOT evidence of any of those conditions.
            return CompanionState.NEEDS_SETUP
        }
        if (!evidence.transportReady) return CompanionState.NEEDS_START
        if (!evidence.authorized) return CompanionState.NEEDS_PERMISSION
        return CompanionState.READY
    }

    fun canOpenCompanion(installed: Boolean, explicitTap: Boolean) =
        installed && explicitTap

    /** No shell, administrative policy, broadcasts or scripts in the discovery phase. */
    fun mayExecutePrivilegedAction(): Boolean = false
}
