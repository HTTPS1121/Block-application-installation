package com.appguard.blocker.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.ui.BlockedActivity
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared block logic used by Accessibility + UsageStats monitor.
 * Prefers SYSTEM_ALERT_WINDOW overlay; falls back to BlockedActivity.
 */
object BlockCoordinator {

    private val blocking = AtomicBoolean(false)
    private var lastPackage: String? = null
    private var lastAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var overlayView: android.view.View? = null

    fun isSystemExempt(packageName: String): Boolean {
        if (packageName in PrefsRepository.ALWAYS_ALLOWED) return true
        if (packageName == "com.android.systemui") return true
        if (packageName.contains("launcher", ignoreCase = true)) return true
        if (packageName.contains("inputmethod", ignoreCase = true)) return true
        if (packageName.contains("keyboard", ignoreCase = true)) return true
        if (packageName.endsWith(".permissioncontroller")) return true
        return false
    }

    fun shouldBlockApp(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return false
        if (isSystemExempt(packageName)) return false
        val prefs = PrefsRepository(context)
        if (!prefs.allowlistEnabled) return false
        if (prefs.isPackageAllowed(packageName)) return false
        return true
    }

    fun blockApp(context: Context, packageName: String, mode: String = BlockedActivity.MODE_APP) {
        if (!shouldThrottle(packageName)) return
        val appContext = context.applicationContext

        try {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            appContext.startActivity(home)
        } catch (_: Exception) {
        }

        mainHandler.post {
            if (Settings.canDrawOverlays(appContext)) {
                showOverlay(appContext, packageName, mode)
            } else {
                openBlockedActivity(appContext, packageName, mode)
            }
        }
    }

    fun blockInstall(context: Context, packageName: String) {
        blockApp(context, packageName, BlockedActivity.MODE_INSTALL)
    }

    private fun shouldThrottle(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        if (packageName == lastPackage && now - lastAt < 900) return false
        lastPackage = packageName
        lastAt = now
        return true
    }

    private fun showOverlay(context: Context, packageName: String, mode: String) {
        dismissOverlay(context)
        blocking.set(true)

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_blocked, null)
        val title = view.findViewById<TextView>(R.id.blockedTitle)
        val message = view.findViewById<TextView>(R.id.blockedMessage)
        val btn = view.findViewById<Button>(R.id.btnGoHome)

        val label = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrDefault(packageName)

        if (mode == BlockedActivity.MODE_INSTALL) {
            title.setText(R.string.blocked_install_title)
            message.setText(R.string.blocked_install_message)
        } else {
            title.setText(R.string.blocked_title)
            message.text = context.getString(R.string.blocked_message, label.ifBlank { packageName })
        }

        btn.setOnClickListener {
            dismissOverlay(context)
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(home)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            wm.addView(view, params)
            overlayView = view
        } catch (_: Exception) {
            blocking.set(false)
            openBlockedActivity(context, packageName, mode)
        }
    }

    fun dismissOverlay(context: Context) {
        val view = overlayView ?: return
        overlayView = null
        blocking.set(false)
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
        } catch (_: Exception) {
        }
    }

    private fun openBlockedActivity(context: Context, packageName: String, mode: String) {
        blocking.set(true)
        val intent = Intent(context, BlockedActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(BlockedActivity.EXTRA_MODE, mode)
            putExtra(BlockedActivity.EXTRA_PACKAGE, packageName)
        }
        context.startActivity(intent)
        mainHandler.postDelayed({ blocking.set(false) }, 1000)
    }
}
