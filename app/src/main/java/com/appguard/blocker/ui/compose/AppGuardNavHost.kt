package com.appguard.blocker.ui.compose

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.ui.compose.AppCatalog
import com.appguard.blocker.ui.compose.navigation.Routes
import com.appguard.blocker.ui.compose.screens.AllowlistScreen
import com.appguard.blocker.ui.compose.screens.HomeScreen
import com.appguard.blocker.ui.compose.screens.SettingsAllowlistPolicyScreen
import com.appguard.blocker.ui.compose.screens.SettingsChangePinScreen
import com.appguard.blocker.ui.compose.screens.SettingsDeviceScreen
import com.appguard.blocker.ui.compose.screens.SettingsDnsScreen
import com.appguard.blocker.ui.compose.screens.SettingsRootScreen
import com.appguard.blocker.ui.compose.screens.SettingsSecurityHubScreen
import com.appguard.blocker.ui.compose.screens.SettingsThemeScreen
import com.appguard.blocker.ui.compose.screens.SetupMode
import com.appguard.blocker.ui.compose.screens.SetupScreen
import com.appguard.blocker.ui.compose.ui.ConsumerTopBar

private fun routeBase(route: String?): String =
    route?.substringBefore("?").orEmpty()

private fun acceptsInitialRoute(route: String?): Boolean {
    if (route.isNullOrBlank()) return false
    val base = routeBase(route)
    if (base in Routes.bottomBarRoutes || base == Routes.WIZARD_SETUP) return true
    if (base.startsWith("settings/")) return true
    if (route.startsWith(Routes.ALLOWLIST)) return true
    return false
}

private fun isSettingsRoute(route: String): Boolean =
    route == Routes.SETTINGS || route.startsWith("settings/")

@Composable
fun AppGuardNavHost(
    prefs: PrefsRepository,
    versionName: String,
    refreshTick: Int,
    initialRoute: String?,
    onThemeChanged: (com.appguard.blocker.ui.compose.theme.AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = routeBase(backStack?.destination?.route)
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        AppCatalog.attach(context)
    }

    androidx.compose.runtime.LaunchedEffect(initialRoute) {
        if (acceptsInitialRoute(initialRoute)) {
            navController.navigate(initialRoute!!) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Bottom-nav Home: pop nested stack (allowlist, nested settings) before restoreState.
    fun navigateToHomeRoot() {
        if (route == Routes.HOME) return
        if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
            navController.navigate(Routes.HOME) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    fun navigateToSettingsRoot() {
        if (route == Routes.SETTINGS) return
        if (!navController.popBackStack(Routes.SETTINGS, inclusive = false)) {
            navController.navigate(Routes.SETTINGS) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    fun navigateToAllowlistTab() {
        if (route == Routes.ALLOWLIST) return
        val allowlistRoute = Routes.allowlist(fromSetup = false)
        navController.navigate(allowlistRoute) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (route in Routes.bottomBarRoutes || isSettingsRoute(route)) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == Routes.HOME,
                        onClick = { navigateToHomeRoot() },
                        icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_home)) }
                    )
                    NavigationBarItem(
                        selected = route == Routes.ALLOWLIST,
                        onClick = { navigateToAllowlistTab() },
                        icon = { Icon(Icons.Outlined.Apps, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_allowlist)) }
                    )
                    NavigationBarItem(
                        selected = isSettingsRoute(route),
                        onClick = { navigateToSettingsRoot() },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    prefs = prefs,
                    versionName = versionName,
                    refreshTick = refreshTick,
                    onOpenAllowlistSetup = {
                        navController.navigate(Routes.allowlist(fromSetup = true)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenPermissions = {
                        navController.navigate(Routes.SETTINGS_PERMISSIONS) {
                            launchSingleTop = true
                        }
                    },
                    onOpenSettings = { navigateToSettingsRoot() },
                    onOpenAllowlist = { navigateToAllowlistTab() }
                )
            }
            composable(
                route = "${Routes.ALLOWLIST}?${Routes.ARG_FROM_SETUP}={${Routes.ARG_FROM_SETUP}}",
                arguments = listOf(
                    navArgument(Routes.ARG_FROM_SETUP) {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { entry ->
                val fromSetup = entry.arguments?.getBoolean(Routes.ARG_FROM_SETUP) == true
                AllowlistScreen(
                    prefs = prefs,
                    refreshTick = refreshTick,
                    fromSetup = fromSetup,
                    onFinishedSetup = { navigateToHomeRoot() }
                )
            }
            composable(Routes.WIZARD_SETUP) {
                SetupScreen(
                    prefs = prefs,
                    refreshTick = refreshTick,
                    mode = SetupMode.Wizard,
                    onContinueToAllowlist = {
                        navController.navigate(Routes.allowlist(fromSetup = true)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsRootScreen(
                    prefs = prefs,
                    refreshTick = refreshTick,
                    onOpenPermissions = {
                        navController.navigate(Routes.SETTINGS_PERMISSIONS) { launchSingleTop = true }
                    },
                    onOpenAllowlist = { navigateToAllowlistTab() },
                    onOpenAllowlistPolicy = {
                        navController.navigate(Routes.SETTINGS_ALLOWLIST_POLICY) { launchSingleTop = true }
                    },
                    onOpenSecurity = {
                        navController.navigate(Routes.SETTINGS_SECURITY) { launchSingleTop = true }
                    },
                    onOpenDns = {
                        navController.navigate(Routes.SETTINGS_DNS) { launchSingleTop = true }
                    },
                    onOpenDevice = {
                        navController.navigate(Routes.SETTINGS_DEVICE) { launchSingleTop = true }
                    },
                    onOpenTheme = {
                        navController.navigate(Routes.SETTINGS_THEME) { launchSingleTop = true }
                    }
                )
            }
            composable(Routes.SETTINGS_THEME) {
                SettingsThemeScreen(
                    prefs = prefs,
                    onBack = { navController.popBackStack() },
                    onThemeChanged = onThemeChanged
                )
            }
            composable(Routes.SETTINGS_PERMISSIONS) {
                androidx.compose.material3.Scaffold(
                    topBar = {
                        ConsumerTopBar(
                            title = stringResource(R.string.permissions_title_settings),
                            onBack = { navController.popBackStack() }
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.surface
                ) { padding ->
                    SetupScreen(
                        prefs = prefs,
                        refreshTick = refreshTick,
                        mode = SetupMode.Maintenance,
                        onContinueToAllowlist = {},
                        modifier = Modifier.padding(padding)
                    )
                }
            }
            composable(Routes.SETTINGS_SECURITY) {
                SettingsSecurityHubScreen(
                    onOpenChangePin = {
                        navController.navigate(Routes.SETTINGS_SECURITY_PIN) { launchSingleTop = true }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS_SECURITY_PIN) {
                SettingsChangePinScreen(
                    prefs = prefs,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS_ALLOWLIST_POLICY) {
                SettingsAllowlistPolicyScreen(
                    prefs = prefs,
                    refreshTick = refreshTick,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS_DNS) {
                SettingsDnsScreen(
                    prefs = prefs,
                    refreshTick = refreshTick,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS_DEVICE) {
                SettingsDeviceScreen(
                    prefs = prefs,
                    refreshTick = refreshTick,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
