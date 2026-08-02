package com.appguard.blocker.protection

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
    val nonce: String
) {
    fun isValid(now: Long = System.currentTimeMillis()): Boolean =
        now in issuedAtMs..expiresAtMs
}
