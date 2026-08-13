package com.appguard.blocker.protection

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import com.appguard.blocker.admin.DeviceAdminHelper
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.service.UsageMonitorService
import com.appguard.blocker.ui.ProtectionChallengeActivity
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Kaspersky-style coordinator (Pixel Soft layer — proven effective):
 * tamper → leave threat UI → PIN challenge → short lease (~45s) then re-arm.
 */
object ProtectionController {

    /** Match Kaspersky ProtectionDefenderImpl.g(45). */
    const val LEASE_MS = 45_000L
    private const val CHALLENGE_DEBOUNCE_MS = 500L

    private val challengeLaunching = AtomicBoolean(false)
    private var lastChallengeAt = 0L
    private var lastChallengeReason: TamperReason? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var leaseExpireRunnable: Runnable? = null

    fun state(context: Context): ProtectionState {
        val prefs = PrefsRepository(context)
        expireLeaseIfNeeded(context)
        return when {
            !prefs.protectionArmed -> ProtectionState.DISARMED
            prefs.hasValidRemovalLease() -> ProtectionState.UNINSTALL_UNLOCKED
            prefs.challengeActive -> ProtectionState.CHALLENGE
            else -> ProtectionState.ARMED
        }
    }

    fun selfProtectActive(context: Context): Boolean {
        expireLeaseIfNeeded(context)
        return PrefsRepository(context).selfProtectEnabled()
    }

    fun onTamperDetected(context: Context, reason: TamperReason) {
        expireLeaseIfNeeded(context)
        if (!selfProtectActive(context)) return

        val now = System.currentTimeMillis()
        if (reason == lastChallengeReason && now - lastChallengeAt < CHALLENGE_DEBOUNCE_MS) return
        if (challengeLaunching.get() && now - lastChallengeAt < CHALLENGE_DEBOUNCE_MS) return

        lastChallengeAt = now
        lastChallengeReason = reason
        val prefs = PrefsRepository(context)
        prefs.challengeActive = true
        prefs.pendingTamperReason = reason.name

        launchChallenge(context, reason)
    }

    fun launchChallenge(context: Context, reason: TamperReason) {
        if (!challengeLaunching.compareAndSet(false, true)) return
        try {
            // Flags aligned with Kaspersky SelfProtectionBlockViewPresenter (0x54010000 family)
            val intent = Intent(context, ProtectionChallengeActivity::class.java).apply {
                addFlags(
                    // No NO_HISTORY: IME focus was destroying the PIN screen on tap
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                putExtra(ProtectionChallengeActivity.EXTRA_REASON, reason.name)
            }
            context.applicationContext.startActivity(intent)
        } catch (_: Exception) {
        } finally {
            mainHandler.postDelayed({ challengeLaunching.set(false) }, 500)
        }
    }

    fun onChallengeCancelled(context: Context) {
        val prefs = PrefsRepository(context)
        prefs.challengeActive = false
        prefs.pendingTamperReason = null
    }

    /**
     * @param navigateToSystem if false (default for a11y intercept): just open 45s window
     * and finish — user already sits on the system screen. Manual unlock navigates.
     */
    fun onPinSuccess(
        context: Context,
        reason: TamperReason,
        navigateToSystem: Boolean = reason == TamperReason.MANUAL_UNLOCK
    ) {
        val prefs = PrefsRepository(context)
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val lease = RemovalLease(
            reason = reason,
            issuedAtMs = nowWall,
            expiresAtMs = nowWall + LEASE_MS,
            nonce = UUID.randomUUID().toString(),
            expiresAtElapsedRealtime = nowElapsed + LEASE_MS
        )
        prefs.setRemovalLease(lease)
        prefs.challengeActive = false
        prefs.pendingTamperReason = null
        prefs.uninstallUnlocked = true

        // Uninstall needs admin gone during the window
        if (reason == TamperReason.UNINSTALL || reason == TamperReason.MANUAL_UNLOCK) {
            if (DeviceAdminHelper.isAdminActive(context)) {
                DeviceAdminHelper.removeAdmin(context)
            }
        }

        scheduleLeaseExpiry(context.applicationContext, lease.expiresAtElapsedRealtime)

        if (navigateToSystem) {
            openSystemTarget(context, reason)
        }
    }

    fun openSystemTarget(context: Context, reason: TamperReason) {
        val app = context.applicationContext
        try {
            when (reason) {
                TamperReason.DISABLE_DEVICE_ADMIN -> {
                    app.startActivity(
                        Intent(Settings.ACTION_SECURITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                TamperReason.DISABLE_ACCESSIBILITY -> {
                    PermissionOpeners.openAccessibilityDetails(app)
                }
                TamperReason.UNINSTALL, TamperReason.MANUAL_UNLOCK -> {
                    if (DeviceAdminHelper.isAdminActive(app)) {
                        DeviceAdminHelper.removeAdmin(app)
                    }
                    app.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${app.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            }
        } catch (_: Exception) {
            try {
                app.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }

    fun clearLease(context: Context) {
        cancelLeaseTimer()
        PrefsRepository(context).clearRemovalLease()
        PrefsRepository(context).uninstallUnlocked = false
    }

    fun arm(context: Context) {
        cancelLeaseTimer()
        val prefs = PrefsRepository(context)
        prefs.protectionArmed = true
        prefs.allowlistEnabled = true
        prefs.uninstallUnlocked = false
        prefs.challengeActive = false
        prefs.clearRemovalLease()
        runCatching {
            prefs.getRecentlyInstalled().toList().forEach { prefs.clearRecentlyInstalled(it) }
        }
        UsageMonitorService.start(context)
    }

    fun disarm(context: Context) {
        cancelLeaseTimer()
        val prefs = PrefsRepository(context)
        prefs.emergencyDisarm()
        prefs.challengeActive = false
        prefs.clearRemovalLease()
        UsageMonitorService.stop(context)
    }

    fun expireLeaseIfNeeded(context: Context) {
        val prefs = PrefsRepository(context)
        val lease = prefs.getRemovalLease() ?: return
        if (!lease.isValid()) {
            onLeaseExpired(context)
        }
    }

    private fun scheduleLeaseExpiry(appContext: Context, expiresAtElapsed: Long) {
        cancelLeaseTimer()
        val delayMs = (expiresAtElapsed - SystemClock.elapsedRealtime() + 250L).coerceAtLeast(0L)
        val r = Runnable {
            expireLeaseIfNeeded(appContext)
        }
        leaseExpireRunnable = r
        mainHandler.postDelayed(r, delayMs)
    }

    private fun cancelLeaseTimer() {
        leaseExpireRunnable?.let { mainHandler.removeCallbacks(it) }
        leaseExpireRunnable = null
    }

    private fun onLeaseExpired(context: Context) {
        val prefs = PrefsRepository(context)
        prefs.clearRemovalLease()
        prefs.uninstallUnlocked = false
        // Re-arm Device Admin if still installed and protection armed
        if (prefs.protectionArmed && prefs.setupCompleted &&
            !DeviceAdminHelper.isAdminActive(context)
        ) {
            try {
                // Can't silently re-add without user; show enable screen only if we can
                // Prefer soft: leave flag — next open of Settings in-app can request.
                // Notification path avoided; Main/Settings relock handles requestEnable.
            } catch (_: Exception) {
            }
        }
    }
}

object PermissionOpeners {
    fun openAccessibilityDetails(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }
}
