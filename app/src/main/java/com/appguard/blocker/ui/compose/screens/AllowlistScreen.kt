package com.appguard.blocker.ui.compose.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.protection.ProtectionController
import com.appguard.blocker.ui.OnboardingSteps
import com.appguard.blocker.ui.compose.AppCatalog
import com.appguard.blocker.ui.compose.AppCatalogEntry
import com.appguard.blocker.ui.compose.components.OnboardingRail
import com.appguard.blocker.ui.compose.ui.ConsumerLineDivider
import com.appguard.blocker.ui.compose.ui.ConsumerPrimaryButton
import com.appguard.blocker.ui.compose.ui.ConsumerRow
import com.appguard.blocker.ui.compose.ui.ConsumerSearchField
import com.appguard.blocker.ui.compose.ui.ConsumerTopBar

private enum class AllowlistFilter { ALL, ALLOWED, NOT_ALLOWED, UNINSTALLED }
private enum class AllowlistViewMode { COMFORTABLE, COMPACT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowlistScreen(
    prefs: PrefsRepository,
    refreshTick: Int, // kept: parent recomposes on resume; list itself is cache-first
    fromSetup: Boolean,
    onFinishedSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val live by AppCatalog.entries.collectAsState()
    val overrides = remember { mutableStateMapOf<String, Boolean>() }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AllowlistFilter.ALL) }
    var viewMode by rememberSaveable { mutableStateOf(AllowlistViewMode.COMFORTABLE) }

    val apps by remember {
        derivedStateOf {
            live.map { entry ->
                overrides[entry.packageName]?.let { entry.copy(allowed = it) } ?: entry
            }
        }
    }

    val installedFiltered by remember {
        derivedStateOf {
            if (filter == AllowlistFilter.UNINSTALLED) return@derivedStateOf emptyList()
            val q = query.trim().lowercase()
            apps.asSequence()
                .filter { it.installed }
                .filter {
                    when (filter) {
                        AllowlistFilter.ALLOWED -> it.allowed
                        AllowlistFilter.NOT_ALLOWED -> !it.allowed
                        else -> true
                    }
                }
                .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
                .toList()
        }
    }
    val uninstalledFiltered by remember {
        derivedStateOf {
            if (!prefs.rememberApprovalAfterReinstall) return@derivedStateOf emptyList()
            if (filter == AllowlistFilter.NOT_ALLOWED) return@derivedStateOf emptyList()
            val q = query.trim().lowercase()
            apps.filter {
                !it.installed &&
                    (q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q))
            }
        }
    }
    val compact = viewMode == AllowlistViewMode.COMPACT

    Scaffold(
        modifier = modifier,
        topBar = { ConsumerTopBar(stringResource(R.string.allowlist_title)) },
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            ConsumerPrimaryButton(
                text = stringResource(R.string.approve),
                enabled = apps.isNotEmpty(),
                onClick = {
                    AppCatalog.saveAllowlist(context, prefs, apps.toList(), armAfterSave = fromSetup)
                    Toast.makeText(context, R.string.saved, Toast.LENGTH_SHORT).show()
                    if (fromSetup) {
                        ProtectionController.arm(context)
                        onFinishedSetup()
                    }
                },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (fromSetup) {
                OnboardingRail(activeStep = OnboardingSteps.ALLOWLIST)
                Text(
                    stringResource(R.string.allowlist_from_setup),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            ConsumerSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.search_apps)
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == AllowlistFilter.ALL,
                    onClick = { filter = AllowlistFilter.ALL },
                    shape = MaterialTheme.shapes.large,
                    label = { Text(stringResource(R.string.allowlist_filter_all)) }
                )
                FilterChip(
                    selected = filter == AllowlistFilter.ALLOWED,
                    onClick = { filter = AllowlistFilter.ALLOWED },
                    shape = MaterialTheme.shapes.large,
                    label = { Text(stringResource(R.string.allowlist_filter_allowed)) }
                )
                FilterChip(
                    selected = filter == AllowlistFilter.NOT_ALLOWED,
                    onClick = { filter = AllowlistFilter.NOT_ALLOWED },
                    shape = MaterialTheme.shapes.large,
                    label = { Text(stringResource(R.string.allowlist_filter_blocked)) }
                )
                if (prefs.rememberApprovalAfterReinstall) {
                    FilterChip(
                        selected = filter == AllowlistFilter.UNINSTALLED,
                        onClick = { filter = AllowlistFilter.UNINSTALLED },
                        shape = MaterialTheme.shapes.large,
                        label = { Text(stringResource(R.string.allowlist_filter_uninstalled)) }
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                SegmentedButton(
                    selected = viewMode == AllowlistViewMode.COMFORTABLE,
                    onClick = { viewMode = AllowlistViewMode.COMFORTABLE },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text(stringResource(R.string.allowlist_view_comfortable)) }
                )
                SegmentedButton(
                    selected = viewMode == AllowlistViewMode.COMPACT,
                    onClick = { viewMode = AllowlistViewMode.COMPACT },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text(stringResource(R.string.allowlist_view_compact)) }
                )
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = {
                        apps.filter { it.installed }.forEach { overrides[it.packageName] = true }
                    },
                    shape = MaterialTheme.shapes.large,
                    label = { Text(stringResource(R.string.select_all)) }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        apps.filter { it.installed }.forEach { overrides[it.packageName] = false }
                    },
                    shape = MaterialTheme.shapes.large,
                    label = { Text(stringResource(R.string.deselect_all)) }
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                items(installedFiltered, key = { "i-${it.packageName}" }) { entry ->
                    val checked = entry.allowed
                    ConsumerRow(
                        title = entry.label,
                        subtitle = if (compact) null else entry.packageName,
                        onClick = { overrides[entry.packageName] = !checked },
                        leading = { AllowlistIcon(entry.packageName, compact) },
                        trailing = {
                            Checkbox(checked = checked, onCheckedChange = { on -> overrides[entry.packageName] = on })
                        }
                    )
                    ConsumerLineDivider()
                }
                if (uninstalledFiltered.isNotEmpty()) {
                    item(key = "uninstalled-header") {
                        Text(
                            stringResource(R.string.allowlist_uninstalled_section_title, uninstalledFiltered.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                    items(uninstalledFiltered, key = { "u-${it.packageName}" }) { entry ->
                        ConsumerRow(
                            title = entry.label,
                            subtitle = if (compact) null else entry.packageName,
                            onClick = null,
                            leading = { AllowlistIcon(entry.packageName, compact) },
                            trailing = {
                                TextButton(onClick = {
                                    prefs.removeRememberedApproval(entry.packageName)
                                    overrides.remove(entry.packageName)
                                    AppCatalog.saveAllowlist(
                                        context,
                                        prefs,
                                        apps.filter { it.packageName != entry.packageName },
                                        armAfterSave = false
                                    )
                                    Toast.makeText(context, R.string.allowlist_revoked_toast, Toast.LENGTH_SHORT).show()
                                }) { Text(stringResource(R.string.allowlist_revoke_approval)) }
                            }
                        )
                        if (!compact) {
                            Text(
                                stringResource(R.string.allowlist_not_installed_chip),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 4.dp)
                            )
                        }
                        ConsumerLineDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AllowlistIcon(packageName: String, compact: Boolean) {
    val iconGen by AppCatalog.iconRevision.collectAsState()
    val bitmap = remember(packageName, iconGen) { AppCatalog.peekIcon(packageName) }
    val size = if (compact) 36.dp else 44.dp
    if (bitmap != null) {
        Image(
            bitmap!!,
            contentDescription = null,
            modifier = Modifier.size(size).clip(
                if (compact) MaterialTheme.shapes.small else MaterialTheme.shapes.medium
            )
        )
    } else {
        Icon(
            Icons.Outlined.Android,
            contentDescription = null,
            modifier = Modifier.size(size).padding(if (compact) 4.dp else 6.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
