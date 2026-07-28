package com.appguard.blocker

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.service.PackageChangeReceiver
import com.appguard.blocker.service.UsageMonitorService

class AppGuardApplication : Application() {

    private val packageChangeReceiver = PackageChangeReceiver()

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
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
        if (prefs.allowlistEnabled || prefs.installBlockEnabled) {
            runCatching { UsageMonitorService.start(this) }
        }
    }
}
