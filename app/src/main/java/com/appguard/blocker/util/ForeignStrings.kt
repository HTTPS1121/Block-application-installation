package com.appguard.blocker.util

import android.content.Context
import android.content.pm.PackageManager

/** Resolve localized strings from another package (Settings), like Kaspersky. */
object ForeignStrings {

    fun settings(context: Context, name: String): String? =
        fromPackage(context, "com.android.settings", name)

    fun fromPackage(context: Context, packageName: String, resName: String): String? {
        return try {
            val pm = context.packageManager
            val res = pm.getResourcesForApplication(packageName)
            val id = res.getIdentifier(resName, "string", packageName)
            if (id == 0) null else res.getString(id)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Labels used to recognize App Info / Admin screens in the current locale. */
    fun appInfoMarkers(context: Context): List<String> {
        val out = mutableListOf<String>()
        listOf(
            "force_stop", "finish_application", "uninstall_text", "uninstall_application",
            "archive", "app_info_label", "storage_settings_for_app", "auto_launch_enable_text"
        ).forEach { key -> settings(context, key)?.let { out.add(it) } }
        // Pixel Hebrew SPA uses הסרה / סגירה ידנית (not Force stop / Uninstall)
        out.addAll(
            listOf(
                "Force stop", "Force Stop", "עצור בכוח", "סגירה ידנית",
                "Uninstall", "הסר", "הסרה", "הסר התקנה",
                "Archive", "העברה לארכיון",
                "App info", "פרטי האפליקציה",
                "Storage & cache", "אחסון ומטמון",
                "Open by default", "פתיחה כברירת מחדל"
            )
        )
        return out.distinct().filter { it.isNotBlank() }
    }

    /** Strong markers — enough alone with our app name (avoid Settings homepage false positives). */
    fun appInfoStrongMarkers(context: Context): List<String> {
        val out = mutableListOf<String>()
        listOf("force_stop", "finish_application", "uninstall_text", "uninstall_application")
            .forEach { key -> settings(context, key)?.let { out.add(it) } }
        out.addAll(
            listOf(
                "Force stop", "Force Stop", "עצור בכוח", "סגירה ידנית",
                "Uninstall", "הסר", "הסרה", "הסר התקנה",
                "Archive", "העברה לארכיון"
            )
        )
        return out.distinct().filter { it.isNotBlank() }
    }

    /** Buttons that must not work on OUR App Info (removal / kill service). */
    fun dangerousAppInfoActionLabels(context: Context): List<String> {
        val out = mutableListOf<String>()
        listOf(
            "force_stop", "finish_application", "uninstall_text", "uninstall_application",
            "archive", "disable_text", "app_disable_dlg_positive"
        ).forEach { key -> settings(context, key)?.let { out.add(it) } }
        out.addAll(
            listOf(
                "Uninstall", "הסר", "הסרה", "הסר התקנה", "delete app", "Delete", "מחק", "מחיקה",
                "Force stop", "Force Stop", "עצור בכוח", "סגירה ידנית",
                "Archive", "העברה לארכיון",
                "Disable", "השבת", "Disable app"
            )
        )
        return out.distinct().filter { it.isNotBlank() }
    }

    fun adminMarkers(context: Context): List<String> {
        val out = mutableListOf<String>()
        listOf(
            "device_admin_status", "device_admin_add_title", "remove_device_admin",
            "deactivate_device_admin", "active_device_admin_msg"
        ).forEach { key -> settings(context, key)?.let { out.add(it) } }
        out.addAll(
            listOf(
                "Deactivate", "Deactivate this device admin app",
                "Turn off this admin app", "Activate this device admin app",
                "בטל", "ביטול הפעלה", "סיום הפעלה", "הפעלת מנהל המכשיר",
                "Device admin", "מנהל המכשיר", "אפליקציות של מנהל המכשיר",
                "This admin app"
            )
        )
        return out.distinct().filter { it.isNotBlank() }
    }
}
