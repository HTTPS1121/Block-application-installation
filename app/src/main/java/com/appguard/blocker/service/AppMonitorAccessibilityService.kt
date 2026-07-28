package com.appguard.blocker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.ui.BlockedActivity

class AppMonitorAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PrefsRepository
    private var lastBlockedPackage: String? = null
    private var lastBlockAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PrefsRepository(this)
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!::prefs.isInitialized) prefs = PrefsRepository(this)

        val packageName = event.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return

        // Level 2: block install UI flows
        if (prefs.installBlockEnabled && isInstallFlow(event, packageName)) {
            blockInstall(packageName)
            return
        }

        // Level 1: allowlist gate
        if (!prefs.allowlistEnabled) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (prefs.isPackageAllowed(packageName)) return
        if (isLauncherOrSystemUi(packageName)) return

        blockApp(packageName)
    }

    private fun isInstallFlow(event: AccessibilityEvent, packageName: String): Boolean {
        if (packageName !in PrefsRepository.INSTALLER_PACKAGES &&
            !packageName.contains("packageinstaller", ignoreCase = true) &&
            packageName != "com.android.vending"
        ) {
            return false
        }

        val texts = mutableListOf<String>()
        event.text?.forEach { texts.add(it.toString()) }
        event.contentDescription?.let { texts.add(it.toString()) }
        rootInActiveWindow?.let { collectTexts(it, texts) }

        val joined = texts.joinToString(" ").lowercase()
        return PrefsRepository.INSTALL_BLOCK_KEYWORDS.any { keyword ->
            joined.contains(keyword.lowercase())
        } || packageName.contains("packageinstaller", ignoreCase = true)
    }

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.let { out.add(it.toString()) }
        node.contentDescription?.let { out.add(it.toString()) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectTexts(child, out)
            }
        }
    }

    private fun blockInstall(packageName: String) {
        if (shouldThrottle(packageName)) return
        performGlobalAction(GLOBAL_ACTION_HOME)
        val intent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedActivity.EXTRA_MODE, BlockedActivity.MODE_INSTALL)
            putExtra(BlockedActivity.EXTRA_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    private fun blockApp(packageName: String) {
        if (shouldThrottle(packageName)) return
        performGlobalAction(GLOBAL_ACTION_HOME)
        val intent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedActivity.EXTRA_MODE, BlockedActivity.MODE_APP)
            putExtra(BlockedActivity.EXTRA_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    private fun shouldThrottle(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && now - lastBlockAt < 1200) return true
        lastBlockedPackage = packageName
        lastBlockAt = now
        return false
    }

    private fun isLauncherOrSystemUi(packageName: String): Boolean {
        return packageName == "com.android.systemui" ||
            packageName.contains("launcher", ignoreCase = true) ||
            packageName.contains("home", ignoreCase = true)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: AppMonitorAccessibilityService? = null

        fun isRunning(): Boolean = instance != null
    }
}
