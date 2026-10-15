package com.cardenaspiero255.jarvis.core

import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Android OS consent result only arms one queued MediaProjection start.
 * Revocation wins over a service start which has not consumed the permit yet.
 */
class ProjectionStartPermit {
    private val authorized = AtomicBoolean(false)

    fun arm(): Boolean = authorized.compareAndSet(false, true)
    fun consume(): Boolean = authorized.compareAndSet(true, false)
    fun revoke() { authorized.set(false) }
    fun pending(): Boolean = authorized.get()
}
