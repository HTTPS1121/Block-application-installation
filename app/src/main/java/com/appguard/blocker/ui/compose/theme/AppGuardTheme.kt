package com.appguard.blocker.ui.compose.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun AppGuardTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit,
) {
    val colors = when (themeMode) {
        AppThemeMode.LIGHT -> appLightColorScheme()
        AppThemeMode.DARK -> appDarkColorScheme()
        AppThemeMode.AMOLED -> appAmoledColorScheme()
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.decorView.setBackgroundColor(colors.background.toArgb())
        window.statusBarColor = colors.background.toArgb()
        window.navigationBarColor = colors.background.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = themeMode == AppThemeMode.LIGHT
            isAppearanceLightNavigationBars = themeMode == AppThemeMode.LIGHT
        }
    }

    MaterialTheme(colorScheme = colors, content = content)
}
