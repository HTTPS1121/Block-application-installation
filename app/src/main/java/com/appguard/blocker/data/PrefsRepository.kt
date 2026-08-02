package com.appguard.blocker.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

class PrefsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
        return MessageDigest.isEqual(expected, hashPin(pin, salt))
    }

    /** Returns plaintext recovery code once; stores only hash. */
    fun generateAndStoreRecoveryCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rng = SecureRandom()
        val code = CharArray(8) { alphabet[rng.nextInt(alphabet.length)] }.concatToString()
        val salt = ByteArray(16).also { rng.nextBytes(it) }
        val hash = hashPin(code, salt)
        prefs.edit()
            .putString(KEY_RECOVERY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_RECOVERY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putBoolean(KEY_RECOVERY_SHOWN, false)
            .apply()
        return code
    }

    fun hasRecoveryCode(): Boolean = prefs.contains(KEY_RECOVERY_HASH)

    var recoveryShown: Boolean
        get() = prefs.getBoolean(KEY_RECOVERY_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_RECOVERY_SHOWN, value).apply()

    fun verifyRecoveryCode(code: String): Boolean {
        val saltB64 = prefs.getString(KEY_RECOVERY_SALT, null) ?: return false
        val hashB64 = prefs.getString(KEY_RECOVERY_HASH, null) ?: return false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expected = Base64.decode(hashB64, Base64.NO_WRAP)
        return MessageDigest.isEqual(expected, hashPin(code.trim().uppercase(), salt))
    }

    var allowlistEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALLOWLIST_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ALLOWLIST_ENABLED, value).apply()

    var uninstallUnlocked: Boolean
        get() = prefs.getBoolean(KEY_UNINSTALL_UNLOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_UNINSTALL_UNLOCKED, value).apply()

    var protectionArmed: Boolean
        get() = prefs.getBoolean(KEY_PROTECTION_ARMED, false)
        set(value) = prefs.edit().putBoolean(KEY_PROTECTION_ARMED, value).apply()

    var challengeActive: Boolean
        get() = prefs.getBoolean(KEY_CHALLENGE_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_CHALLENGE_ACTIVE, value).apply()

    var pendingTamperReason: String?
        get() = prefs.getString(KEY_PENDING_TAMPER, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_PENDING_TAMPER) else putString(KEY_PENDING_TAMPER, value)
            }.apply()
        }

    fun getRemovalLease(): com.appguard.blocker.protection.RemovalLease? {
        val reasonName = prefs.getString(KEY_LEASE_REASON, null) ?: return null
        val reason = runCatching {
            com.appguard.blocker.protection.TamperReason.valueOf(reasonName)
        }.getOrNull() ?: return null
        val issued = prefs.getLong(KEY_LEASE_ISSUED, 0L)
        val expires = prefs.getLong(KEY_LEASE_EXPIRES, 0L)
        val nonce = prefs.getString(KEY_LEASE_NONCE, null) ?: return null
        if (issued <= 0L || expires <= 0L) return null
        return com.appguard.blocker.protection.RemovalLease(reason, issued, expires, nonce)
    }

    fun setRemovalLease(lease: com.appguard.blocker.protection.RemovalLease) {
        prefs.edit()
            .putString(KEY_LEASE_REASON, lease.reason.name)
            .putLong(KEY_LEASE_ISSUED, lease.issuedAtMs)
            .putLong(KEY_LEASE_EXPIRES, lease.expiresAtMs)
            .putString(KEY_LEASE_NONCE, lease.nonce)
            .putBoolean(KEY_UNINSTALL_UNLOCKED, true)
            .apply()
    }

    fun clearRemovalLease() {
        prefs.edit()
            .remove(KEY_LEASE_REASON)
            .remove(KEY_LEASE_ISSUED)
            .remove(KEY_LEASE_EXPIRES)
            .remove(KEY_LEASE_NONCE)
            .putBoolean(KEY_UNINSTALL_UNLOCKED, false)
            .apply()
    }

    fun hasValidRemovalLease(): Boolean = getRemovalLease()?.isValid() == true

    fun getAllowedPackages(): MutableSet<String> =
        prefs.getStringSet(KEY_ALLOWED_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()

    fun setAllowedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_ALLOWED_PACKAGES, packages.toSet()).apply()
    }

    fun allowPackage(packageName: String) {
        val set = getAllowedPackages()
        set.add(packageName)
        setAllowedPackages(set)
        clearRecentlyInstalled(packageName)
    }

    fun blockPackages(packages: Collection<String>) {
        val set = getAllowedPackages()
        set.removeAll(packages.toSet())
        setAllowedPackages(set)
    }

    fun isPackageAllowed(packageName: String): Boolean {
        if (isCoreExempt(packageName)) return true
        return getAllowedPackages().contains(packageName)
    }

    fun markRecentlyInstalled(packageName: String) {
        if (isCoreExempt(packageName) || isPackageInstaller(packageName)) return
        if (packageName == PLAY_STORE) return
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

    fun getRecentlyInstalled(): Set<String> =
        prefs.getStringSet(KEY_RECENT_INSTALLS, emptySet())?.toSet() ?: emptySet()

    fun isRecentlyInstalled(packageName: String): Boolean =
        getRecentlyInstalled().contains(packageName)

    /**
     * Single rule: when protection is on, only allowlisted (+ core exempt) may open.
     * No "pending new install" queue — a new app simply isn't on the list → blocked.
     */
    fun shouldBlockPackage(packageName: String): Boolean {
        if (packageName == appContext.packageName) return false
        if (isCoreExempt(packageName)) return false
        if (packageName == PLAY_STORE) return false
        if (!allowlistEnabled) return false
        return !isPackageAllowed(packageName)
    }

    fun protectionActive(): Boolean = allowlistEnabled

    /**
     * Self-protect (Admin / Accessibility / uninstall) follows the same switch as allowlist.
     * Only a valid RemovalLease (after correct PIN) pauses intercept.
     */
    fun selfProtectEnabled(): Boolean {
        if (!setupCompleted) return false
        // Single mode: protection on = allowlist on (also keep protectionArmed in sync)
        if (!allowlistEnabled && !protectionArmed) return false
        if (!allowlistEnabled) return false
        if (hasValidRemovalLease()) return false
        return true
    }

    /** Kill-switch used by version upgrade recovery / unlock flow. */
    fun emergencyDisarm() {
        prefs.edit()
            .putBoolean(KEY_ALLOWLIST_ENABLED, false)
            .putBoolean(KEY_PROTECTION_ARMED, false)
            .putBoolean(KEY_CHALLENGE_ACTIVE, false)
            .remove(KEY_PENDING_TAMPER)
            .remove(KEY_LEASE_REASON)
            .remove(KEY_LEASE_ISSUED)
            .remove(KEY_LEASE_EXPIRES)
            .remove(KEY_LEASE_NONCE)
            .putBoolean(KEY_UNINSTALL_UNLOCKED, false)
            .apply()
    }

    var lastSeenVersionCode: Int
        get() = prefs.getInt(KEY_LAST_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_VERSION, value).apply()

    fun listLaunchableUserApps(): List<Pair<String, String>> {
        val pm = appContext.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                val pkg = app.packageName
                if (pkg == appContext.packageName) return@filter false
                if (isCoreExempt(pkg)) return@filter false
                if (isPackageInstaller(pkg) || pkg == PLAY_STORE) return@filter false
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    pm.getLaunchIntentForPackage(pkg) != null
            }
            .map { it.packageName to pm.getApplicationLabel(it).toString() }
            .sortedBy { it.second.lowercase() }
    }

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
        private const val KEY_RECOVERY_SALT = "recovery_salt"
        private const val KEY_RECOVERY_HASH = "recovery_hash"
        private const val KEY_RECOVERY_SHOWN = "recovery_shown"
        private const val KEY_ALLOWLIST_ENABLED = "allowlist_enabled"
        private const val KEY_UNINSTALL_UNLOCKED = "uninstall_unlocked"
        private const val KEY_ALLOWED_PACKAGES = "allowed_packages"
        private const val KEY_RECENT_INSTALLS = "recent_installs"
        private const val KEY_SETUP_COMPLETED = "setup_completed"
        private const val KEY_PROTECTION_ARMED = "protection_armed"
        private const val KEY_LAST_VERSION = "last_seen_version_code"
        private const val KEY_CHALLENGE_ACTIVE = "challenge_active"
        private const val KEY_PENDING_TAMPER = "pending_tamper_reason"
        private const val KEY_LEASE_REASON = "removal_lease_reason"
        private const val KEY_LEASE_ISSUED = "removal_lease_issued"
        private const val KEY_LEASE_EXPIRES = "removal_lease_expires"
        private const val KEY_LEASE_NONCE = "removal_lease_nonce"

        const val PLAY_STORE = "com.android.vending"
        const val OUR_LABEL = "שומר אפליקציות"
        const val OUR_PACKAGE = "com.appguard.blocker"

        /** Minimal system exemptions — Settings/Launcher/SystemUI/IME + Pixel Settings search. */
        val CORE_EXEMPT = setOf(
            OUR_PACKAGE,
            "com.android.systemui",
            "com.android.settings",
            // Pixel Settings search UI (opening search launches this package)
            "com.google.android.settings.intelligence",
            "com.android.settings.intelligence",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin",
            "com.google.android.apps.accessibility.voiceaccess",
            // Google account / sign-in infrastructure (Settings → account → Manage)
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.gsf.login",
            "com.google.android.partnersetup",
            "com.google.android.googlequicksearchbox"
        )

        val PACKAGE_INSTALLERS = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.huawei.android.packageinstaller"
        )

        /** New-app install only (NOT update). */
        val NEW_INSTALL_KEYWORDS = listOf(
            "install", "התקן", "להתקין", "התקנה", "get", "התקן עכשיו",
            "install anyway", "התקן בכל זאת", "download", "הורד"
        )

        val UPDATE_KEYWORDS = listOf(
            "update", "עדכן", "updating", "מעדכן", "update all", "עדכון"
        )

        val SIDELOAD_KEYWORDS = listOf(
            "apk", ".apk", "package installer", "מתקין חבילות", "install unknown"
        )

        val UNINSTALL_KEYWORDS = listOf(
            "uninstall", "הסר", "הסרה", "הסר התקנה", "remove", "מחיקה", "delete app",
            "delete", "uninstall app"
        )

        val ADMIN_DISABLE_KEYWORDS = listOf(
            "deactivate", "בטל", "סיום הפעלה", "ביטול הפעלה",
            "device admin", "מנהל המכשיר", "device administrator",
            "turn off this admin", "ביטול מנהל", "deactivate this device admin app",
            "device admin apps", "אפליקציות של מנהל", "activate this device admin",
            "ok", "אישור" // paired with admin context in detector
        )

        val ACCESSIBILITY_DISABLE_KEYWORDS = listOf(
            "accessibility", "נגישות", "turn off", "כבה", "disable", "השבת", "use service",
            "השתמש בשירות", "shortcut"
        )

        fun isCoreExempt(packageName: String): Boolean {
            if (packageName in CORE_EXEMPT) return true
            if (packageName.contains("launcher", ignoreCase = true)) return true
            if (packageName.contains("inputmethod", ignoreCase = true)) return true
            if (packageName.contains("keyboard", ignoreCase = true)) return true
            if (packageName.endsWith(".permissioncontroller")) return true
            // Any OEM/Pixel Settings search / intelligence surface
            if (packageName.contains("settings.intelligence", ignoreCase = true)) return true
            // GMS / account flows (subpackages)
            if (packageName == "com.google.android.gms" ||
                packageName.startsWith("com.google.android.gms.")
            ) return true
            return false
        }

        fun isPackageInstaller(packageName: String): Boolean {
            if (packageName in PACKAGE_INSTALLERS) return true
            if (packageName.contains("packageinstaller", ignoreCase = true)) return true
            return false
        }

        fun isInstallerPackage(packageName: String): Boolean =
            packageName == PLAY_STORE || isPackageInstaller(packageName)

        val ALWAYS_ALLOWED: Set<String> get() = CORE_EXEMPT
        val INSTALLER_PACKAGES: Set<String> get() = PACKAGE_INSTALLERS + PLAY_STORE
        val INSTALL_BLOCK_KEYWORDS: List<String> get() = NEW_INSTALL_KEYWORDS
    }
}
