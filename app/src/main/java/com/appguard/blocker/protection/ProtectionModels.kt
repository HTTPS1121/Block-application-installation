package com.appguard.blocker.protection

import android.os.SystemClock

/**
 * Kaspersky-style protection states.
 *
 * ARMED = normal blocking
 * CHALLENGE = PIN screen open due to tamper
 * UNINSTALL_UNLOCKED = short lease after correct PIN to finish system uninstall/admin disable
 * DISARMED = setup / fully released
 */
enum class ProtectionState {
    DISARMED,
    ARMED,
    CHALLENGE,
    UNINSTALL_UNLOCKED
}

enum class TamperReason {
    UNINSTALL,
    DISABLE_DEVICE_ADMIN,
    DISABLE_ACCESSIBILITY,
    MANUAL_UNLOCK
}

data class RemovalLease(
    val reason: TamperReason,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
    val nonce: String,
    /** [SystemClock.elapsedRealtime] deadline — immune to wall-clock jumps. */
    val expiresAtElapsedRealtime: Long = 0L
) {
    /**
     * Valid only while BOTH clocks agree the window is open (more restrictive wins).
     * Wall clock covers process restart; elapsed covers NTP / manual time changes.
     */
    fun isValid(
        nowWall: Long = System.currentTimeMillis(),
        nowElapsed: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        if (nowWall < issuedAtMs || nowWall >= expiresAtMs) return false
        if (expiresAtElapsedRealtime > 0L && nowElapsed >= expiresAtElapsedRealtime) return false
        return true
    }
}
