package com.appguard.blocker.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.protection.AppAccessGuard
import com.appguard.blocker.ui.compose.screens.PinScreen
import com.appguard.blocker.ui.compose.screens.navigateAfterPinUnlock
import com.appguard.blocker.ui.compose.theme.AppGuardActivityContent
import com.appguard.blocker.util.AuthSession
import com.appguard.blocker.util.DevAdb

class PinActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        prefs = PrefsRepository(this)
        AppAccessGuard.onGuardianUiOpened(this)

        if (tryHandleDevAdb(intent)) return

        val setupMode = !prefs.hasPin()
        setContent {
            AppGuardActivityContent(themeMode = prefs.appThemeMode) {
                PinScreen(
                    prefs = prefs,
                    setupModeInitial = setupMode,
                    versionName = appVersionName(),
                    onUnlocked = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tryHandleDevAdb(intent)
    }

    /** Dev/debug APK: adb_unlock / adb_pin from [scripts/adb-dev-unlock.ps1]. */
    private fun tryHandleDevAdb(intent: Intent?): Boolean {
        if (!DevAdb.isDevBuild(this)) return false
        if (DevAdb.wantsUnlock(intent)) {
            AuthSession.unlock()
            navigateAfterPinUnlock(this, prefs)
            finish()
            return true
        }
        val pin = DevAdb.pinFromIntent(intent) ?: return false
        if (!prefs.hasPin()) return false
        if (!prefs.verifyForLogin(pin) && !prefs.verifyRecoveryCode(pin)) return false
        AuthSession.unlock()
        navigateAfterPinUnlock(this, prefs)
        finish()
        return true
    }

    private fun appVersionName(): String =
        runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "?" }
}
