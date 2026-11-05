package com.appguard.blocker.ui

import android.os.Bundle
import com.appguard.blocker.ui.compose.navigation.Routes

/** Legacy entry — all UI lives in [MainActivity] Compose graph. */
class AllowlistActivity : SecureActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fromSetup = intent.getBooleanExtra(EXTRA_FROM_SETUP, false)
        startActivity(MainActivity.intent(this, Routes.allowlist(fromSetup)))
        finish()
    }

    companion object {
        const val EXTRA_FROM_SETUP = "from_setup"
    }
}
