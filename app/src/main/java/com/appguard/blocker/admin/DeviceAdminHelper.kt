package com.appguard.blocker.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.widget.Toast
import com.appguard.blocker.R

object DeviceAdminHelper {

    fun component(context: Context): ComponentName =
        ComponentName(context, AppDeviceAdminReceiver::class.java)

    fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isAdminActive(context: Context): Boolean =
        dpm(context).isAdminActive(component(context))

    fun isDeviceOwner(context: Context): Boolean =
        dpm(context).isDeviceOwnerApp(context.packageName)

    fun requestEnable(context: Context) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.device_admin_description)
            )
        }
        context.startActivity(intent)
    }

    fun removeAdmin(context: Context) {
        if (isAdminActive(context)) {
            dpm(context).removeActiveAdmin(component(context))
        }
    }

    fun applyInstallRestriction(context: Context, enabled: Boolean) {
        if (!isDeviceOwner(context)) return
        val manager = dpm(context)
        val admin = component(context)
        if (enabled) {
            manager.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
            manager.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            manager.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
        } else {
            manager.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
            manager.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            manager.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
        }
    }

    fun showCannotUninstallMessage(context: Context) {
        Toast.makeText(context, R.string.cannot_uninstall_message, Toast.LENGTH_LONG).show()
    }
}
