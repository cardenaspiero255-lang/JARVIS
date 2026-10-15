package com.cardenaspiero255.jarvis.core

/**
 * GameHub Ultra's applicationId as verified in its actual build.gradle.kts.
 * This is an explicit application launch, NOT trusted IPC or a game booster.
 */
class GameHubLaunchPolicy {
    val packageName = "com.cardenaspiero255.gamehubultra"
    val supportsRemoteCommands: Boolean get() = false

    fun canLaunch(explicitUserTap: Boolean, appInstalled: Boolean, resolvedPackage: String): Boolean =
        explicitUserTap && appInstalled && resolvedPackage == packageName
}
