package com.appguard.blocker.protection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.appguard.blocker.R
import com.appguard.blocker.admin.DeviceAdminHelper
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.util.A11yNodes
import com.appguard.blocker.util.ForeignStrings

/**
 * Kaspersky-faithful detectors. Launcher icon alone is NEVER a threat.
 * Pixel App Info = SpaActivity + Hebrew «הסרה» / «סגירה ידנית».
 */
object TamperDetectors {

    @Volatile
    private var appDetailsComponent: ComponentName? = null

    /** Last Settings activity class from WINDOW_STATE_CHANGED (CONTENT_CHANGED often has ComposeView). */
    @Volatile
    var lastSettingsActivityClass: String = ""
        private set

    fun warmUp(context: Context) {
        appDetailsComponent = resolveAppDetails(context)
    }

    fun noteWindowClass(packageName: String, className: String) {
        if (className.isBlank()) return
        if (isSettings(packageName) || isSettingsIntelligence(packageName)) {
            lastSettingsActivityClass = className
        }
    }

    fun resolveAppDetails(context: Context): ComponentName {
        val fallback = ComponentName(
            "com.android.settings",
            "com.android.settings.applications.InstalledAppDetailsTop"
        )
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            val ri = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            val ai = ri?.activityInfo
            if (ai != null && !ai.packageName.isNullOrBlank() && !ai.name.isNullOrBlank()) {
                ComponentName(ai.packageName, ai.name)
            } else fallback
        } catch (_: Exception) {
            fallback
        }.also { appDetailsComponent = it }
    }

    fun appLabel(context: Context): String = context.getString(R.string.app_name)

    fun isSettings(pkg: String): Boolean =
        pkg == "com.android.settings" || pkg.startsWith("com.android.settings")

    fun isSettingsIntelligence(pkg: String): Boolean =
        pkg.contains("settings.intelligence", ignoreCase = true)

    /**
     * Pixel/AOSP Settings search — must NEVER be treated as Admin/A11y/AppInfo threat.
     * Also: search often runs in com.google.android.settings.intelligence (exempt from allowlist).
     */
    fun isSettingsSearchUi(
        eventClass: String,
        root: AccessibilityNodeInfo?,
        eventPackage: String = ""
    ): Boolean {
        val pkg = eventPackage.lowercase().ifBlank {
            root?.packageName?.toString().orEmpty().lowercase()
        }
        if (isSettingsIntelligence(pkg)) return true

        val className = eventClass.lowercase().ifBlank {
            lastSettingsActivityClass.lowercase()
        }
        if (className.contains("search") ||
            className.contains("settingssearch") ||
            className.contains("searchactivity") ||
            className.contains("searchresult") ||
            className.contains("suggestion") && className.contains("settings") ||
            className.contains("manageapplications") ||
            className.contains("spaapplications")
        ) return true

        if (root == null) return false

        if (isAllAppsListChrome(root)) return true

        // Active search field IDs (not every toolbar that mentions "search")
        if (treeHasSearchFieldId(root)) return true

        // Focused EditText in Settings = user is typing in search
        if (isSettings(pkg) && hasFocusedEditable(root)) return true

        if (nodeWithText(root, "Search settings") ||
            nodeWithText(root, "חיפוש בהגדרות") ||
            nodeWithText(root, "חיפוש הגדרות") ||
            nodeWithText(root, "חפש בהגדרות") ||
            nodeWithText(root, "חיפוש הגדרה") ||
            nodeWithText(root, "Search apps") ||
            nodeWithText(root, "חיפוש אפליקציות") ||
            nodeWithText(root, "חפש אפליקציות") ||
            nodeWithText(root, "חיפוש באפליקציות")
        ) {
            // Homepage always shows the hint — only treat as search if EditText present / focused / results
            return hasEditable(root) || hasFocusedEditable(root) ||
                className.contains("search") || isSettingsIntelligence(pkg)
        }

        return try {
            val joined = collectJoined(root)
            (joined.contains("search settings") && hasEditable(root)) ||
                joined.contains("settingssearch") ||
                (joined.contains("search apps") && hasEditable(root))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Settings → Apps → All apps list (including in-page search while typing).
     * Our name as a filtered row must NOT trigger uninstall / App Info detectors.
     */
    fun isAllAppsListUi(
        eventClass: String,
        root: AccessibilityNodeInfo?,
        eventPackage: String = ""
    ): Boolean {
        if (root == null) return false
        val pkg = eventPackage.lowercase().ifBlank {
            root.packageName?.toString().orEmpty().lowercase()
        }
        if (!isSettings(pkg) && !isSettingsIntelligence(pkg)) return false

        val className = eventClass.lowercase().ifBlank {
            lastSettingsActivityClass.lowercase()
        }
        if (className.contains("manageapplications") ||
            className.contains("spaapplications") ||
            (className.contains("installedapp") &&
                !className.contains("appinfo") &&
                !className.contains("details"))
        ) return true

        if (isAllAppsListChrome(root)) return true

        // Title may hide while search is active; list rows lack App Info controls
        if (isSettings(pkg) &&
            (treeHasSearchFieldId(root) || hasFocusedEditable(root) || hasEditable(root)) &&
            !hasAppInfoPageMarkers(root)
        ) {
            return true
        }

        return false
    }

    private fun isAllAppsListChrome(root: AccessibilityNodeInfo): Boolean =
        nodeWithExactText(root, "כל האפליקציות") ||
            nodeWithExactText(root, "All apps") ||
            nodeWithExactText(root, "See all apps") ||
            nodeWithExactText(root, "Your apps") ||
            nodeWithExactText(root, "האפליקציות שלך")

    /** Force-stop / Uninstall / Archive buttons — absent on All-apps browser rows. */
    private fun hasAppInfoPageMarkers(root: AccessibilityNodeInfo): Boolean =
        listOf(
            "Force stop", "Force Stop", "עצור בכוח", "סגירה ידנית",
            "Uninstall", "הסרה", "הסר התקנה", "Archive", "העברה לארכיון",
            "App info", "פרטי האפליקציה"
        ).any { markerExactOrNode(root, it) }

    /**
     * Only OUR App Info. Exact title «שומר אפליקציות» + Uninstall/Force-stop controls.
     * Substring «name appears in All apps list» must NOT match.
     */
    fun isOurAppInfo(
        context: Context,
        eventPackage: String,
        eventClass: String,
        root: AccessibilityNodeInfo?
    ): Boolean {
        if (root == null) return false
        val pkg = eventPackage.lowercase()
        if (pkg.contains("launcher") || pkg == "com.android.systemui") return false
        if (!isSettings(pkg)) return false
        if (isSettingsSearchUi(eventClass, root, eventPackage)) return false
        if (isAllAppsListUi(eventClass, root, eventPackage)) return false

        // Subject of the page must be us (exact node text), not a list row among many apps
        if (!hasExactAppTitle(root, appLabel(context)) &&
            !hasExactAppTitle(root, PrefsRepository.OUR_LABEL)
        ) return false

        // Must look like App Info controls (not All-apps list that merely lists our name)
        val strong = ForeignStrings.appInfoStrongMarkers(context)
            .any { markerExactOrNode(root, it) }
        if (!strong) return false

        return true
    }

    fun isLauncher(pkg: String): Boolean =
        pkg.contains("launcher", ignoreCase = true)

    fun isOurUninstallUi(
        context: Context,
        eventPackage: String,
        eventClass: String,
        root: AccessibilityNodeInfo?
    ): Boolean {
        if (root == null) return false
        val pkg = eventPackage.lowercase()
        val rootPkg = root.packageName?.toString().orEmpty().lowercase()
        val className = eventClass.lowercase()
        if (className.contains("grantpermissions")) return false

        // After Cancel on uninstall, event may still say packageinstaller while
        // rootInActiveWindow is already Launcher — which ALWAYS contains our icon label.
        // Never treat that as "uninstalling us".
        if (isLauncher(rootPkg) || rootPkg == "com.android.systemui") {
            if (!(isLauncher(pkg) || pkg == "com.android.systemui")) return false
            if (!isUninstallConfirmDialog(className, root)) return false
            return isOurUninstallSubject(root, context)
        }

        // Package installer: root must still be installer + confirm chrome + we are the subject
        if (pkg.contains("packageinstaller") || PrefsRepository.isPackageInstaller(eventPackage)) {
            if (!isPackageInstallerPackage(rootPkg)) return false
            if (!isOurUninstallSubject(root, context)) return false
            return hasUninstallConfirm(root, className) ||
                className.contains("uninstall") ||
                nodeWithText(root, "להסיר את האפליקציה") ||
                nodeWithText(root, "Do you want to uninstall")
        }

        if (isSettings(pkg)) {
            if (!isSettings(rootPkg) && rootPkg.isNotBlank()) return false
            if (isSettingsSearchUi(eventClass, root, eventPackage)) return false
            if (isAllAppsListUi(eventClass, root, eventPackage)) return false
            if (!isOurUninstallSubject(root, context)) return false
            return hasUninstallConfirm(root, className)
        }
        return false
    }

    /** True only if the uninstall/dialog subject is us — not merely our name on the home grid. */
    private fun isOurUninstallSubject(root: AccessibilityNodeInfo, context: Context): Boolean =
        hasExactAppTitle(root, appLabel(context)) ||
            hasExactAppTitle(root, PrefsRepository.OUR_LABEL) ||
            nodeWithExactText(root, PrefsRepository.OUR_PACKAGE)

    fun isPackageInstallerPackage(pkg: String): Boolean {
        val p = pkg.lowercase()
        return PrefsRepository.isPackageInstaller(p) || p.contains("packageinstaller")
    }

    /** Real uninstall confirmation — not Pixel long-press shortcut bubble. */
    private fun isUninstallConfirmDialog(className: String, root: AccessibilityNodeInfo): Boolean {
        val cls = className.lowercase()
        if (cls.contains("dialog") || cls.contains("alert") || cls.contains("uninstall")) {
            return hasUninstallConfirm(root, className)
        }
        // Shortcut / DeepShortcut / PopupContainer — never
        if (cls.contains("shortcut") || cls.contains("popup") || cls.contains("bubble") ||
            cls.contains("arrow") || cls.contains("taskmenu")
        ) return false
        return false
    }

    private fun hasUninstallConfirm(root: AccessibilityNodeInfo, className: String): Boolean {
        val dialogish = className.contains("dialog") ||
            className.contains("alert") ||
            nodeWithExactText(root, "Uninstall") ||
            nodeWithExactText(root, "הסר התקנה") ||
            nodeWithExactText(root, "הסרה") ||
            nodeWithText(root, "להסיר את האפליקציה")
        if (!dialogish) return false
        return nodeWithExactText(root, "Uninstall") ||
            nodeWithExactText(root, "הסר") ||
            nodeWithExactText(root, "הסרה") ||
            nodeWithExactText(root, "OK") ||
            nodeWithExactText(root, "אישור") ||
            nodeWithText(root, "להסיר את האפליקציה")
    }

    /**
     * Kaspersky DeviceAdminSelfProtectionStrategy + Pixel Hebrew admin UI.
     */
    fun isOurAdminDeactivate(
        context: Context,
        eventPackage: String,
        eventClass: String,
        root: AccessibilityNodeInfo?
    ): Boolean {
        if (root == null) return false
        if (!DeviceAdminHelper.isAdminActive(context)) return false
        val pkg = eventPackage.lowercase()
        val className = effectiveClass(eventClass).lowercase()
        if (!isSettings(pkg) && !className.contains("deviceadmin")) return false
        if (isSettingsSearchUi(eventClass, root, eventPackage)) return false
        if (isAllAppsListUi(eventClass, root, eventPackage)) return false
        // Exact label — not «our name appears somewhere in Settings»
        if (!hasExactAppTitle(root, appLabel(context)) &&
            !hasExactAppTitle(root, PrefsRepository.OUR_LABEL)
        ) return false

        // Real admin detail / deactivate UI
        if (className.contains("deviceadminadd") ||
            className.contains("deviceadminsettings") ||
            (className.contains("deviceadmin") && !className.contains("search"))
        ) {
            return true
        }

        val joined = collectJoined(root)
        val deactivate =
            joined.contains("deactivate") ||
                joined.contains("ביטול הפעלה") ||
                joined.contains("סיום הפעלה") ||
                nodeWithText(root, "Deactivate this device admin app") ||
                ForeignStrings.settings(context, "device_admin_status")
                    ?.let { nodeWithText(root, it) } == true

        if (className.contains("alertdialog") || className.contains("dialog")) {
            return deactivate
        }
        return deactivate
    }

    /**
     * Kaspersky: settings + our accessibility **description** in the tree.
     */
    fun isOurAccessibilityPage(
        context: Context,
        eventPackage: String,
        eventClass: String,
        root: AccessibilityNodeInfo?
    ): Boolean {
        if (root == null) return false
        val pkg = eventPackage.lowercase()
        if (!isSettings(pkg)) return false
        if (isSettingsSearchUi(eventClass, root, eventPackage)) return false
        if (isAllAppsListUi(eventClass, root, eventPackage)) return false

        val descr = context.getString(R.string.accessibility_service_description)
        if (nodeWithText(root, descr)) return true

        if (!treeHasOurIdentity(context, root)) return false
        val className = effectiveClass(eventClass).lowercase()
        if (className.contains("accessibilityservice") ||
            className.contains("togglefeatures") ||
            className.contains("volume")
        ) {
            return nodeWithText(root, "Use service") ||
                nodeWithText(root, "השתמש בשירות") ||
                nodeWithText(root, "Shortcut") ||
                nodeWithText(root, "קיצור דרך")
        }
        return nodeWithText(root, "Use service") ||
            nodeWithText(root, "השתמש בשירות")
    }

    private fun effectiveClass(eventClass: String): String {
        val c = eventClass.trim()
        // Compose / fragment CONTENT_CHANGED — fall back to last activity
        if (c.isEmpty() ||
            c.contains("ComposeView", ignoreCase = true) ||
            c.contains("android.view.View") ||
            c.contains("android.widget")
        ) {
            return lastSettingsActivityClass.ifBlank { c }
        }
        return c
    }

    fun treeHasOurIdentity(context: Context, root: AccessibilityNodeInfo): Boolean {
        val label = appLabel(context)
        // Exact match only — findByText is substring and hits «All apps» lists
        if (hasExactAppTitle(root, label)) return true
        if (hasExactAppTitle(root, PrefsRepository.OUR_LABEL)) return true
        if (nodeWithExactText(root, PrefsRepository.OUR_PACKAGE)) return true
        return false
    }

    /** True if some node text/contentDescription equals [title] (trim), not merely contains it. */
    fun hasExactAppTitle(root: AccessibilityNodeInfo, title: String?): Boolean {
        if (title.isNullOrBlank()) return false
        val want = normalizeLabel(title)
        return try {
            fun walk(n: AccessibilityNodeInfo, depth: Int): Boolean {
                if (depth > 14) return false
                val texts = listOfNotNull(
                    n.text?.toString(),
                    n.contentDescription?.toString()
                )
                if (texts.any { normalizeLabel(it) == want }) return true
                val c = n.childCount.coerceAtMost(40)
                for (i in 0 until c) {
                    val child = n.getChild(i) ?: continue
                    try {
                        if (walk(child, depth + 1)) return true
                    } finally {
                        A11yNodes.recycleQuietly(child)
                    }
                }
                return false
            }
            walk(root, 0)
        } catch (_: Exception) {
            false
        }
    }

    fun nodeWithExactText(root: AccessibilityNodeInfo, text: String?): Boolean =
        hasExactAppTitle(root, text)

    private fun normalizeLabel(s: String): String =
        s.replace('\u00A0', ' ').trim().lowercase()

    fun nodeWithText(root: AccessibilityNodeInfo, text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        // Prefer exact; fall back to findByText only for longer markers (≥4) to avoid «הסר» noise
        if (text.length >= 4 && nodeWithExactText(root, text)) return true
        return try {
            val list = root.findAccessibilityNodeInfosByText(text)
            try {
                list?.isNotEmpty() == true
            } finally {
                A11yNodes.recycleAll(list)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun markerExactOrNode(root: AccessibilityNodeInfo, marker: String): Boolean {
        if (marker.isBlank()) return false
        if (nodeWithExactText(root, marker)) return true
        // Short markers («הסר») — exact only; longer — substring OK
        if (marker.length < 4) return false
        return try {
            val list = root.findAccessibilityNodeInfosByText(marker)
            try {
                list?.isNotEmpty() == true
            } finally {
                A11yNodes.recycleAll(list)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun hasFocusedEditable(root: AccessibilityNodeInfo): Boolean {
        return try {
            fun walk(n: AccessibilityNodeInfo, depth: Int): Boolean {
                if (depth > 12) return false
                val cls = n.className?.toString().orEmpty().lowercase()
                if (n.isFocused && (n.isEditable || cls.contains("edittext") || cls.contains("search"))) {
                    return true
                }
                val c = n.childCount.coerceAtMost(40)
                for (i in 0 until c) {
                    val child = n.getChild(i) ?: continue
                    try {
                        if (walk(child, depth + 1)) return true
                    } finally {
                        A11yNodes.recycleQuietly(child)
                    }
                }
                return false
            }
            walk(root, 0)
        } catch (_: Exception) {
            false
        }
    }

    private fun treeHasSearchFieldId(root: AccessibilityNodeInfo): Boolean {
        val needles = listOf(
            "search_src_text", "search_src", "open_search_view",
            "search_action_bar_title", "search_action_bar", "search_view", "animated_hint",
            ":id/search_bar", "settings_search", "apps_search", "search_list"
        )
        return try {
            fun walk(node: AccessibilityNodeInfo, depth: Int): Boolean {
                if (depth > 12) return false
                val id = node.viewIdResourceName?.lowercase().orEmpty()
                if (needles.any { id.contains(it) }) return true
                val c = node.childCount.coerceAtMost(40)
                for (i in 0 until c) {
                    val child = node.getChild(i) ?: continue
                    try {
                        if (walk(child, depth + 1)) return true
                    } finally {
                        A11yNodes.recycleQuietly(child)
                    }
                }
                return false
            }
            walk(root, 0)
        } catch (_: Exception) {
            false
        }
    }

    private fun hasEditable(root: AccessibilityNodeInfo): Boolean {
        return try {
            fun walk(n: AccessibilityNodeInfo, depth: Int): Boolean {
                if (depth > 12) return false
                val cls = n.className?.toString().orEmpty().lowercase()
                if (n.isEditable || cls.contains("edittext")) return true
                val c = n.childCount.coerceAtMost(40)
                for (i in 0 until c) {
                    val child = n.getChild(i) ?: continue
                    try {
                        if (walk(child, depth + 1)) return true
                    } finally {
                        A11yNodes.recycleQuietly(child)
                    }
                }
                return false
            }
            walk(root, 0)
        } catch (_: Exception) {
            false
        }
    }

    private fun collectJoined(root: AccessibilityNodeInfo): String {
        val out = mutableListOf<String>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 10 || out.size > 60) return
            n.text?.let { out.add(it.toString()) }
            n.contentDescription?.let { out.add(it.toString()) }
            val c = n.childCount.coerceAtMost(30)
            for (i in 0 until c) {
                val child = n.getChild(i) ?: continue
                try {
                    walk(child, depth + 1)
                } finally {
                    A11yNodes.recycleQuietly(child)
                }
            }
        }
        walk(root, 0)
        return out.joinToString(" ").lowercase()
    }
}
