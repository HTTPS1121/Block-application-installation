package com.appguard.blocker.data

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/** SHA-256 of the certificates that sign the installed APK. Stable across uninstall/reinstall. */
object SigningCerts {

    fun sha256(context: Context, packageName: String): String? {
        val certs = certificates(context, packageName) ?: return null
        if (certs.isEmpty()) return null
        return certs.map { sha256(it) }.distinct().sorted().joinToString(",")
    }

    fun label(context: Context, packageName: String): String =
        runCatching {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        }.getOrDefault(packageName)

    fun installedPackagesWithSignature(context: Context, sha256: String): List<String> {
        val pm = context.packageManager
        return runCatching { pm.getInstalledApplications(PackageManager.GET_META_DATA) }
            .getOrDefault(emptyList())
            .map { it.packageName }
            .filter { sha256(context, it) == sha256 }
    }

    private fun certificates(context: Context, packageName: String): Array<out Signature>? {
        val pm = context.packageManager
        val info = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        } catch (_: Exception) {
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return null
            val current = signing.apkContentsSigners
            if (!current.isNullOrEmpty()) return current
            return signing.signingCertificateHistory
        }
        @Suppress("DEPRECATION")
        return info.signatures
    }

    private fun sha256(signature: Signature): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
