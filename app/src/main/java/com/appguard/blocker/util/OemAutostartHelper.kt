package com.appguard.blocker.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Best-effort OEM auto-start / background-run screens (Xiaomi, Huawei, Oppo, Vivo, Samsung…).
 * There is no public Android API — we try known vendor intents.
 */
object OemAutostartHelper {

    private data class Target(val packageName: String, val className: String)

    private val CANDIDATES = listOf(
        // Xiaomi / HyperOS / MIUI
        Target("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        Target("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
        // Huawei / Honor
        Target("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        Target("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
        Target("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        // Oppo / Realme / ColorOS
        Target("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        Target("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        Target("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        // Vivo
        Target("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        Target("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        // Samsung
        Target("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        Target("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        // OnePlus
        Target("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        // Asus
        Target("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
        // Letv
        Target("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
    )

    fun findAutostartIntent(context: Context): Intent? {
        val pm = context.packageManager
        for (target in CANDIDATES) {
            val intent = Intent().setComponent(ComponentName(target.packageName, target.className))
            if (pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        // Generic app details as last fallback for manual toggle
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.takeIf { Build.MANUFACTURER.lowercase() in OEM_HINT_MANUFACTURERS }
    }

    fun open(context: Context): Boolean {
        val intent = findAutostartIntent(context) ?: return false
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private val OEM_HINT_MANUFACTURERS = setOf(
        "xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme",
        "vivo", "iqoo", "oneplus", "samsung", "asus", "meizu", "letv", "tecno", "infinix"
    )
}
