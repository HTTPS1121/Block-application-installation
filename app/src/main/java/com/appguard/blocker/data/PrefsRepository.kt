package com.appguard.blocker.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Base64
import com.appguard.blocker.ui.compose.theme.AppThemeMode
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** Past approval kept by package id (com.example.app) — survives uninstall when enabled. */
data class RememberedApproval(
    val packageName: String,
    val label: String
)

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

    /** Optional fast login (1–4 digits). Off by default; full PIN always works. */
    var shortCodeLoginEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHORT_CODE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SHORT_CODE_ENABLED, value).apply()

    fun hasShortCode(): Boolean = prefs.contains(KEY_SHORT_CODE_HASH)

    fun setShortCode(code: String) {
        require(isValidShortCode(code)) { "short code must be 1–4 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(code, salt)
        prefs.edit()
            .putString(KEY_SHORT_CODE_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_SHORT_CODE_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun clearShortCode() {
        prefs.edit()
            .remove(KEY_SHORT_CODE_SALT)
            .remove(KEY_SHORT_CODE_HASH)
            .putBoolean(KEY_SHORT_CODE_ENABLED, false)
            .apply()
    }

    /** Dev/debug only — temporary «1» quick login; disable via clear short code in settings. */
    var devTempShortCodeOneActive: Boolean
        get() = prefs.getBoolean(KEY_DEV_TEMP_SHORT_ONE, true)
        set(value) = prefs.edit().putBoolean(KEY_DEV_TEMP_SHORT_ONE, value).apply()

    fun applyDevTempShortCodeOne() {
        setShortCode("1")
        shortCodeLoginEnabled = true
    }

    fun verifyShortCode(code: String): Boolean {
        if (!isValidShortCode(code)) return false
        val saltB64 = prefs.getString(KEY_SHORT_CODE_SALT, null) ?: return false
        val hashB64 = prefs.getString(KEY_SHORT_CODE_HASH, null) ?: return false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expected = Base64.decode(hashB64, Base64.NO_WRAP)
        return MessageDigest.isEqual(expected, hashPin(code, salt))
    }

    /** PinActivity login: full PIN, or short code when enabled. */
    fun verifyForLogin(input: String): Boolean {
        if (verifyPin(input)) return true
        if (!shortCodeLoginEnabled || !hasShortCode()) return false
        return verifyShortCode(input)
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

    /**
     * When true (default): uninstall keeps remembered approval by package name;
     * reinstall auto-allows without PIN. When false: uninstall clears memory — must approve again.
     */
    var rememberApprovalAfterReinstall: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER_AFTER_REINSTALL, true)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER_AFTER_REINSTALL, value).apply()

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
        val expiresElapsed = prefs.getLong(KEY_LEASE_EXPIRES_ELAPSED, 0L)
        val nonce = prefs.getString(KEY_LEASE_NONCE, null) ?: return null
        if (issued <= 0L || expires <= 0L) return null
        return com.appguard.blocker.protection.RemovalLease(
            reason, issued, expires, nonce, expiresElapsed
        )
    }

    fun setRemovalLease(lease: com.appguard.blocker.protection.RemovalLease) {
        prefs.edit()
            .putString(KEY_LEASE_REASON, lease.reason.name)
            .putLong(KEY_LEASE_ISSUED, lease.issuedAtMs)
            .putLong(KEY_LEASE_EXPIRES, lease.expiresAtMs)
            .putLong(KEY_LEASE_EXPIRES_ELAPSED, lease.expiresAtElapsedRealtime)
            .putString(KEY_LEASE_NONCE, lease.nonce)
            .putBoolean(KEY_UNINSTALL_UNLOCKED, true)
            .apply()
    }

    fun clearRemovalLease() {
        prefs.edit()
            .remove(KEY_LEASE_REASON)
            .remove(KEY_LEASE_ISSUED)
            .remove(KEY_LEASE_EXPIRES)
            .remove(KEY_LEASE_EXPIRES_ELAPSED)
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
        if (rememberApprovalAfterReinstall) {
            rememberApproval(packageName)
        }
    }

    fun blockPackages(packages: Collection<String>) {
        val set = getAllowedPackages()
        set.removeAll(packages.toSet())
        setAllowedPackages(set)
        packages.forEach { removeRememberedApproval(it) }
    }

    fun isPackageAllowed(packageName: String): Boolean {
        if (isAlwaysOpen(packageName)) return true
        return getAllowedPackages().contains(packageName)
    }

    /** Allowlist UI: runtime allow or remembered identity (when reinstall memory is on). */
    fun isAllowlistApproved(packageName: String): Boolean {
        if (isAlwaysOpen(packageName)) return true
        if (isPackageAllowed(packageName)) return true
        return rememberApprovalAfterReinstall && isRememberedApproval(packageName)
    }

    fun getRememberedApprovals(): List<RememberedApproval> {
        migrateLegacyTrustStorage()
        val raw = prefs.getStringSet(KEY_REMEMBERED_APPROVALS, emptySet()) ?: emptySet()
        return raw.mapNotNull { parseRememberedEntry(it) }
    }

    fun isRememberedApproval(packageName: String): Boolean =
        getRememberedApprovals().any { it.packageName == packageName }

    fun rememberApproval(packageName: String) {
        val label = runCatching {
            appContext.packageManager.getApplicationLabel(
                appContext.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrNull()
            ?: getRememberedApprovals().find { it.packageName == packageName }?.label
            ?: packageName
        addRememberedApproval(RememberedApproval(packageName, label))
    }

    fun addRememberedApproval(entry: RememberedApproval) {
        val set = prefs.getStringSet(KEY_REMEMBERED_APPROVALS, emptySet())?.toMutableSet()
            ?: mutableSetOf()
        set.removeAll { raw -> parseRememberedEntry(raw)?.packageName == entry.packageName }
        set.add(encodeRememberedEntry(entry))
        prefs.edit().putStringSet(KEY_REMEMBERED_APPROVALS, set).apply()
    }

    fun removeRememberedApproval(packageName: String) {
        val set = prefs.getStringSet(KEY_REMEMBERED_APPROVALS, emptySet())?.toMutableSet()
            ?: return
        val before = set.size
        set.removeAll { raw -> parseRememberedEntry(raw)?.packageName == packageName }
        if (set.size != before) {
            prefs.edit().putStringSet(KEY_REMEMBERED_APPROVALS, set).apply()
        }
    }

    fun getRememberedNotInstalled(): List<RememberedApproval> {
        val installed = runCatching {
            appContext.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { it.packageName }
                .toSet()
        }.getOrDefault(emptySet())
        return getRememberedApprovals()
            .filter { it.packageName !in installed }
            .sortedBy { it.label.lowercase() }
    }

    /** One-time: seed memory from current runtime allowlist. */
    fun migrateAllowedPackagesToRemembered() {
        if (!rememberApprovalAfterReinstall) return
        for (pkg in getAllowedPackages()) {
            if (isAlwaysOpen(pkg)) continue
            rememberApproval(pkg)
        }
    }

    fun countApprovedIdentities(): Int {
        val pkgs = getAllowedPackages().toMutableSet()
        pkgs.addAll(getRememberedApprovals().map { it.packageName })
        return pkgs.size
    }

    private fun migrateLegacyTrustStorage() {
        if (prefs.getBoolean(KEY_LEGACY_TRUST_MIGRATED, false)) return
        val legacy = prefs.getStringSet(KEY_TRUSTED_SIGNATURES, emptySet()) ?: emptySet()
        for (raw in legacy) {
            val parts = raw.split('|', limit = 3)
            if (parts.isEmpty()) continue
            val pkg = parts[0]
            if (pkg.isBlank() || isAlwaysOpen(pkg)) continue
            val label = parts.getOrElse(2) { pkg }.ifBlank { pkg }
            addRememberedApproval(RememberedApproval(pkg, label))
        }
        prefs.edit()
            .remove(KEY_TRUSTED_SIGNATURES)
            .putBoolean(KEY_LEGACY_TRUST_MIGRATED, true)
            .apply()
    }

    private fun encodeRememberedEntry(entry: RememberedApproval): String =
        "${entry.packageName}|${entry.label.replace('|', ' ')}"

    private fun parseRememberedEntry(raw: String): RememberedApproval? {
        val parts = raw.split('|', limit = 2)
        if (parts.isEmpty()) return null
        val pkg = parts[0]
        if (pkg.isBlank()) return null
        val label = parts.getOrElse(1) { pkg }.ifBlank { pkg }
        return RememberedApproval(pkg, label)
    }

    fun isPackageInstalled(packageName: String): Boolean =
        runCatching {
            appContext.packageManager.getApplicationInfo(packageName, 0)
            true
        }.getOrDefault(false)

    /**
     * Always open — no allowlist needed:
     * core exempt, Play Store, package installer, AND any OS system package
     * ([ApplicationInfo.FLAG_SYSTEM] / [ApplicationInfo.FLAG_UPDATED_SYSTEM_APP]).
     *
     * Trade-off: preinstalled Pixel/OEM apps (Chrome, Phone, …) cannot be blocked.
     * User/Play installs without system flag still require allowlist.
     */
    fun isAlwaysOpen(packageName: String): Boolean {
        if (packageName == appContext.packageName) return true
        if (isCoreExempt(packageName)) return true
        if (packageName == PLAY_STORE) return true
        if (isPackageInstaller(packageName)) return true
        return isOsSystemPackage(packageName)
    }

    fun isOsSystemPackage(packageName: String): Boolean {
        systemFlagCache[packageName]?.let { return it }
        val result = try {
            val ai = appContext.packageManager.getApplicationInfo(packageName, 0)
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (_: PackageManager.NameNotFoundException) {
            // Mid-uninstall / vanished process — never kick
            true
        } catch (_: Exception) {
            false
        }
        systemFlagCache[packageName] = result
        return result
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
     * When protection is on: block only non-system packages missing from allowlist.
     * OS system apps/processes are always open. Self-protect still gates OUR uninstall.
     */
    fun shouldBlockPackage(packageName: String): Boolean {
        if (isAlwaysOpen(packageName)) return false
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
            .remove(KEY_LEASE_EXPIRES_ELAPSED)
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
                if (isAlwaysOpen(pkg)) return@filter false
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    pm.getLaunchIntentForPackage(pkg) != null
            }
            .map { it.packageName to pm.getApplicationLabel(it).toString() }
            .sortedBy { it.second.lowercase() }
    }

    var appThemeMode: AppThemeMode
        get() = AppThemeMode.fromStorage(prefs.getString(KEY_THEME_MODE, null))
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.storageKey).commit()
        }

    var setupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETED, value).apply()

    var dnsLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_DNS_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_DNS_LOCK, value).apply()

    var dnsMode: String
        get() = prefs.getString(KEY_DNS_MODE, "opportunistic") ?: "opportunistic"
        set(value) = prefs.edit().putString(KEY_DNS_MODE, value).apply()

    var dnsHost: String
        get() = prefs.getString(KEY_DNS_HOST, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DNS_HOST, value).apply()

    fun hasDnsSnapshot(): Boolean = prefs.contains(KEY_DNS_PREV_MODE)

    fun dnsSnapshot(): Pair<String, String>? {
        if (!hasDnsSnapshot()) return null
        val mode = prefs.getString(KEY_DNS_PREV_MODE, "opportunistic") ?: "opportunistic"
        val host = prefs.getString(KEY_DNS_PREV_HOST, "") ?: ""
        return mode to host
    }

    fun setDnsSnapshot(mode: String, host: String) {
        prefs.edit()
            .putString(KEY_DNS_PREV_MODE, mode)
            .putString(KEY_DNS_PREV_HOST, host)
            .apply()
    }

    fun clearDnsSnapshot() {
        prefs.edit()
            .remove(KEY_DNS_PREV_MODE)
            .remove(KEY_DNS_PREV_HOST)
            .apply()
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return digest.digest()
    }

    private fun isValidShortCode(code: String): Boolean =
        code.length in MIN_SHORT_CODE_LENGTH..MAX_SHORT_CODE_LENGTH &&
            code.all { it.isDigit() }

    companion object {
        const val MIN_SHORT_CODE_LENGTH = 1
        const val MAX_SHORT_CODE_LENGTH = 4
        private const val PREFS_NAME = "app_guard_prefs"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_SHORT_CODE_ENABLED = "short_code_login_enabled"
        private const val KEY_SHORT_CODE_SALT = "short_code_salt"
        private const val KEY_SHORT_CODE_HASH = "short_code_hash"
        private const val KEY_DEV_TEMP_SHORT_ONE = "dev_temp_short_code_one"
        private const val KEY_RECOVERY_SALT = "recovery_salt"
        private const val KEY_RECOVERY_HASH = "recovery_hash"
        private const val KEY_RECOVERY_SHOWN = "recovery_shown"
        private const val KEY_ALLOWLIST_ENABLED = "allowlist_enabled"
        private const val KEY_UNINSTALL_UNLOCKED = "uninstall_unlocked"
        private const val KEY_ALLOWED_PACKAGES = "allowed_packages"
        private const val KEY_REMEMBERED_APPROVALS = "remembered_approvals_v1"
        private const val KEY_REMEMBER_AFTER_REINSTALL = "remember_approval_after_reinstall"
        private const val KEY_TRUSTED_SIGNATURES = "trusted_signatures_v1"
        private const val KEY_LEGACY_TRUST_MIGRATED = "legacy_trust_migrated"
        private const val KEY_RECENT_INSTALLS = "recent_installs"
        private const val KEY_SETUP_COMPLETED = "setup_completed"
        private const val KEY_THEME_MODE = "theme_mode_v1"
        private const val KEY_PROTECTION_ARMED = "protection_armed"
        private const val KEY_LAST_VERSION = "last_seen_version_code"
        private const val KEY_CHALLENGE_ACTIVE = "challenge_active"
        private const val KEY_PENDING_TAMPER = "pending_tamper_reason"
        private const val KEY_LEASE_REASON = "removal_lease_reason"
        private const val KEY_LEASE_ISSUED = "removal_lease_issued"
        private const val KEY_LEASE_EXPIRES = "removal_lease_expires"
        private const val KEY_LEASE_EXPIRES_ELAPSED = "removal_lease_expires_elapsed"
        private const val KEY_LEASE_NONCE = "removal_lease_nonce"
        private const val KEY_DNS_LOCK = "dns_lock_enabled"
        private const val KEY_DNS_MODE = "dns_mode"
        private const val KEY_DNS_HOST = "dns_host"
        private const val KEY_DNS_PREV_MODE = "dns_prev_mode"
        private const val KEY_DNS_PREV_HOST = "dns_prev_host"

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

        /** Invalidate after package install/remove so FLAG_SYSTEM cache stays honest. */
        fun clearSystemFlagCache() {
            systemFlagCache.clear()
        }

        private val systemFlagCache = ConcurrentHashMap<String, Boolean>()

        fun isInstallerPackage(packageName: String): Boolean =
            packageName == PLAY_STORE || isPackageInstaller(packageName)

        val ALWAYS_ALLOWED: Set<String> get() = CORE_EXEMPT
        val INSTALLER_PACKAGES: Set<String> get() = PACKAGE_INSTALLERS + PLAY_STORE
        val INSTALL_BLOCK_KEYWORDS: List<String> get() = NEW_INSTALL_KEYWORDS
    }
}
