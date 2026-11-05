package com.appguard.blocker.ui.compose.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.service.UsageMonitorService
import com.appguard.blocker.ui.compose.ui.ConsumerGroup
import com.appguard.blocker.ui.compose.ui.ConsumerLineDivider
import com.appguard.blocker.ui.compose.ui.ConsumerRow
import com.appguard.blocker.ui.compose.ui.ConsumerSection
import com.appguard.blocker.ui.compose.ui.ConsumerTopBar
import com.appguard.blocker.util.PermissionHelper

@Composable
fun HomeScreen(
    prefs: PrefsRepository,
    versionName: String,
    refreshTick: Int,
    onOpenAllowlistSetup: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAllowlist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var allowlistOn by remember(refreshTick) { mutableStateOf(prefs.allowlistEnabled) }
    val permissionsReady = remember(refreshTick) { PermissionHelper.criticalReady(context) }
    val count = remember(refreshTick) { prefs.countApprovedIdentities() }

    Scaffold(
        modifier = modifier,
        topBar = { ConsumerTopBar(stringResource(R.string.app_name)) },
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            if (!allowlistOn) {
                FloatingActionButton(
                    onClick = {
                        if (!permissionsReady) {
                            Toast.makeText(context, R.string.setup_incomplete, Toast.LENGTH_LONG).show()
                            onOpenPermissions()
                        } else {
                            Toast.makeText(context, R.string.full_protection_on, Toast.LENGTH_LONG).show()
                            onOpenAllowlistSetup()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.largeIncreased,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_m3_shield),
                        contentDescription = null
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            val heroContainer = if (allowlistOn) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
            val heroOn = if (allowlistOn) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.extraLargeIncreased,
                color = heroContainer
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        text = if (allowlistOn) {
                            stringResource(R.string.protection_active)
                        } else {
                            stringResource(R.string.protection_inactive)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = heroOn
                    )
                    Text(
                        text = stringResource(R.string.allowlist_count, count),
                        style = MaterialTheme.typography.headlineMedium,
                        color = heroOn,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.main_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = heroOn.copy(alpha = 0.82f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            if (!permissionsReady) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = stringResource(R.string.home_perm_banner),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }

            ConsumerSection(stringResource(R.string.home_section_control))
            ConsumerGroup {
                ConsumerRow(
                    title = stringResource(R.string.level1_title),
                    subtitle = stringResource(R.string.level1_desc),
                    leading = {
                        Icon(
                            painterResource(R.drawable.ic_m3_shield),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailing = {
                        Switch(
                            checked = allowlistOn,
                            onCheckedChange = { checked ->
                                if (checked && !permissionsReady) {
                                    Toast.makeText(
                                        context,
                                        R.string.setup_incomplete,
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onOpenPermissions()
                                    return@Switch
                                }
                                allowlistOn = checked
                                prefs.allowlistEnabled = checked
                                prefs.protectionArmed = checked && PermissionHelper.criticalReady(context)
                                if (prefs.allowlistEnabled && PermissionHelper.usageAccessGranted(context)) {
                                    UsageMonitorService.start(context)
                                } else if (!prefs.allowlistEnabled) {
                                    UsageMonitorService.stop(context)
                                }
                            }
                        )
                    }
                )
                ConsumerLineDivider()
                ConsumerRow(
                    title = stringResource(R.string.manage_allowlist),
                    subtitle = stringResource(R.string.settings_allowlist_entry_sub),
                    onClick = onOpenAllowlist,
                    leading = {
                        Icon(
                            painterResource(R.drawable.ic_m3_apps),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }

            ConsumerSection(stringResource(R.string.home_section_more))
            ConsumerGroup {
                ConsumerRow(
                    title = stringResource(R.string.settings_all_settings),
                    subtitle = stringResource(R.string.settings_all_settings_sub),
                    onClick = onOpenSettings,
                    leading = {
                        Icon(
                            painterResource(R.drawable.ic_m3_settings),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_version_label, versionName),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
            Spacer(Modifier.height(72.dp))
        }
    }
}
