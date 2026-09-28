package com.appguard.blocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.data.SignatureStatus
import com.appguard.blocker.data.SigningCerts

/**
 * Install decisions are stored by signing certificate.
 * A whitelist signature is allowed again after uninstall + reinstall.
 * Unknown signatures land in the pending list and stay closed.
 * Completing an install does not send the user home.
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
            Intent.ACTION_PACKAGE_ADDED -> onPackageAdded(context, prefs, intent, pkg)

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

    private fun onPackageAdded(
        context: Context,
        prefs: PrefsRepository,
        intent: Intent,
        pkg: String
    ) {
        if (prefs.isAlwaysOpen(pkg)) return

        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        val sig = SigningCerts.sha256(context, pkg)
        val label = SigningCerts.label(context, pkg)

        if (sig == null) {
            if (!replacing) {
                removeFromAllowlist(prefs, pkg)
                if (prefs.allowlistEnabled) {
                    Toast.makeText(context, R.string.new_app_blocked_toast, Toast.LENGTH_LONG).show()
                }
            }
            return
        }

        when (prefs.signatureStatus(sig)) {
            SignatureStatus.WHITE -> {
                prefs.putSignature(sig, pkg, label, SignatureStatus.WHITE)
                prefs.allowPackage(pkg)
            }
            SignatureStatus.REJECTED -> {
                prefs.putSignature(sig, pkg, label, SignatureStatus.REJECTED)
                removeFromAllowlist(prefs, pkg)
                if (prefs.allowlistEnabled && !replacing) {
                    Toast.makeText(context, R.string.signature_black_toast, Toast.LENGTH_LONG).show()
                }
            }
            SignatureStatus.PENDING -> {
                prefs.putSignature(sig, pkg, label, SignatureStatus.PENDING)
                removeFromAllowlist(prefs, pkg)
                if (prefs.allowlistEnabled && !replacing) {
                    Toast.makeText(context, R.string.new_app_blocked_toast, Toast.LENGTH_LONG).show()
                }
            }
            null -> {
                if (replacing && prefs.isPackageAllowed(pkg)) {
                    prefs.putSignature(sig, pkg, label, SignatureStatus.WHITE)
                    return
                }
                prefs.putSignature(sig, pkg, label, SignatureStatus.PENDING)
                removeFromAllowlist(prefs, pkg)
                if (prefs.allowlistEnabled && !replacing) {
                    Toast.makeText(context, R.string.new_app_blocked_toast, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun removeFromAllowlist(prefs: PrefsRepository, pkg: String) {
        val allowed = prefs.getAllowedPackages()
        if (allowed.remove(pkg)) {
            prefs.setAllowedPackages(allowed)
        }
        prefs.clearRecentlyInstalled(pkg)
    }
}
