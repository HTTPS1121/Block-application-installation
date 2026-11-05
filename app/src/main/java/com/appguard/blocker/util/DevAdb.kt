package com.appguard.blocker.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

/** Local dev / debuggable APK only — adb automation from the PC (not in release). */
object DevAdb {
    const val EXTRA_ADB_PIN = "adb_pin"
    /** Skip PIN verify; opens session / challenge success. Requires DEBUGGABLE. */
    const val EXTRA_ADB_UNLOCK = "adb_unlock"

    fun isDevBuild(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    fun wantsUnlock(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_ADB_UNLOCK, false) == true

    fun pinFromIntent(intent: Intent?): String? =
        intent?.getStringExtra(EXTRA_ADB_PIN)?.trim()?.takeIf { it.length >= 4 }
}
