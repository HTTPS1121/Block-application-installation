package com.appguard.blocker.util

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.appguard.blocker.admin.AppDeviceAdminReceiver
import com.appguard.blocker.service.AppMonitorAccessibilityService

enum class PermissionItem {
    ACCESSIBILITY,
    USAGE_ACCESS,
    DEVICE_ADMIN,
    OVERLAY,
    BATTERY_OPTIMIZATION,
    NOTIFICATIONS,
    OEM_AUTOSTART
}

data class PermissionState(
    val item: PermissionItem,
    val granted: Boolean
)

object PermissionHelper {

    fun accessibilityEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val target = ComponentName(context, AppMonitorAccessibilityService::class.java).flattenToString()
        if (enabled.any { it.resolveInfo.serviceInfo.let { si ->
                ComponentName(si.packageName, si.name).flattenToString() == target
            } }) return true

        val secure = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return secure.split(':').any {
            it.equals(target, ignoreCase = true) ||
                it.equals(
                    "${context.packageName}/${AppMonitorAccessibilityService::class.java.name}",
                    ignoreCase = true
                )
        }
    }

    fun usageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun deviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(context, AppDeviceAdminReceiver::class.java))
    }

    fun overlayGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun batteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun notificationsGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun oemAutostartAvailable(context: Context): Boolean =
        OemAutostartHelper.findAutostartIntent(context) != null

    fun allStates(context: Context): List<PermissionState> = listOf(
        PermissionState(PermissionItem.ACCESSIBILITY, accessibilityEnabled(context)),
        PermissionState(PermissionItem.USAGE_ACCESS, usageAccessGranted(context)),
        PermissionState(PermissionItem.DEVICE_ADMIN, deviceAdminActive(context)),
        PermissionState(PermissionItem.OVERLAY, overlayGranted(context)),
        PermissionState(PermissionItem.BATTERY_OPTIMIZATION, batteryOptimizationIgnored(context)),
        PermissionState(PermissionItem.NOTIFICATIONS, notificationsGranted(context)),
        PermissionState(
            PermissionItem.OEM_AUTOSTART,
            // OEM has no reliable API — treat as optional/manual unless unavailable
            !oemAutostartAvailable(context)
        )
    )

    fun criticalReady(context: Context): Boolean =
        accessibilityEnabled(context) &&
            usageAccessGranted(context) &&
            deviceAdminActive(context) &&
            overlayGranted(context) &&
            batteryOptimizationIgnored(context)

    fun openAccessibility(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openUsageAccess(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openOverlay(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openBatteryOptimization(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
