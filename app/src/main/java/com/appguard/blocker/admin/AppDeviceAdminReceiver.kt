package com.appguard.blocker.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.protection.ProtectionController
import com.appguard.blocker.protection.TamperReason

class AppDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val prefs = PrefsRepository(context)
        if (prefs.hasValidRemovalLease()) {
            return context.getString(R.string.unlock_uninstall_desc)
        }
        // Pull user into PIN challenge (Kaspersky-style) instead of silent allow
        Handler(Looper.getMainLooper()).post {
            ProtectionController.onTamperDetected(context, TamperReason.DISABLE_DEVICE_ADMIN)
        }
        return context.getString(R.string.challenge_admin_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        val prefs = PrefsRepository(context)
        if (!prefs.hasValidRemovalLease()) {
            Toast.makeText(context, R.string.cannot_uninstall_message, Toast.LENGTH_LONG).show()
        }
        // Do NOT auto-disable Level 1/2
    }

    override fun onEnabled(context: Context, intent: Intent) {
        PrefsRepository(context).clearRemovalLease()
        PrefsRepository(context).uninstallUnlocked = false
        Toast.makeText(context, R.string.device_admin_on, Toast.LENGTH_SHORT).show()
    }
}
