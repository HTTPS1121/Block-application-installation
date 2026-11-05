package com.appguard.blocker.ui.compose.theme

enum class AppThemeMode(val storageKey: String) {
    LIGHT("light"),
    DARK("dark"),
    AMOLED("amoled");

    companion object {
        fun fromStorage(value: String?): AppThemeMode =
            entries.firstOrNull { it.storageKey == value } ?: DARK
    }
}
