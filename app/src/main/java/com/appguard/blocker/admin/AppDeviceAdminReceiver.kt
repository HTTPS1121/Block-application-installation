package com.appguard.blocker.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository

class AppDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val prefs = PrefsRepository(context)
        return if (prefs.uninstallUnlocked) {
            context.getString(R.string.unlock_uninstall_desc)
        } else {
            context.getString(R.string.cannot_uninstall_message)
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        PrefsRepository(context).uninstallUnlocked = true
        Toast.makeText(context, R.string.uninstall_unlocked, Toast.LENGTH_LONG).show()
    }

    override fun onEnabled(context: Context, intent: Intent) {
        PrefsRepository(context).uninstallUnlocked = false
        Toast.makeText(context, R.string.device_admin_on, Toast.LENGTH_SHORT).show()
    }
}
