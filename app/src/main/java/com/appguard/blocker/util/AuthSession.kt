package com.appguard.blocker.util

import java.util.concurrent.atomic.AtomicBoolean

/** In-memory session: cleared when the whole app goes to background. */
object AuthSession {
    private val unlocked = AtomicBoolean(false)

    fun isUnlocked(): Boolean = unlocked.get()

    fun unlock() {
        unlocked.set(true)
    }

    fun lock() {
        unlocked.set(false)
    }
}
