package com.appguard.blocker.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

class PrefsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasPin(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val saltB64 = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val hashB64 = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expected = Base64.decode(hashB64, Base64.NO_WRAP)
        val actual = hashPin(pin, salt)
        return MessageDigest.isEqual(expected, actual)
    }

    var allowlistEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALLOWLIST_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ALLOWLIST_ENABLED, value).apply()

    var installBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_INSTALL_BLOCK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_INSTALL_BLOCK_ENABLED, value).apply()

    var uninstallUnlocked: Boolean
        get() = prefs.getBoolean(KEY_UNINSTALL_UNLOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_UNINSTALL_UNLOCKED, value).apply()

    fun getAllowedPackages(): MutableSet<String> =
        prefs.getStringSet(KEY_ALLOWED_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()

    fun setAllowedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_ALLOWED_PACKAGES, packages.toSet()).apply()
    }

    fun isPackageAllowed(packageName: String): Boolean {
        if (ALWAYS_ALLOWED.contains(packageName)) return true
        return getAllowedPackages().contains(packageName)
    }

    fun markRecentlyInstalled(packageName: String) {
        val set = prefs.getStringSet(KEY_RECENT_INSTALLS, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(packageName)
        prefs.edit().putStringSet(KEY_RECENT_INSTALLS, set).apply()
    }

    fun clearRecentlyInstalled(packageName: String) {
        val set = prefs.getStringSet(KEY_RECENT_INSTALLS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (set.remove(packageName)) {
            prefs.edit().putStringSet(KEY_RECENT_INSTALLS, set).apply()
        }
    }

    fun isRecentlyInstalled(packageName: String): Boolean =
        prefs.getStringSet(KEY_RECENT_INSTALLS, emptySet())?.contains(packageName) == true

    var setupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETED, value).apply()

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return digest.digest()
    }

    companion object {
        private const val PREFS_NAME = "app_guard_prefs"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_ALLOWLIST_ENABLED = "allowlist_enabled"
        private const val KEY_INSTALL_BLOCK_ENABLED = "install_block_enabled"
        private const val KEY_UNINSTALL_UNLOCKED = "uninstall_unlocked"
        private const val KEY_ALLOWED_PACKAGES = "allowed_packages"
        private const val KEY_RECENT_INSTALLS = "recent_installs"
        private const val KEY_SETUP_COMPLETED = "setup_completed"

        val ALWAYS_ALLOWED = setOf(
            "com.appguard.blocker",
            "com.android.settings",
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.android.vending" // Play Store itself navigable; install flow blocked separately
        )

        val INSTALLER_PACKAGES = setOf(
            "com.android.vending",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.huawei.android.packageinstaller"
        )

        val INSTALL_BLOCK_KEYWORDS = listOf(
            "install",
            "התקן",
            "להתקין",
            "התקנה",
            "Install",
            "Update",
            "עדכן"
        )
    }
}
