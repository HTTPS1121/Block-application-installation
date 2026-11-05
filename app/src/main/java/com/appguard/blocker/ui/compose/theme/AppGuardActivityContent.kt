package com.appguard.blocker.ui.compose.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Full-screen Compose shell for launcher / system Activities (PIN, challenge, …).
 * Fills the window with [MaterialTheme.colorScheme.background] so edge-to-edge
 * does not show the static XML theme (dark) under a mismatched MaterialTheme.
 */
@Composable
fun AppGuardActivityContent(
    themeMode: AppThemeMode,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AppGuardTheme(themeMode = themeMode) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            content()
        }
    }
}
