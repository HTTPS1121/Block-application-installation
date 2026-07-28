package com.appguard.blocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appguard.blocker.data.PrefsRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val prefs = PrefsRepository(context)
        if (prefs.allowlistEnabled || prefs.installBlockEnabled) {
            UsageMonitorService.start(context)
        }
    }
}
