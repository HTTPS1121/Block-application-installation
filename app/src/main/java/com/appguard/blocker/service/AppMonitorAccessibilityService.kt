package com.appguard.blocker.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.protection.AppAccessGuard
import com.appguard.blocker.protection.ProtectionController
import com.appguard.blocker.protection.TamperDetectors
import com.appguard.blocker.protection.TamperReason
import com.appguard.blocker.ui.BlockedActivity
import com.appguard.blocker.util.A11yNodes
import com.appguard.blocker.util.ForeignStrings

/**
 * Self-protect like Kaspersky:
 * Admin / Accessibility pages in Settings → repeated BACK + PIN (never HOME spam on launcher).
 */
class AppMonitorAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PrefsRepository
    private val handler = Handler(Looper.getMainLooper())
    private var lastAppBlockAt = 0L
    private var lastAppBlockPkg: String? = null
    private var lastTamperAt = 0L
    private var lastTamperReason: TamperReason? = null
    private var serviceConnectedAt = 0L
    private var uninstallChainRunning = false
    private var settingsWatchUntil = 0L

    /** Settings: keep BACKing while still on Admin / A11y / our App Info (Kaspersky pattern). */
    private val settingsThreatWatch = object : Runnable {
        override fun run() {
            try {
                if (System.currentTimeMillis() > settingsWatchUntil) return
                if (!AppAccessGuard.mayRunSelfProtect(this@AppMonitorAccessibilityService)) {
                    handler.postDelayed(this, 450)
                    return
                }
                // While PIN challenge UI is up — never BACK into it
                if (AppAccessGuard.isChallengeUiShowing() ||
                    PrefsRepository(this@AppMonitorAccessibilityService).challengeActive
                ) {
                    handler.postDelayed(this, 450)
                    return
                }
                val root = rootInActiveWindow ?: run {
                    handler.postDelayed(this, 450)
                    return
                }
                val pkg = root.packageName?.toString().orEmpty()
                // PIN / our UI / keyboard on top — don't BACK
                if (AppAccessGuard.isOurPackage(this@AppMonitorAccessibilityService, pkg) ||
                    pkg.contains("inputmethod", ignoreCase = true) ||
                    pkg.contains("keyboard", ignoreCase = true)
                ) {
                    handler.postDelayed(this, 450)
                    return
                }
                if (!TamperDetectors.isSettings(pkg)) {
                    handler.postDelayed(this, 450)
                    return
                }
                if (TamperDetectors.isSettingsSearchUi("", root, pkg)) {
                    handler.postDelayed(this, 450)
                    return
                }
                // App Info itself is allowed — only Admin / A11y pages get the BACK watch
                val reason = when {
                    TamperDetectors.isOurAdminDeactivate(
                        this@AppMonitorAccessibilityService, pkg, "", root
                    ) -> TamperReason.DISABLE_DEVICE_ADMIN
                    TamperDetectors.isOurAccessibilityPage(
                        this@AppMonitorAccessibilityService, pkg, "", root
                    ) -> TamperReason.DISABLE_ACCESSIBILITY
                    else -> {
                        handler.postDelayed(this, 450)
                        return
                    }
                }
                clickCancelLikeButtons()
                performGlobalAction(GLOBAL_ACTION_BACK)
                ProtectionController.onTamperDetected(this@AppMonitorAccessibilityService, reason)
                handler.postDelayed(this, 450)
            } catch (_: Exception) {
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PrefsRepository(this)
        instance = this
        serviceConnectedAt = SystemClock.uptimeMillis()
        TamperDetectors.warmUp(this)
        if (prefs.protectionActive()) {
            UsageMonitorService.start(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            handleEvent(event)
        } catch (_: Exception) {
        }
    }

    private fun handleEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!::prefs.isInitialized) prefs = PrefsRepository(this)

        // Ignore ~1s after a11y connects (Kaspersky)
        if (SystemClock.uptimeMillis() - serviceConnectedAt < 1_000L) return

        val packageName = event.packageName?.toString() ?: return
        if (AppAccessGuard.isOurPackage(this, packageName)) {
            AppAccessGuard.markGuardianUiActive()
            return
        }

        val type = event.eventType
        val classNameEarly = event.className?.toString().orEmpty()
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            TamperDetectors.noteWindowClass(packageName, classNameEarly)
        }

        val selfProtect = AppAccessGuard.mayRunSelfProtect(this)

        // Launcher long-press / shortcut menus: never self-protect (icon labels ≠ uninstall)
        if (TamperDetectors.isLauncher(packageName) || packageName == "com.android.systemui") {
            if (type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
                type == AccessibilityEvent.TYPE_VIEW_CLICKED
            ) {
                // fall through only to allowlist logic below — no tamper
            } else if (selfProtect &&
                (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    type == AccessibilityEvent.TYPE_WINDOWS_CHANGED)
            ) {
                val root = rootInActiveWindow
                val className = event.className?.toString().orEmpty()
                if (TamperDetectors.isOurUninstallUi(this, packageName, className, root)) {
                    hardKickTamper(TamperReason.UNINSTALL)
                    return
                }
            }
            // Continue to allowlist check (launcher is exempt anyway)
            if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
            ) return
            // skip self-protect detectThreat for launcher content noise
            if (!prefs.allowlistEnabled) return
            if (!BlockCoordinator.shouldBlockApp(this, packageName)) return
            // launcher shouldn't be blocked
            return
        }

        if (selfProtect && (
                type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                    type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
                )
        ) {
            // Cancel / No on uninstall dialog of ANY app — never PIN
            if (isNegativeOrDismissClick(event)) return

            val root = rootInActiveWindow
            val className = event.className?.toString().orEmpty()
            // Dangerous controls on OUR App Info: הסרה / סגירה ידנית / ארכיון / השבת
            if (isOurDangerousAppInfoAction(event, packageName, root) ||
                TamperDetectors.isOurUninstallUi(this, packageName, className, root)
            ) {
                eatDangerousAppInfoAction()
                return
            }
            if (isAdminDeactivateClick(event) ||
                TamperDetectors.isOurAdminDeactivate(this, packageName, className, root)
            ) {
                kickSettingsThreat(TamperReason.DISABLE_DEVICE_ADMIN)
                return
            }
            if (TamperDetectors.isOurAccessibilityPage(this, packageName, className, root)) {
                kickSettingsThreat(TamperReason.DISABLE_ACCESSIBILITY)
                return
            }
        }

        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        if (selfProtect) {
            // Confirm dialogs after tapping Force stop / Uninstall on our App Info
            if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
            ) {
                val root = rootInActiveWindow
                // Dismiss race: installer event + launcher root must not PIN
                if (isStaleInstallerEvent(packageName, root)) {
                    // fall through — not our uninstall
                } else if (isOurDangerousConfirmDialog(packageName, root)) {
                    eatDangerousAppInfoAction()
                    return
                }
            }
            val threat = detectThreat(event, packageName)
            if (threat != null) {
                when (threat) {
                    TamperReason.DISABLE_DEVICE_ADMIN,
                    TamperReason.DISABLE_ACCESSIBILITY,
                    TamperReason.UNINSTALL -> {
                        if (TamperDetectors.isSettings(packageName) ||
                            threat != TamperReason.UNINSTALL
                        ) {
                            kickSettingsThreat(threat)
                        } else {
                            hardKickTamper(threat)
                        }
                    }
                    else -> hardKickTamper(threat)
                }
                return
            }
        }

        // Single mode: not on allowlist → kick. Never while guardian UI is opening/open.
        if (!prefs.allowlistEnabled) return
        if (AppAccessGuard.mustNotKickGuardian()) return
        // Package installer is exempt from allowlist kicks (uninstall other apps must work).
        // OUR uninstall is handled above by self-protect / isOurUninstallUi.
        if (!BlockCoordinator.shouldBlockApp(this, packageName)) return

        val now = System.currentTimeMillis()
        if (packageName == lastAppBlockPkg && now - lastAppBlockAt < 250) return
        lastAppBlockPkg = packageName
        lastAppBlockAt = now

        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
        BlockCoordinator.blockApp(this, packageName)
    }

    /** Kaspersky Admin/A11y: BACK immediately + PIN; keep BACKing while page stays open. */
    private fun kickSettingsThreat(reason: TamperReason) {
        clickCancelLikeButtons()
        performGlobalAction(GLOBAL_ACTION_BACK)
        BlockCoordinator.showPleaseWait(this)
        ProtectionController.onTamperDetected(this, reason)
        settingsWatchUntil = System.currentTimeMillis() + 12_000
        handler.removeCallbacks(settingsThreatWatch)
        handler.postDelayed(settingsThreatWatch, 400)
        handler.postDelayed({ BlockCoordinator.dismissOverlay(this) }, 900)
    }

    /** App Info / Uninstall: BACK (+ HOME only for packageinstaller). */
    private fun hardKickTamper(reason: TamperReason) {
        if (!debounceLaunch(reason)) return
        clickCancelLikeButtons()
        BlockCoordinator.showPleaseWait(this)

        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString().orEmpty()
        val isInstaller = pkg.contains("packageinstaller", ignoreCase = true) ||
            PrefsRepository.isPackageInstaller(pkg)
        performGlobalAction(GLOBAL_ACTION_BACK)
        if (reason == TamperReason.UNINSTALL && isInstaller && !uninstallChainRunning) {
            uninstallChainRunning = true
            performGlobalAction(GLOBAL_ACTION_HOME)
            handler.postDelayed({
                ProtectionController.onTamperDetected(this, reason)
                uninstallChainRunning = false
                BlockCoordinator.dismissOverlay(this)
            }, 350)
            return
        }
        ProtectionController.onTamperDetected(this, reason)
        handler.postDelayed({ BlockCoordinator.dismissOverlay(this) }, 800)
    }

    private fun debounceLaunch(reason: TamperReason): Boolean {
        val now = System.currentTimeMillis()
        if (reason == lastTamperReason && now - lastTamperAt < 1_200) return false
        lastTamperAt = now
        lastTamperReason = reason
        return true
    }

    private fun detectThreat(event: AccessibilityEvent, packageName: String): TamperReason? {
        if (TamperDetectors.isLauncher(packageName) || packageName == "com.android.systemui") {
            return null // handled separately; never via content/long-click noise
        }

        val root = rootInActiveWindow
        val className = event.className?.toString().orEmpty()
        if (TamperDetectors.isSettingsIntelligence(packageName)) {
            return null
        }
        // Google account / GMS surfaces — not tamper targets
        if (isGoogleAccountSurface(packageName)) {
            return null
        }
        if (TamperDetectors.isSettings(packageName) &&
            (TamperDetectors.isSettingsSearchUi(className, root, packageName) ||
                TamperDetectors.isAllAppsListUi(className, root, packageName))
        ) {
            return null
        }

        // Uninstall confirm / packageinstaller only — App Info viewing is allowed
        if (TamperDetectors.isOurUninstallUi(this, packageName, className, root)) {
            return TamperReason.UNINSTALL
        }
        if (TamperDetectors.isOurAdminDeactivate(this, packageName, className, root)) {
            return TamperReason.DISABLE_DEVICE_ADMIN
        }
        if (TamperDetectors.isOurAccessibilityPage(this, packageName, className, root)) {
            return TamperReason.DISABLE_ACCESSIBILITY
        }
        return null
    }

    private fun isGoogleAccountSurface(packageName: String): Boolean {
        val p = packageName.lowercase()
        return p == "com.google.android.gms" ||
            p.startsWith("com.google.android.gms.") ||
            p == "com.google.android.gsf" ||
            p == "com.google.android.gsf.login" ||
            p.contains("googlequicksearchbox") ||
            p == "com.google.android.contacts" ||
            p.contains("chrometabalactivity") // custom tabs host varies
    }

    private fun treeContainsOurLabel(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return TamperDetectors.treeHasOurIdentity(this, root)
    }

    private fun eventMentionsUs(event: AccessibilityEvent): Boolean {
        val texts = collectEventTexts(event, rootInActiveWindow)
        return mentionsOurApp(texts, texts.joinToString(" ").lowercase())
    }

    private fun isOurAccessibilityDisableScreen(
        joined: String,
        className: String,
        root: AccessibilityNodeInfo?
    ): Boolean {
        val a11y = className.contains("accessibility") ||
            joined.contains("accessibility") ||
            joined.contains("נגישות")
        if (!a11y) return false

        // Kaspersky: match OUR accessibility description text in tree
        val descr = getString(R.string.accessibility_service_description)
        val hasDescr = try {
            val list = root?.findAccessibilityNodeInfosByText(descr)
            try {
                list?.isNotEmpty() == true ||
                    joined.contains(descr.lowercase().take(24))
            } finally {
                A11yNodes.recycleAll(list)
            }
        } catch (_: Exception) {
            false
        }
        val ourService = hasDescr ||
            joined.contains(PrefsRepository.OUR_PACKAGE.lowercase()) ||
            joined.contains(PrefsRepository.OUR_LABEL.lowercase()) ||
            treeContainsOurLabel(root)
        if (!ourService) return false

        return hasClickableByLabels(
            root ?: return true,
            listOf(
                "turn off", "כבה", "disable", "השבת",
                "use service", "השתמש בשירות", "off", "on"
            ),
            0
        ) || hasSwitch(root) ||
            joined.contains("use service") ||
            joined.contains("השתמש בשירות")
    }

    private fun hasSwitch(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val cls = node.className?.toString().orEmpty().lowercase()
        if (cls.contains("switch") || cls.contains("toggle") || cls.contains("checkbox")) {
            return true
        }
        val count = node.childCount.coerceAtMost(40)
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            try {
                if (hasSwitch(child)) return true
            } finally {
                A11yNodes.recycleQuietly(child)
            }
        }
        return false
    }

    private fun isAdminDeactivateClick(event: AccessibilityEvent): Boolean {
        val parts = mutableListOf<String>()
        event.text?.forEach { parts.add(it.toString()) }
        event.contentDescription?.let { parts.add(it.toString()) }
        runCatching {
            event.source?.let { src ->
                try {
                    src.text?.let { parts.add(it.toString()) }
                    src.contentDescription?.let { parts.add(it.toString()) }
                } finally {
                    A11yNodes.recycleQuietly(src)
                }
            }
        }
        val joined = parts.joinToString(" ").lowercase()
        val deactivateLabels = listOf(
            "deactivate", "בטל", "ביטול הפעלה", "סיום הפעלה", "turn off this admin"
        )
        if (deactivateLabels.none { joined.contains(it) }) return false
        val root = rootInActiveWindow ?: return false
        val texts = mutableListOf<String>()
        collectTexts(root, texts, 0)
        val all = texts.joinToString(" ").lowercase()
        return mentionsOurApp(texts, all) || treeContainsOurPackage(root, 0) ||
            treeContainsOurLabel(root)
    }

    private fun hasUninstallControl(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return hasClickableByLabels(
            root,
            listOf("uninstall", "הסר", "הסרה", "הסר התקנה", "uninstall updates", "delete app"),
            0
        )
    }

    private fun eatDangerousAppInfoAction() {
        clickCancelLikeButtons()
        performGlobalAction(GLOBAL_ACTION_BACK)
        hardKickTamper(TamperReason.UNINSTALL)
    }

    /**
     * הסרה / סגירה ידנית / ארכיון / השבת — only on OUR App Info page.
     * Walks source + parents (Compose often puts label on a child, click on parent).
     */
    private fun isOurDangerousAppInfoAction(
        event: AccessibilityEvent,
        eventPackage: String,
        root: AccessibilityNodeInfo?
    ): Boolean {
        if (root == null) return false
        if (TamperDetectors.isLauncher(eventPackage) || eventPackage == "com.android.systemui") {
            return false
        }
        if (!TamperDetectors.isSettings(eventPackage) &&
            !PrefsRepository.isPackageInstaller(eventPackage)
        ) return false
        if (TamperDetectors.isSettings(eventPackage) &&
            (TamperDetectors.isSettingsSearchUi("", root, eventPackage) ||
                TamperDetectors.isAllAppsListUi("", root, eventPackage))
        ) return false

        val rootPkg = root.packageName?.toString().orEmpty()
        // Installer click only if root is still installer (not home after Cancel)
        if (PrefsRepository.isPackageInstaller(eventPackage)) {
            if (!TamperDetectors.isPackageInstallerPackage(rootPkg)) return false
            if (!TamperDetectors.hasExactAppTitle(root, getString(R.string.app_name)) &&
                !TamperDetectors.hasExactAppTitle(root, PrefsRepository.OUR_LABEL)
            ) return false
        } else if (!TamperDetectors.isOurAppInfo(this, eventPackage, "", root)) {
            return false
        }

        val labels = ForeignStrings.dangerousAppInfoActionLabels(this)
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        if (labels.isEmpty()) return false

        // Only the click target (+ 2 parents). Do NOT scan whole page (הסרה always visible).
        val nodeTexts = mutableListOf<String>()
        event.text?.forEach { nodeTexts.add(it.toString()) }
        event.contentDescription?.let { nodeTexts.add(it.toString()) }
        runCatching {
            var node: AccessibilityNodeInfo? = event.source
            var hops = 0
            while (node != null && hops < 3) {
                try {
                    node.text?.let { nodeTexts.add(it.toString()) }
                    node.contentDescription?.let { nodeTexts.add(it.toString()) }
                    val parent = node.parent
                    A11yNodes.recycleQuietly(node)
                    node = parent
                } catch (_: Exception) {
                    A11yNodes.recycleQuietly(node)
                    node = null
                    break
                }
                hops++
            }
            A11yNodes.recycleQuietly(node)
        }
        if (nodeTexts.isEmpty()) return false

        return nodeTexts.any { raw ->
            val t = raw.trim().lowercase()
            if (t.isEmpty()) return@any false
            labels.any { label ->
                t == label || t.startsWith("$label ") || t.endsWith(" $label")
            }
        }
    }

    /** Cancel / לא / ביטול — leaving an uninstall dialog must never trigger PIN. */
    private fun isNegativeOrDismissClick(event: AccessibilityEvent): Boolean {
        val parts = mutableListOf<String>()
        event.text?.forEach { parts.add(it.toString()) }
        event.contentDescription?.let { parts.add(it.toString()) }
        runCatching {
            event.source?.let { src ->
                try {
                    src.text?.let { parts.add(it.toString()) }
                    src.contentDescription?.let { parts.add(it.toString()) }
                } finally {
                    A11yNodes.recycleQuietly(src)
                }
            }
        }
        val joined = parts.joinToString(" ").trim().lowercase()
        if (joined.isEmpty()) return false
        val negatives = listOf(
            "cancel", "ביטול", "לא", "no", "dismiss", "סגור", "close",
            "keep", "השאר", "keep app", "אל תסיר"
        )
        return negatives.any { joined == it || joined.startsWith("$it ") || joined.endsWith(" $it") }
    }

    /**
     * Event package still packageinstaller but active window already left it
     * (typical after Cancel) — do not self-protect.
     */
    private fun isStaleInstallerEvent(eventPackage: String, root: AccessibilityNodeInfo?): Boolean {
        if (!PrefsRepository.isPackageInstaller(eventPackage) &&
            !eventPackage.contains("packageinstaller", ignoreCase = true)
        ) return false
        val rootPkg = root?.packageName?.toString().orEmpty()
        if (rootPkg.isBlank()) return true
        return !TamperDetectors.isPackageInstallerPackage(rootPkg)
    }

    /** Force-stop / uninstall confirmation dialog while our App Info is the subject. */
    private fun isOurDangerousConfirmDialog(
        eventPackage: String,
        root: AccessibilityNodeInfo?
    ): Boolean {
        if (root == null) return false
        if (!TamperDetectors.isSettings(eventPackage) &&
            !PrefsRepository.isPackageInstaller(eventPackage)
        ) return false
        val rootPkg = root.packageName?.toString().orEmpty()
        if (PrefsRepository.isPackageInstaller(eventPackage) &&
            !TamperDetectors.isPackageInstallerPackage(rootPkg)
        ) return false
        if (TamperDetectors.isLauncher(rootPkg) || rootPkg == "com.android.systemui") return false
        if (TamperDetectors.isSettings(eventPackage) &&
            (TamperDetectors.isSettingsSearchUi("", root, eventPackage) ||
                TamperDetectors.isAllAppsListUi("", root, eventPackage))
        ) return false
        if (!TamperDetectors.hasExactAppTitle(root, getString(R.string.app_name)) &&
            !TamperDetectors.hasExactAppTitle(root, PrefsRepository.OUR_LABEL)
        ) return false

        val joined = buildString {
            fun walk(n: AccessibilityNodeInfo, depth: Int) {
                if (depth > 8 || length > 400) return
                n.text?.let { append(it).append(' ') }
                n.contentDescription?.let { append(it).append(' ') }
                val c = n.childCount.coerceAtMost(25)
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
        }.lowercase()

        val looksConfirm =
            joined.contains("force stop") || joined.contains("עצור בכוח") ||
                joined.contains("סגירה ידנית") ||
                joined.contains("uninstall app") || joined.contains("uninstall updates") ||
                joined.contains("disable app") || joined.contains("השבת את") ||
                joined.contains("להסיר את האפליקציה") ||
                TamperDetectors.nodeWithExactText(root, "הסרה") ||
                TamperDetectors.nodeWithExactText(root, "Uninstall")
        if (!looksConfirm) return false

        // Dialog chrome: OK / Force stop / Uninstall affirmative
        return TamperDetectors.nodeWithExactText(root, "OK") ||
            TamperDetectors.nodeWithExactText(root, "אישור") ||
            TamperDetectors.nodeWithExactText(root, "Force stop") ||
            TamperDetectors.nodeWithExactText(root, "עצור בכוח") ||
            TamperDetectors.nodeWithExactText(root, "סגירה ידנית") ||
            TamperDetectors.nodeWithExactText(root, "Uninstall") ||
            TamperDetectors.nodeWithExactText(root, "הסרה") ||
            TamperDetectors.nodeWithExactText(root, "הסר") ||
            TamperDetectors.nodeWithText(root, "Force stop") ||
            TamperDetectors.nodeWithText(root, "להסיר")
    }

    private fun mentionsOurApp(texts: List<String>, joined: String): Boolean {
        val label = getString(R.string.app_name).trim().lowercase()
        val ourPkg = PrefsRepository.OUR_PACKAGE.lowercase()
        // Exact token match only — substring hits All-apps lists / unrelated pages
        if (texts.any { it.trim().equals(getString(R.string.app_name), ignoreCase = true) }) return true
        if (texts.any { it.trim().equals(PrefsRepository.OUR_LABEL, ignoreCase = true) }) return true
        if (texts.any { it.trim().equals(PrefsRepository.OUR_PACKAGE, ignoreCase = true) }) return true
        // joined fallback: whole-string equality fragments only
        val parts = joined.split(Regex("\\s+"))
        return parts.any { it == label || it == ourPkg }
    }

    private fun treeContainsOurPackage(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 12) return false
        val id = node.viewIdResourceName?.lowercase().orEmpty()
        if (id.contains(PrefsRepository.OUR_PACKAGE.lowercase())) return true
        val t = (node.text?.toString().orEmpty() + node.contentDescription?.toString().orEmpty())
            .lowercase()
        if (t.contains(PrefsRepository.OUR_PACKAGE.lowercase())) return true
        val count = node.childCount.coerceAtMost(40)
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            try {
                if (treeContainsOurPackage(child, depth + 1)) return true
            } finally {
                A11yNodes.recycleQuietly(child)
            }
        }
        return false
    }

    private fun isSettingsPackage(packageName: String): Boolean =
        packageName == "com.android.settings" || packageName.startsWith("com.android.settings")

    private fun clickCancelLikeButtons() {
        val root = rootInActiveWindow ?: return
        clickFirstByLabels(
            root,
            listOf("cancel", "ביטול", "לא", "no", "dismiss", "סגור", "close", "keep", "השאר"),
            0
        )
    }

    /** True if a clickable label exists; recycles all children obtained via getChild. */
    private fun hasClickableByLabels(
        node: AccessibilityNodeInfo,
        labels: List<String>,
        depth: Int
    ): Boolean {
        if (depth > 12) return false
        val text = (node.text?.toString().orEmpty() + " " +
            node.contentDescription?.toString().orEmpty()).trim().lowercase()
        if (text.isNotEmpty() && labels.any { text == it || text.startsWith("$it ") }) {
            if (node.isClickable) return true
            var p: AccessibilityNodeInfo? = node.parent
            var hops = 0
            while (p != null && hops < 4) {
                try {
                    if (p.isClickable) {
                        A11yNodes.recycleQuietly(p)
                        return true
                    }
                    val next = p.parent
                    A11yNodes.recycleQuietly(p)
                    p = next
                } catch (_: Exception) {
                    A11yNodes.recycleQuietly(p)
                    p = null
                    break
                }
                hops++
            }
            A11yNodes.recycleQuietly(p)
        }
        val count = node.childCount.coerceAtMost(40)
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            try {
                if (hasClickableByLabels(child, labels, depth + 1)) return true
            } finally {
                A11yNodes.recycleQuietly(child)
            }
        }
        return false
    }

    /** Click first matching label; recycles children. */
    private fun clickFirstByLabels(
        node: AccessibilityNodeInfo,
        labels: List<String>,
        depth: Int
    ): Boolean {
        if (depth > 12) return false
        val text = (node.text?.toString().orEmpty() + " " +
            node.contentDescription?.toString().orEmpty()).trim().lowercase()
        if (text.isNotEmpty() && labels.any { text == it || text.startsWith("$it ") }) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            var p: AccessibilityNodeInfo? = node.parent
            var hops = 0
            while (p != null && hops < 4) {
                try {
                    if (p.isClickable) {
                        p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        A11yNodes.recycleQuietly(p)
                        return true
                    }
                    val next = p.parent
                    A11yNodes.recycleQuietly(p)
                    p = next
                } catch (_: Exception) {
                    A11yNodes.recycleQuietly(p)
                    p = null
                    break
                }
                hops++
            }
            A11yNodes.recycleQuietly(p)
        }
        val count = node.childCount.coerceAtMost(40)
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            try {
                if (clickFirstByLabels(child, labels, depth + 1)) return true
            } finally {
                A11yNodes.recycleQuietly(child)
            }
        }
        return false
    }

    private fun isPlayNewInstallScreen(event: AccessibilityEvent): Boolean {
        val texts = collectEventTexts(event, rootInActiveWindow)
        if (texts.isEmpty()) return false
        val joined = texts.joinToString(" ").lowercase()
        val isUpdate = PrefsRepository.UPDATE_KEYWORDS.any { joined.contains(it.lowercase()) } &&
            !joined.contains("install") && !joined.contains("התקן")
        if (isUpdate) return false
        if (PrefsRepository.NEW_INSTALL_KEYWORDS.none { joined.contains(it.lowercase()) }) return false
        return texts.any { t ->
            val s = t.trim().lowercase()
            s == "install" || s == "התקן" || s == "get" || s == "התקן עכשיו"
        }
    }

    private fun looksLikeSideloadInstall(event: AccessibilityEvent, packageName: String): Boolean {
        if (PrefsRepository.isPackageInstaller(packageName)) return false
        if (packageName == PrefsRepository.PLAY_STORE) return false
        val texts = collectEventTexts(event, rootInActiveWindow)
        if (texts.isEmpty()) return false
        val joined = texts.joinToString(" ").lowercase()
        return PrefsRepository.SIDELOAD_KEYWORDS.any { joined.contains(it.lowercase()) } &&
            PrefsRepository.NEW_INSTALL_KEYWORDS.any { joined.contains(it.lowercase()) }
    }

    private fun collectEventTexts(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo?
    ): MutableList<String> {
        val texts = mutableListOf<String>()
        event.text?.forEach { texts.add(it.toString()) }
        event.contentDescription?.let { texts.add(it.toString()) }
        event.className?.let { texts.add(it.toString()) }
        root?.let { collectTexts(it, texts, 0) }
        return texts
    }

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 10 || out.size > 80) return
        node.text?.let { out.add(it.toString()) }
        node.contentDescription?.let { out.add(it.toString()) }
        node.viewIdResourceName?.let { out.add(it) }
        val count = node.childCount.coerceAtMost(30)
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            try {
                collectTexts(child, out, depth + 1)
            } finally {
                A11yNodes.recycleQuietly(child)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: AppMonitorAccessibilityService? = null
    }
}
