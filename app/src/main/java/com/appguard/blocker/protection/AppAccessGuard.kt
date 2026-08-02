package com.appguard.blocker.protection

import android.content.Context
import android.os.SystemClock
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.service.BlockCoordinator
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Guardian must ALWAYS be openable with PIN.
 * While any guardian Activity is in foreground → no HOME kick / no allowlist block.
 */
object AppAccessGuard {

    private val guardianForegroundCount = AtomicInteger(0)
    private val challengeUiShowing = AtomicBoolean(false)
    @Volatile
    private var launchGraceUntilElapsed = 0L

    fun reset() {
        guardianForegroundCount.set(0)
        challengeUiShowing.set(false)
        launchGraceUntilElapsed = 0L
    }

    fun isOurPackage(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return packageName == context.packageName ||
            packageName == PrefsRepository.OUR_PACKAGE
    }

    fun onGuardianActivityStarted() {
        guardianForegroundCount.incrementAndGet()
        armLaunchGrace()
    }

    fun onGuardianActivityStopped() {
        val left = guardianForegroundCount.updateAndGet { (it - 1).coerceAtLeast(0) }
        // No post-exit hole: the moment guardian is gone, kicks may run again
        if (left == 0) {
            launchGraceUntilElapsed = 0L
        }
    }

    fun onChallengeUiStarted() {
        challengeUiShowing.set(true)
    }

    fun onChallengeUiStopped() {
        challengeUiShowing.set(false)
    }

    fun isChallengeUiShowing(): Boolean = challengeUiShowing.get()

    fun isGuardianInForeground(): Boolean = guardianForegroundCount.get() > 0

    fun inLaunchGrace(): Boolean =
        SystemClock.elapsedRealtime() < launchGraceUntilElapsed

    private fun armLaunchGrace() {
        launchGraceUntilElapsed = SystemClock.elapsedRealtime() + 2_500L
    }

    /**
     * Block/HOME must not run while guardian UI is up, or for a short beat while it opens
     * (UsageStats still naming the previous app). Grace is wiped when guardian fully stops.
     */
    fun mustNotKickGuardian(): Boolean =
        isGuardianInForeground() || inLaunchGrace()

    fun onGuardianUiOpened(context: Context) {
        armLaunchGrace()
        BlockCoordinator.dismissOverlay(context.applicationContext)

        // Clear stale challenge flag only when Challenge UI is not on screen
        val prefs = PrefsRepository(context)
        if (prefs.challengeActive && !challengeUiShowing.get()) {
            prefs.challengeActive = false
            prefs.pendingTamperReason = null
        }
    }

    fun markGuardianUiActive() {
        // Only extend grace while we already know guardian UI is up (avoid notification noise)
        if (isGuardianInForeground()) armLaunchGrace()
    }

    fun mayRunSelfProtect(context: Context): Boolean {
        if (isGuardianInForeground()) return false
        if (isChallengeUiShowing()) return false
        return ProtectionController.selfProtectActive(context)
    }
}
