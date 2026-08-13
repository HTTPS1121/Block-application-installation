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
import com.appguard.blocker.protection.AppAccessGuard
import com.appguard.blocker.ui.BlockedActivity
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared block logic.
 * HOME is rate-limited lightly to avoid crashing the process; never "gives up" on a package.
 */
object BlockCoordinator {

    private val overlayShowing = AtomicBoolean(false)
    private var lastKickAt = 0L
    private var lastKickPackage: String? = null
    private var lastOverlayAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var overlayView: android.view.View? = null

    fun shouldBlockApp(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return false
        return com.appguard.blocker.data.PrefsRepository(context).shouldBlockPackage(packageName)
    }

    fun blockApp(context: Context, packageName: String, mode: String = BlockedActivity.MODE_APP) {
        if (packageName == context.packageName) return
        // User just opened / is inside guardian — never steal focus with HOME
        if (AppAccessGuard.mustNotKickGuardian()) return
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()

        // Always try to leave the forbidden app, but don't spam HOME 20x/sec (causes crashes)
        if (packageName != lastKickPackage || now - lastKickAt > 280) {
            lastKickPackage = packageName
            lastKickAt = now
            goHome(appContext)
        }

        mainHandler.post {
            try {
                if (Settings.canDrawOverlays(appContext)) {
                    showOverlay(appContext, packageName, mode)
                } else {
                    openBlockedActivity(appContext, packageName, mode)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun blockInstall(context: Context, packageName: String) {
        blockApp(context, packageName, BlockedActivity.MODE_INSTALL)
    }

    /**
     * Legacy path — must NEVER put a full-screen overlay over our own UI
     * (that locked users out of opening the guardian). Prefer Challenge activity.
     */
    fun blockUninstallAttempt(context: Context) {
        val appContext = context.applicationContext
        dismissOverlay(appContext)
        goHome(appContext)
    }

    fun goHome(context: Context) {
        try {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.applicationContext.startActivity(home)
        } catch (_: Exception) {
        }
    }

    fun isOverlayShowing(): Boolean = overlayShowing.get()

    /** Kaspersky "Please Wait" while kicking to PIN challenge. */
    fun showPleaseWait(context: Context) {
        val appContext = context.applicationContext
        if (!Settings.canDrawOverlays(appContext)) return
        mainHandler.post {
            try {
                showOverlay(appContext, appContext.packageName, MODE_PLEASE_WAIT)
            } catch (_: Exception) {
            }
        }
    }

    const val MODE_PLEASE_WAIT = "please_wait"

    private fun showOverlay(context: Context, packageName: String, mode: String) {
        val now = System.currentTimeMillis()
        if (overlayShowing.get() && now - lastOverlayAt < 500) {
            return
        }
        lastOverlayAt = now

        dismissOverlay(context)

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_blocked, null)
        val title = view.findViewById<TextView>(R.id.blockedTitle)
        val message = view.findViewById<TextView>(R.id.blockedMessage)
        val btn = view.findViewById<Button>(R.id.btnGoHome)

        val label = if (mode == BlockedActivity.MODE_UNINSTALL) {
            context.getString(R.string.app_name)
        } else {
            runCatching {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(packageName, 0)
                ).toString()
            }.getOrDefault(packageName)
        }

        when (mode) {
            BlockedActivity.MODE_INSTALL -> {
                title.setText(R.string.blocked_install_title)
                message.setText(R.string.blocked_install_message)
            }
            BlockedActivity.MODE_UNINSTALL, MODE_PLEASE_WAIT -> {
                title.setText(R.string.cannot_uninstall_title)
                message.setText(R.string.cannot_uninstall_message)
            }
            else -> {
                title.setText(R.string.blocked_title)
                message.text = context.getString(R.string.blocked_message, label.ifBlank { packageName })
            }
        }

        btn.setOnClickListener {
            dismissOverlay(context)
            goHome(context)
        }
        if (mode == MODE_PLEASE_WAIT) {
            btn.visibility = android.view.View.GONE
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
            overlayShowing.set(true)
        } catch (_: Exception) {
            overlayShowing.set(false)
            openBlockedActivity(context, packageName, mode)
        }
    }

    fun dismissOverlay(context: Context) {
        val view = overlayView ?: run {
            overlayShowing.set(false)
            return
        }
        overlayView = null
        overlayShowing.set(false)
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
        } catch (_: Exception) {
        }
    }

    private fun openBlockedActivity(context: Context, packageName: String, mode: String) {
        try {
            val intent = Intent(context, BlockedActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                putExtra(BlockedActivity.EXTRA_MODE, mode)
                putExtra(BlockedActivity.EXTRA_PACKAGE, packageName)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
