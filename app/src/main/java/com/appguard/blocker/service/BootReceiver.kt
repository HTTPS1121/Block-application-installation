package com.appguard.blocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.protection.ProtectionController
import com.appguard.blocker.service.UsageMonitorService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        // ElapsedRealtime resets on reboot — drop any stale uninstall lease
        ProtectionController.clearLease(context)
        val prefs = PrefsRepository(context)
        if (prefs.allowlistEnabled) {
            UsageMonitorService.start(context)
        }
    }
}
