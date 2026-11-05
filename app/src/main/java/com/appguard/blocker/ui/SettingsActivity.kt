package com.appguard.blocker.ui

import android.os.Bundle
import com.appguard.blocker.ui.compose.navigation.Routes

/** Legacy entry — all UI lives in [MainActivity] Compose graph. */
class SettingsActivity : SecureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(MainActivity.intent(this, Routes.SETTINGS))
        finish()
    }
}
