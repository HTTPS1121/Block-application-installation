package com.appguard.blocker.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.appguard.blocker.R
import com.appguard.blocker.ui.compose.screens.BlockedScreen
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.ui.compose.theme.AppGuardActivityContent

class BlockedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_APP
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        }.getOrDefault(pkg)

        val (titleRes, message) = when (mode) {
            MODE_INSTALL -> R.string.blocked_install_title to getString(R.string.blocked_install_message)
            MODE_UNINSTALL -> R.string.cannot_uninstall_title to getString(R.string.cannot_uninstall_message)
            else -> R.string.blocked_title to getString(R.string.blocked_message, label.ifBlank { pkg })
        }

        val prefs = PrefsRepository(this)
        setContent {
            AppGuardActivityContent(themeMode = prefs.appThemeMode) {
                BlockedScreen(
                    titleRes = titleRes,
                    message = message,
                    onGoHome = { goHome() }
                )
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        finish()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_PACKAGE = "package"
        const val MODE_APP = "app"
        const val MODE_INSTALL = "install"
        const val MODE_UNINSTALL = "uninstall"
    }
}
