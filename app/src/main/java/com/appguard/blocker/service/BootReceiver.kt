package com.appguard.blocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Accessibility service is restarted by the system after boot when enabled.
        // No-op placeholder to keep RECEIVE_BOOT_COMPLETED declared for OEM reliability.
    }
}
