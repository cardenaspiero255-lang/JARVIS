package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameHubLaunchPolicyTest {
    private val policy = GameHubLaunchPolicy()

    @Test fun packageMustMatchRealGameHubUltraApplicationId() {
        assertEquals("com.cardenaspiero255.gamehubultra", policy.packageName)
    }

    @Test fun launchRequiresExplicitTapAndExactPackage() {
        assertTrue(policy.canLaunch(true, true, "com.cardenaspiero255.gamehubultra"))
        assertFalse(policy.canLaunch(false, true, "com.cardenaspiero255.gamehubultra"))
        assertFalse(policy.canLaunch(true, false, "com.cardenaspiero255.gamehubultra"))
        assertFalse(policy.canLaunch(true, true, "com.attacker.gamehubultra"))
        assertFalse(policy.canLaunch(true, true, ""))
    }

    @Test fun noOtherAppLaunchOrPrivilegedControlsAreGranted() {
        assertFalse(policy.supportsRemoteCommands)
        assertFalse(policy.canLaunch(false, false, policy.packageName))
    }
}
