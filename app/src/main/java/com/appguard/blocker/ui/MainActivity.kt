package com.appguard.blocker.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.appguard.blocker.ui.compose.theme.AppThemeMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.protection.ProtectionController
import com.appguard.blocker.ui.compose.AppGuardNavHost
import com.appguard.blocker.ui.compose.navigation.Routes
import com.appguard.blocker.ui.compose.theme.AppGuardTheme

class MainActivity : SecureActivity() {

    override val applyInsetsToRoot: Boolean = false

    private lateinit var prefs: PrefsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = PrefsRepository(this)
        val initialRoute = intent.getStringExtra(EXTRA_ROUTE)
        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current
            var refreshTick by mutableIntStateOf(0)
            var themeMode by mutableStateOf(prefs.appThemeMode)
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) refreshTick++
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            AppGuardTheme(themeMode = themeMode) {
                AppGuardNavHost(
                    prefs = prefs,
                    versionName = appVersionName(),
                    refreshTick = refreshTick,
                    initialRoute = initialRoute,
                    onThemeChanged = { mode -> themeMode = mode }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ProtectionController.expireLeaseIfNeeded(this)
    }

    private fun appVersionName(): String =
        runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "?" }

    companion object {
        const val EXTRA_ROUTE = "route"

        fun intent(context: android.content.Context, route: String): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_ROUTE, route)
    }
}
