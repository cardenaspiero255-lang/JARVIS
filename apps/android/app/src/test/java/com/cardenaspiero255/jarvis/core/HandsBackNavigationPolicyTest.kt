package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** TDD: Android Back is one local, explicitly tapped operation, never a voice tool. */
class HandsBackNavigationPolicyTest {
    private val policy = HandsPolicy()

    @Test fun backNeedsCurrentServiceArmAndAnExplicitTap() {
        val authorized = HandsContext(
            serviceConnected = true, emergencyStopped = false, explicitTap = true
        )
        assertEquals(HandsDecision.ALLOW_BACK,
            policy.evaluate(HandsOperation.GLOBAL_BACK, authorized))
        for (blocked in listOf(
            authorized.copy(serviceConnected = false),
            authorized.copy(emergencyStopped = true),
            authorized.copy(explicitTap = false),
            HandsContext()
        )) assertEquals(HandsDecision.DENY,
            policy.evaluate(HandsOperation.GLOBAL_BACK, blocked))
    }

    @Test fun backNeverImpliesGeneralAccessibilityAutomation() {
        val authorized = HandsContext(true, false, true)
        for (operation in listOf(
            HandsOperation.TYPE_TEXT, HandsOperation.CLICK_NODE,
            HandsOperation.READ_SCREEN, HandsOperation.LAUNCH_APP
        )) assertEquals(HandsDecision.DENY, policy.evaluate(operation, authorized))
    }

    @Test fun existingHomeAuthorizationRemainsIndependent() {
        assertEquals(HandsDecision.ALLOW_HOME,
            policy.evaluate(HandsOperation.GO_HOME, HandsContext(true, false, true)))
        assertEquals(HandsDecision.DENY,
            policy.evaluate(HandsOperation.GO_HOME, HandsContext(true, true, true)))
    }
}
