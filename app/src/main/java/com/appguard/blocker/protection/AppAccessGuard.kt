package com.appguard.blocker.protection

import android.content.Context
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.service.BlockCoordinator
import java.util.concurrent.atomic.AtomicInteger

/**
 * Guardian must ALWAYS be openable with PIN.
 * While any guardian Activity is in foreground → self-protect OFF (no HOME kick).
 * When user leaves to Settings/Launcher → self-protect ON again.
 */
object AppAccessGuard {

    private val guardianForegroundCount = AtomicInteger(0)

    /** Call from Application.onCreate — prevent stuck count after process death. */
    fun reset() {
        guardianForegroundCount.set(0)
    }

    fun isOurPackage(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return packageName == context.packageName ||
            packageName == PrefsRepository.OUR_PACKAGE
    }

    fun onGuardianActivityStarted() {
        guardianForegroundCount.incrementAndGet()
    }

    fun onGuardianActivityStopped() {
        guardianForegroundCount.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    fun isGuardianInForeground(): Boolean = guardianForegroundCount.get() > 0

    fun onGuardianUiOpened(context: Context) {
        val prefs = PrefsRepository(context)
        if (prefs.challengeActive) {
            prefs.challengeActive = false
            prefs.pendingTamperReason = null
        }
        BlockCoordinator.dismissOverlay(context.applicationContext)
    }

    fun markGuardianUiActive() = Unit

    fun mayRunSelfProtect(context: Context): Boolean {
        // CRITICAL: never intercept while user is inside our app (PIN/Main/…)
        if (isGuardianInForeground()) return false
        return ProtectionController.selfProtectActive(context)
    }
}
