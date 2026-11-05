package com.appguard.blocker.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.ui.compose.screens.RecoveryScreen
import com.appguard.blocker.ui.compose.theme.AppGuardActivityContent
import com.appguard.blocker.util.AuthSession

class RecoveryActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsRepository
    private lateinit var shownCode: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        prefs = PrefsRepository(this)
        AuthSession.unlock()
        shownCode = savedInstanceState?.getString(STATE_CODE)
            ?: intent.getStringExtra(EXTRA_CODE)
            ?: prefs.generateAndStoreRecoveryCode()
        intent.putExtra(EXTRA_CODE, shownCode)

        setContent {
            AppGuardActivityContent(themeMode = prefs.appThemeMode) {
                RecoveryScreen(
                    prefs = prefs,
                    code = shownCode,
                    onDone = { finish() }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::shownCode.isInitialized) {
            outState.putString(STATE_CODE, shownCode)
        }
    }

    companion object {
        const val EXTRA_CODE = "recovery_code"
        private const val STATE_CODE = "state_recovery_code"
    }
}
