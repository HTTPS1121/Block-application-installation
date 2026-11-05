package com.appguard.blocker.ui.compose.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
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
        window.decorView.setBackgroundColor(colors.surface.toArgb())
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = themeMode == AppThemeMode.LIGHT
            isAppearanceLightNavigationBars = themeMode == AppThemeMode.LIGHT
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
