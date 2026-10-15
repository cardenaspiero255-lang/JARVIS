package com.cardenaspiero255.jarvis.core

/** Keep animated cosmetic effects out of the way of scrolling and accessibility. */
class OmegaMotionPolicy {
    fun shouldAnimate(attached: Boolean, focused: Boolean,
                      systemAnimations: Boolean, scrollActive: Boolean): Boolean =
        attached && focused && systemAnimations && !scrollActive
}
