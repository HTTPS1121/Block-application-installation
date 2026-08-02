package com.appguard.blocker.protection

import android.content.Context
import android.os.SystemClock
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.service.BlockCoordinator
import java.util.concurrent.atomic.AtomicInteger

/**
 * Guardian must ALWAYS be openable with PIN.
 * While any guardian Activity is in foreground → no HOME kick / no allowlist block.
 */
object AppAccessGuard {

    private val guardianForegroundCount = AtomicInteger(0)
    @Volatile
    private var launchGraceUntilElapsed = 0L

    /** Call from Application.onCreate — prevent stuck count after process death. */
    fun reset() {
        guardianForegroundCount.set(0)
        launchGraceUntilElapsed = 0L
    }

    fun isOurPackage(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return packageName == context.packageName ||
            packageName == PrefsRepository.OUR_PACKAGE
    }

    fun onGuardianActivityStarted() {
        guardianForegroundCount.incrementAndGet()
        // UsageStats often still reports the previous app for a beat — don't HOME us away
        launchGraceUntilElapsed = SystemClock.elapsedRealtime() + 2_500L
    }

    fun onGuardianActivityStopped() {
        guardianForegroundCount.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    fun isGuardianInForeground(): Boolean = guardianForegroundCount.get() > 0

    fun inLaunchGrace(): Boolean =
        SystemClock.elapsedRealtime() < launchGraceUntilElapsed

    /** True while our UI is up OR just opened — block/HOME must not run. */
    fun mustNotKickGuardian(): Boolean =
        isGuardianInForeground() || inLaunchGrace()

    fun onGuardianUiOpened(context: Context) {
        val prefs = PrefsRepository(context)
        if (prefs.challengeActive) {
            prefs.challengeActive = false
            prefs.pendingTamperReason = null
        }
        BlockCoordinator.dismissOverlay(context.applicationContext)
        launchGraceUntilElapsed = SystemClock.elapsedRealtime() + 2_500L
    }

    fun markGuardianUiActive() {
        launchGraceUntilElapsed = SystemClock.elapsedRealtime() + 2_500L
    }

    fun mayRunSelfProtect(context: Context): Boolean {
        if (mustNotKickGuardian()) return false
        return ProtectionController.selfProtectActive(context)
    }
}
