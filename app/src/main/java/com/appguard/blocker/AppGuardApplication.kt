package com.appguard.blocker

import android.app.Activity
import android.app.Application
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import com.appguard.blocker.admin.DeviceAdminHelper
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.protection.AppAccessGuard
import com.appguard.blocker.service.PackageChangeReceiver
import com.appguard.blocker.service.UsageMonitorService
import com.appguard.blocker.ui.BlockedActivity
import com.appguard.blocker.ui.ProtectionChallengeActivity
import com.appguard.blocker.util.AuthSession
import com.google.android.material.color.DynamicColors

class AppGuardApplication : Application() {

    private val packageChangeReceiver = PackageChangeReceiver()
    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        // Material You — wallpaper palette on API 31+ (same idea as ReVanced dynamic color)
        DynamicColors.applyToActivitiesIfAvailable(this)
        AppAccessGuard.reset()

        runCatching {
            val prefs = PrefsRepository(this)
            val current = packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
            val previous = prefs.lastSeenVersionCode

            // Rescue from HOME-loop / over-aggressive self-protect builds
            if (previous > 0 && previous < 8 && current >= 8) {
                prefs.emergencyDisarm()
                prefs.challengeActive = false
                runCatching { DeviceAdminHelper.removeAdmin(this) }
                runCatching { UsageMonitorService.stop(this) }
            }
            prefs.lastSeenVersionCode = current
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) {
                // Challenge must NOT count as "guardian foreground" — that disabled
                // self-protect while PIN was under Settings and blocked re-intercept.
                if (activity is BlockedActivity || activity is ProtectionChallengeActivity) return
                AppAccessGuard.onGuardianActivityStarted()
                startedActivities++
            }

            override fun onActivityResumed(activity: Activity) {
                when (activity) {
                    is BlockedActivity -> Unit
                    is ProtectionChallengeActivity -> Unit
                    else -> AppAccessGuard.onGuardianUiOpened(activity)
                }
            }

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) {
                if (activity is BlockedActivity || activity is ProtectionChallengeActivity) return
                AppAccessGuard.onGuardianActivityStopped()
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) {
                    val prefs = PrefsRepository(this@AppGuardApplication)
                    if (prefs.hasPin() && prefs.setupCompleted) {
                        AuthSession.lock()
                    }
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })

        val filter = IntentFilter().apply {
            addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
            addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
            addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageChangeReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(packageChangeReceiver, filter)
        }

        val prefs = PrefsRepository(this)
        // Keep self-protect flag in sync with the single protection switch
        if (prefs.allowlistEnabled && !prefs.protectionArmed) {
            prefs.protectionArmed = true
        } else if (!prefs.allowlistEnabled && prefs.protectionArmed) {
            prefs.protectionArmed = false
        }
        if (prefs.allowlistEnabled) {
            runCatching { UsageMonitorService.start(this) }
        }
    }
}
