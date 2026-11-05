package com.appguard.blocker.ui.compose.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.appguard.blocker.R
import com.appguard.blocker.admin.DeviceAdminHelper
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.ui.OnboardingSteps
import com.appguard.blocker.ui.compose.components.OnboardingRail
import com.appguard.blocker.ui.compose.ui.ConsumerLineDivider
import com.appguard.blocker.ui.compose.ui.ConsumerPrimaryButton
import com.appguard.blocker.ui.compose.ui.ConsumerRow
import com.appguard.blocker.ui.compose.ui.ConsumerSection
import com.appguard.blocker.ui.compose.ui.ConsumerStatusDot
import com.appguard.blocker.ui.compose.ui.ConsumerTopBar
import com.appguard.blocker.util.OemAutostartHelper
import com.appguard.blocker.util.PermissionHelper
import com.appguard.blocker.util.PermissionItem

enum class SetupMode {
    /** אשף התקנה — rail + סיום והפעלת הגנה */
    Wizard,
    /** הגדרות > הרשאות — תחזוקה בלבד */
    Maintenance,
}

@Composable
fun SetupScreen(
    prefs: PrefsRepository,
    refreshTick: Int,
    onContinueToAllowlist: () -> Unit,
    modifier: Modifier = Modifier,
    mode: SetupMode = SetupMode.Wizard,
) {
    if (mode == SetupMode.Wizard) {
        DisposableEffect(Unit) {
            prefs.protectionArmed = false
            onDispose { }
        }
        val context = LocalContext.current
        val ready = remember(refreshTick) { PermissionHelper.criticalReady(context) }

        Scaffold(
            modifier = modifier,
            topBar = { ConsumerTopBar(stringResource(R.string.setup_title)) },
            containerColor = MaterialTheme.colorScheme.surface,
            bottomBar = {
                ConsumerPrimaryButton(
                    text = stringResource(R.string.setup_continue),
                    enabled = ready,
                    onClick = {
                        if (!ready) {
                            Toast.makeText(context, R.string.setup_incomplete, Toast.LENGTH_LONG).show()
                            return@ConsumerPrimaryButton
                        }
                        prefs.setupCompleted = true
                        prefs.uninstallUnlocked = false
                        prefs.allowlistEnabled = false
                        prefs.protectionArmed = false
                        onContinueToAllowlist()
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        ) { padding ->
            PermissionsSetupList(
                refreshTick = refreshTick,
                mode = mode,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    } else {
        PermissionsSetupList(
            refreshTick = refreshTick,
            mode = mode,
            modifier = modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PermissionsSetupList(
    refreshTick: Int,
    mode: SetupMode,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var tick by remember(refreshTick) { mutableStateOf(0) }
    val states = remember(tick) { PermissionHelper.allStates(context) }
        .filter { state ->
            state.item != PermissionItem.OEM_AUTOSTART ||
                OemAutostartHelper.findAutostartIntent(context) != null
        }
    val grantedCritical = remember(states) {
        listOf(
            PermissionItem.ACCESSIBILITY,
            PermissionItem.USAGE_ACCESS,
            PermissionItem.DEVICE_ADMIN,
            PermissionItem.OVERLAY,
            PermissionItem.BATTERY_OPTIMIZATION
        ).count { item -> states.first { it.item == item }.granted }
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }

    LazyColumn(modifier = modifier) {
        if (mode == SetupMode.Wizard) {
            item {
                OnboardingRail(activeStep = OnboardingSteps.PERMISSIONS)
            }
        }
        item {
            Text(
                stringResource(
                    if (mode == SetupMode.Wizard) {
                        R.string.setup_subtitle
                    } else {
                        R.string.setup_permissions_maint_sub
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            LinearProgressIndicator(
                progress = { grantedCritical / 5f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Text(
                stringResource(R.string.setup_progress, grantedCritical, 5),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
            ConsumerSection(stringResource(R.string.required_permissions))
        }
        itemsIndexed(states) { index, state ->
            PermissionRow(
                item = state.item,
                granted = state.granted,
                onRequest = {
                    when (state.item) {
                        PermissionItem.ACCESSIBILITY ->
                            PermissionHelper.openAccessibility(context)
                        PermissionItem.USAGE_ACCESS ->
                            PermissionHelper.openUsageAccess(context)
                        PermissionItem.DEVICE_ADMIN ->
                            DeviceAdminHelper.requestEnable(context)
                        PermissionItem.OVERLAY -> PermissionHelper.openOverlay(context)
                        PermissionItem.BATTERY_OPTIMIZATION ->
                            PermissionHelper.openBatteryOptimization(context)
                        PermissionItem.NOTIFICATIONS ->
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        PermissionItem.OEM_AUTOSTART ->
                            if (!OemAutostartHelper.open(context)) {
                                Toast.makeText(
                                    context,
                                    R.string.perm_oem_unavailable,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                    tick++
                }
            )
            if (index < states.lastIndex) ConsumerLineDivider()
        }
        item {
            Column(
                Modifier.padding(
                    bottom = if (mode == SetupMode.Wizard) 88.dp else 32.dp
                )
            ) {}
        }
    }
}

@Composable
private fun PermissionRow(
    item: PermissionItem,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    val (title, desc) = when (item) {
        PermissionItem.ACCESSIBILITY ->
            R.string.perm_accessibility_title to R.string.perm_accessibility_desc
        PermissionItem.USAGE_ACCESS ->
            R.string.perm_usage_title to R.string.perm_usage_desc
        PermissionItem.DEVICE_ADMIN ->
            R.string.perm_admin_title to R.string.perm_admin_desc
        PermissionItem.OVERLAY ->
            R.string.perm_overlay_title to R.string.perm_overlay_desc
        PermissionItem.BATTERY_OPTIMIZATION ->
            R.string.perm_battery_title to R.string.perm_battery_desc
        PermissionItem.NOTIFICATIONS ->
            R.string.perm_notifications_title to R.string.perm_notifications_desc
        PermissionItem.OEM_AUTOSTART ->
            R.string.perm_oem_title to R.string.perm_oem_desc
    }
    val action = when {
        granted && item != PermissionItem.OEM_AUTOSTART -> stringResource(R.string.setup_done)
        item == PermissionItem.OEM_AUTOSTART -> stringResource(R.string.setup_open_oem)
        else -> stringResource(R.string.setup_enable)
    }
    ConsumerRow(
        title = stringResource(title),
        subtitle = stringResource(desc),
        onClick = if (!granted || item == PermissionItem.OEM_AUTOSTART) onRequest else null,
        leading = { ConsumerStatusDot(granted) },
        trailing = {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    )
}
