package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HandsPolicyTest {
    private val policy = HandsPolicy()

    @Test fun goHomeRequiresLiveAndroidServiceExplicitTapAndArmedState() {
        val approved = HandsContext(serviceConnected = true, emergencyStopped = false, explicitTap = true)
        assertEquals(HandsDecision.ALLOW_HOME, policy.evaluate(HandsOperation.GO_HOME, approved))
        assertEquals(HandsDecision.DENY, policy.evaluate(HandsOperation.GO_HOME, approved.copy(serviceConnected = false)))
        assertEquals(HandsDecision.DENY, policy.evaluate(HandsOperation.GO_HOME, approved.copy(emergencyStopped = true)))
        assertEquals(HandsDecision.DENY, policy.evaluate(HandsOperation.GO_HOME, approved.copy(explicitTap = false)))
    }

    @Test fun unimplementedScreenActionsAreDeniedEvenIfServiceIsEnabled() {
        val context = HandsContext(serviceConnected = true, emergencyStopped = false, explicitTap = true)
        for (action in listOf(HandsOperation.CLICK_NODE, HandsOperation.TYPE_TEXT,
                              HandsOperation.READ_SCREEN, HandsOperation.LAUNCH_APP)) {
            assertEquals(HandsDecision.DENY, policy.evaluate(action, context))
        }
    }

    @Test fun defaultContextFailsClosed() {
        assertEquals(HandsDecision.DENY,
            policy.evaluate(HandsOperation.GO_HOME, HandsContext()))
    }
}
