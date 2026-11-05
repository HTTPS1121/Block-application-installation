package com.appguard.blocker.ui.compose

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.appguard.blocker.data.PrefsRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppCatalogEntry(
    val packageName: String,
    val label: String,
    val allowed: Boolean,
    val installed: Boolean = true,
)

/**
 * Live user-app catalog via [LauncherApps] — same API launchers use.
 * One [LauncherApps.getActivityList] at process start, then incremental
 * add/remove callbacks. Approvals live in prefs; uninstalled-but-remembered
 * rows are merged from there.
 */
object AppCatalog {

    private const val ICON_PX = 48

    private val installedLabels = ConcurrentHashMap<String, String>()
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()
    private val entriesFlow = MutableStateFlow<List<AppCatalogEntry>>(emptyList())
    private val iconRevisionFlow = MutableStateFlow(0)

    @Volatile private var appContext: Context? = null
    @Volatile private var prefsRef: PrefsRepository? = null
    @Volatile private var started = false
    @Volatile private var migrated = false

    private val worker = HandlerThread("app-catalog").apply { start() }
    private val workerHandler = Handler(worker.looper)

    val entries: StateFlow<List<AppCatalogEntry>> = entriesFlow
    val iconRevision: StateFlow<Int> = iconRevisionFlow

    fun attach(context: Context) {
        val app = context.applicationContext ?: context
        appContext = app
        if (prefsRef == null) prefsRef = PrefsRepository(app)
        publish()
        if (started) return
        started = true
        workerHandler.post { runCatching { loadAll(app) } }
        val launcher = app.getSystemService(LauncherApps::class.java) ?: return
        runCatching { launcher.registerCallback(callback, workerHandler) }
    }

    private fun current(prefs: PrefsRepository): List<AppCatalogEntry> {
        if (!migrated) {
            prefs.migrateAllowedPackagesToRemembered()
            migrated = true
        }
        val allowed = prefs.getAllowedPackages()
        val rememberOn = prefs.rememberApprovalAfterReinstall
        val remembered = if (rememberOn) prefs.getRememberedApprovals() else emptyList()
        val rememberedByPkg = remembered.associateBy { it.packageName }
        val installedPkgs = installedLabels.keys

        val installed = installedLabels.entries.map { (pkg, label) ->
            AppCatalogEntry(
                packageName = pkg,
                label = label,
                allowed = pkg in allowed || pkg in rememberedByPkg,
                installed = true,
            )
        }
        val rememberedOnly = remembered
            .filter { it.packageName !in installedPkgs }
            .map { mem ->
                AppCatalogEntry(
                    packageName = mem.packageName,
                    label = mem.label,
                    allowed = true,
                    installed = false,
                )
            }
        return (installed + rememberedOnly).sortedWith(catalogOrder)
    }

    fun peekIcon(packageName: String): ImageBitmap? = iconCache[packageName]

    fun saveAllowlist(
        context: Context,
        prefs: PrefsRepository,
        entries: List<AppCatalogEntry>,
        armAfterSave: Boolean,
    ) {
        val runtimeAllowed = mutableSetOf<String>()
        for (item in entries) {
            when {
                !item.installed && !item.allowed -> prefs.removeRememberedApproval(item.packageName)
                !item.installed -> {
                    if (prefs.rememberApprovalAfterReinstall) prefs.rememberApproval(item.packageName)
                }
                item.allowed -> {
                    runtimeAllowed.add(item.packageName)
                    if (prefs.rememberApprovalAfterReinstall) prefs.rememberApproval(item.packageName)
                }
                else -> prefs.removeRememberedApproval(item.packageName)
            }
        }
        prefs.setAllowedPackages(runtimeAllowed)
        prefs.getRecentlyInstalled().toList().forEach { prefs.clearRecentlyInstalled(it) }
        publish()
        if (armAfterSave) {
            com.appguard.blocker.protection.ProtectionController.arm(context)
        } else if (prefs.protectionArmed || prefs.allowlistEnabled) {
            prefs.allowlistEnabled = true
            prefs.protectionArmed =
                com.appguard.blocker.util.PermissionHelper.criticalReady(context)
            if (prefs.allowlistEnabled &&
                com.appguard.blocker.util.PermissionHelper.usageAccessGranted(context)
            ) {
                com.appguard.blocker.service.UsageMonitorService.start(context)
            }
        }
    }

    private val catalogOrder =
        compareBy<AppCatalogEntry> { !it.installed }
            .thenByDescending { it.allowed }
            .thenBy { it.label.lowercase() }

    private fun loadAll(app: Context) {
        val launcher = app.getSystemService(LauncherApps::class.java) ?: return
        val self = app.packageName
        val next = HashMap<String, String>()
        for (activity in launcher.getActivityList(null, Process.myUserHandle())) {
            val info = activity.applicationInfo
            val pkg = info.packageName
            if (pkg == self) continue
            if (pkg in next) continue
            if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            if (PrefsRepository.isCoreExempt(pkg) || PrefsRepository.isPackageInstaller(pkg)) continue
            if (pkg == PrefsRepository.PLAY_STORE) continue
            next[pkg] = activity.label?.toString()?.ifBlank { pkg } ?: pkg
        }
        installedLabels.clear()
        installedLabels.putAll(next)
        publish()
        val ctx = appContext ?: return
        for (pkg in next.keys) {
            if (iconCache.containsKey(pkg)) continue
            runCatching {
                rasterIcon(ctx.packageManager.getApplicationIcon(pkg)).also { iconCache[pkg] = it }
            }
        }
        iconRevisionFlow.value = iconRevisionFlow.value + 1
    }

    private fun upsert(pkg: String) {
        val app = appContext ?: return
        if (pkg == app.packageName) return
        if (PrefsRepository.isCoreExempt(pkg) || PrefsRepository.isPackageInstaller(pkg)) return
        if (pkg == PrefsRepository.PLAY_STORE) return
        val launcher = app.getSystemService(LauncherApps::class.java) ?: return
        val activity = launcher.getActivityList(pkg, Process.myUserHandle()).firstOrNull() ?: run {
            installedLabels.remove(pkg)
            iconCache.remove(pkg)
            publish()
            return
        }
        val info = activity.applicationInfo
        if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
            installedLabels.remove(pkg)
            publish()
            return
        }
        installedLabels[pkg] = activity.label?.toString()?.ifBlank { pkg } ?: pkg
        publish()
    }

    private fun remove(pkg: String) {
        if (installedLabels.remove(pkg) != null) {
            iconCache.remove(pkg)
            publish()
        }
    }

    private fun publish() {
        val prefs = prefsRef ?: return
        entriesFlow.value = current(prefs)
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            if (user != Process.myUserHandle()) return
            remove(packageName)
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) {
            if (user != Process.myUserHandle()) return
            upsert(packageName)
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            if (user != Process.myUserHandle()) return
            upsert(packageName)
        }

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            if (user != Process.myUserHandle()) return
            packageNames.forEach { upsert(it) }
        }

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            if (user != Process.myUserHandle()) return
            packageNames.forEach { remove(it) }
        }
    }

    private fun rasterIcon(drawable: Drawable): ImageBitmap =
        drawable.toBitmap(ICON_PX, ICON_PX).asImageBitmap()
}
