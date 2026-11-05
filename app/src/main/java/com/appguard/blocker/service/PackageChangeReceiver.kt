package com.appguard.blocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository

/**
 * Fresh installs are blocked unless the package id was approved before and
 * [PrefsRepository.rememberApprovalAfterReinstall] is on — then reopen without PIN.
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
                if (prefs.isAlwaysOpen(pkg)) return

                if (prefs.rememberApprovalAfterReinstall && prefs.isRememberedApproval(pkg)) {
                    prefs.allowPackage(pkg)
                    return
                }

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
                val wasAllowed = allowed.contains(pkg)
                if (allowed.remove(pkg)) {
                    prefs.setAllowedPackages(allowed)
                }
                prefs.clearRecentlyInstalled(pkg)
                val keep = prefs.rememberApprovalAfterReinstall &&
                    (wasAllowed || prefs.isRememberedApproval(pkg))
                if (keep) {
                    prefs.rememberApproval(pkg)
                } else {
                    prefs.removeRememberedApproval(pkg)
                }
            }
        }
    }
}
