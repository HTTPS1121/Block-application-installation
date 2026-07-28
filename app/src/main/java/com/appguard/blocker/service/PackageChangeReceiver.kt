package com.appguard.blocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.ui.BlockedActivity

/**
 * Detects newly installed packages when install-block is enabled.
 * Without Device Owner we cannot silently uninstall third-party apps,
 * so we warn and open the block screen. With Device Owner, DISALLOW_INSTALL_APPS
 * already prevents the install.
 */
class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_PACKAGE_ADDED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return

        val prefs = PrefsRepository(context)
        if (!prefs.installBlockEnabled) return

        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == context.packageName) return

        Toast.makeText(context, R.string.new_app_blocked_toast, Toast.LENGTH_LONG).show()
        val block = Intent(context, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(BlockedActivity.EXTRA_MODE, BlockedActivity.MODE_INSTALL)
            putExtra(BlockedActivity.EXTRA_PACKAGE, pkg)
        }
        context.startActivity(block)
    }
}
