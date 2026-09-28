package com.appguard.blocker.data

enum class SignatureStatus {
    WHITE,
    /** Denied from the pending list. Hidden. Does not return on open. */
    REJECTED,
    PENDING
}

data class SignatureRecord(
    val sha256: String,
    val packageName: String,
    val label: String,
    val status: SignatureStatus
)
