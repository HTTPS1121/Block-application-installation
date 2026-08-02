package com.appguard.blocker.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.protection.AppAccessGuard
import com.appguard.blocker.util.AuthSession

/**
 * Applies system-bar insets and re-asks PIN when returning from background.
 */
abstract class SecureActivity : AppCompatActivity() {

    protected open val requireAuth: Boolean = true
    protected open val applyInsetsToRoot: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        if (applyInsetsToRoot && view != null) {
            applySystemInsets(view)
        }
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        if (applyInsetsToRoot) {
            val content = findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)
            if (content != null) applySystemInsets(content)
        }
    }

    override fun onResume() {
        super.onResume()
        AppAccessGuard.onGuardianUiOpened(this)
        if (requireAuth) enforceAuth()
    }

    private fun enforceAuth() {
        val prefs = PrefsRepository(this)
        if (!prefs.hasPin()) return
        // During first-time setup wizard, don't bounce to PIN on every Settings return
        if (!prefs.setupCompleted) return
        if (AuthSession.isUnlocked()) return
        startActivity(
            Intent(this, PinActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    protected fun applySystemInsets(target: View) {
        val initialL = target.paddingLeft
        val initialT = target.paddingTop
        val initialR = target.paddingRight
        val initialB = target.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(target) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = initialL + bars.left,
                top = initialT + bars.top,
                right = initialR + bars.right,
                bottom = initialB + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(target)
    }

    companion object {
        fun applyInsets(activity: Activity, target: View) {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            val initialL = target.paddingLeft
            val initialT = target.paddingTop
            val initialR = target.paddingRight
            val initialB = target.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(target) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                v.updatePadding(
                    left = initialL + bars.left,
                    top = initialT + bars.top,
                    right = initialR + bars.right,
                    bottom = initialB + bars.bottom
                )
                insets
            }
            ViewCompat.requestApplyInsets(target)
        }
    }
}
