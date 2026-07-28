package com.appguard.blocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository

/**
 * Detects package install/remove in real time.
 * New apps are never auto-allowed; with allowlist/install-block they are blocked immediately.
 */
class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == context.packageName) return

        val prefs = PrefsRepository(context)

        when (action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return

                // Never auto-allow newly installed apps
                val allowed = prefs.getAllowedPackages()
                if (allowed.remove(pkg)) {
                    prefs.setAllowedPackages(allowed)
                }
                prefs.markRecentlyInstalled(pkg)

                val shouldReact = prefs.installBlockEnabled || prefs.allowlistEnabled
                if (!shouldReact) return

                Toast.makeText(context, R.string.new_app_blocked_toast, Toast.LENGTH_LONG).show()
                BlockCoordinator.blockInstall(context, pkg)
            }

            Intent.ACTION_PACKAGE_REMOVED -> {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                val allowed = prefs.getAllowedPackages()
                if (allowed.remove(pkg)) {
                    prefs.setAllowedPackages(allowed)
                }
                prefs.clearRecentlyInstalled(pkg)
            }
        }
    }
}
