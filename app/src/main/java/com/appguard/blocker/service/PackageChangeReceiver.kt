package com.appguard.blocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository

/**
 * New installs are never auto-added to the allowlist.
 * If protection is on they simply can't open until marked allowed — no "pending" state.
 */
class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == context.packageName) return
        if (PrefsRepository.isCoreExempt(pkg) || PrefsRepository.isPackageInstaller(pkg)) return
        if (pkg == PrefsRepository.PLAY_STORE) return

        val prefs = PrefsRepository(context)
        PrefsRepository.clearSystemFlagCache()

        when (action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                // System packages are always open — no toast / no allowlist noise
                if (prefs.isAlwaysOpen(pkg)) return

                // Ensure new package is not on allowlist
                val allowed = prefs.getAllowedPackages()
                if (allowed.remove(pkg)) {
                    prefs.setAllowedPackages(allowed)
                }
                prefs.clearRecentlyInstalled(pkg)

                if (!prefs.allowlistEnabled) return
                Toast.makeText(context, R.string.new_app_blocked_toast, Toast.LENGTH_LONG).show()
                if (prefs.shouldBlockPackage(pkg)) {
                    BlockCoordinator.blockApp(context, pkg)
                }
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
