package com.appguard.blocker.ui.compose.navigation

object Routes {
    const val HOME = "home"
    const val ALLOWLIST = "allowlist"
    /** אשף התקנה — בלי bottom nav */
    const val WIZARD_SETUP = "wizard_setup"
    /** @deprecated use [WIZARD_SETUP] */
    const val SETUP = WIZARD_SETUP

    const val SETTINGS = "settings"
    const val SETTINGS_PERMISSIONS = "settings/permissions"
    const val SETTINGS_SECURITY = "settings/security"
    const val SETTINGS_SECURITY_PIN = "settings/security/pin"
    const val SETTINGS_ALLOWLIST_POLICY = "settings/allowlist_policy"
    const val SETTINGS_DNS = "settings/dns"
    const val SETTINGS_DEVICE = "settings/device"
    const val SETTINGS_THEME = "settings/theme"

    const val ARG_FROM_SETUP = "fromSetup"

    fun allowlist(fromSetup: Boolean) =
        "$ALLOWLIST?$ARG_FROM_SETUP=$fromSetup"

    val bottomBarRoutes = setOf(HOME, SETTINGS, ALLOWLIST)
}
