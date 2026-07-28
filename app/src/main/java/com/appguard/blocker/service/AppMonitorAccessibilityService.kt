package com.appguard.blocker.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.appguard.blocker.data.PrefsRepository

class AppMonitorAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PrefsRepository
    private var lastBlockedPackage: String? = null
    private var lastBlockAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PrefsRepository(this)
        instance = this
        UsageMonitorService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!::prefs.isInitialized) prefs = PrefsRepository(this)

        val packageName = event.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return

        if (prefs.installBlockEnabled && isInstallFlow(event, packageName)) {
            if (!shouldThrottle(packageName)) return
            // Close current UI aggressively then overlay
            performGlobalAction(GLOBAL_ACTION_HOME)
            BlockCoordinator.blockInstall(this, packageName)
            return
        }

        if (!prefs.allowlistEnabled) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        if (!BlockCoordinator.shouldBlockApp(this, packageName)) return
        if (!shouldThrottle(packageName)) return

        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
        BlockCoordinator.blockApp(this, packageName)
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
            node.getChild(i)?.let { child -> collectTexts(child, out) }
        }
    }

    private fun shouldThrottle(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && now - lastBlockAt < 1000) return false
        lastBlockedPackage = packageName
        lastBlockAt = now
        return true
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
