package com.appguard.blocker.ui

import android.os.Bundle
import com.appguard.blocker.ui.compose.navigation.Routes

/** Legacy entry — all UI lives in [MainActivity] Compose graph. */
class SetupActivity : SecureActivity() {
    override val requireAuth: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(MainActivity.intent(this, Routes.SETUP))
        finish()
    }
}
