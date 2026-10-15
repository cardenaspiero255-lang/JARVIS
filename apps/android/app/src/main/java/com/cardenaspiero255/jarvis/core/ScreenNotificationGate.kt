package com.cardenaspiero255.jarvis.core

/** Gate projection on a visible stop channel when Android requires a grant. */
object ScreenNotificationGate {
    fun canBegin(androidSdk: Int, notificationsGranted: Boolean): Boolean =
        androidSdk < 33 || notificationsGranted
}
