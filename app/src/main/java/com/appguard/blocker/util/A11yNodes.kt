package com.appguard.blocker.util

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/** Safe recycle for nodes obtained via getChild / findByText / event.source. */
object A11yNodes {

    fun recycleQuietly(node: AccessibilityNodeInfo?) {
        if (node == null) return
        try {
            // recycle() required pre-API 33; no-op / deprecated afterward
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                node.recycle()
            }
        } catch (_: Exception) {
        }
    }

    fun recycleAll(nodes: List<AccessibilityNodeInfo>?) {
        nodes?.forEach { recycleQuietly(it) }
    }

    /**
     * Obtain child [index], run [block], always recycle the child afterward.
     * Do not retain the child outside [block].
     */
    inline fun <T> withChild(
        parent: AccessibilityNodeInfo,
        index: Int,
        block: (AccessibilityNodeInfo) -> T
    ): T? {
        val child = parent.getChild(index) ?: return null
        return try {
            block(child)
        } finally {
            recycleQuietly(child)
        }
    }
}
