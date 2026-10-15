package com.cardenaspiero255.jarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmegaMotionPolicyTest {
    private val policy = OmegaMotionPolicy()
    @Test fun respectsWindowAndSystemMotionRestrictions() {
        assertFalse(policy.shouldAnimate(attached = false, focused = true,
            systemAnimations = true, scrollActive = false))
        assertFalse(policy.shouldAnimate(attached = true, focused = false,
            systemAnimations = true, scrollActive = false))
        assertFalse(policy.shouldAnimate(attached = true, focused = true,
            systemAnimations = false, scrollActive = false))
        assertTrue(policy.shouldAnimate(attached = true, focused = true,
            systemAnimations = true, scrollActive = false))
    }
    @Test fun pausesDuringScrollingWithoutDroppingUI() {
        assertFalse(policy.shouldAnimate(attached = true, focused = true,
            systemAnimations = true, scrollActive = true))
        assertTrue(policy.shouldAnimate(attached = true, focused = true,
            systemAnimations = true, scrollActive = false))
    }
}
