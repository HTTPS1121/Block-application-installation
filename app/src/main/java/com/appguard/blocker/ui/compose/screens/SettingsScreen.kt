package com.appguard.blocker.ui.compose.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.appguard.blocker.R
import com.appguard.blocker.admin.DeviceAdminHelper
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.dns.PrivateDns
import com.appguard.blocker.dns.PrivateDnsEnforcer
import com.appguard.blocker.protection.ProtectionController
import com.appguard.blocker.protection.TamperReason
import com.appguard.blocker.ui.compose.ui.ConsumerFormBlock
import com.appguard.blocker.ui.compose.ui.ConsumerLineDivider
import com.appguard.blocker.ui.compose.ui.ConsumerPrimaryButton
import com.appguard.blocker.ui.compose.ui.ConsumerRow
import com.appguard.blocker.ui.compose.ui.ConsumerSection
import com.appguard.blocker.ui.compose.theme.AppThemeMode
import com.appguard.blocker.ui.compose.ui.ConsumerTopBar
import com.appguard.blocker.util.PermissionHelper

@Composable
fun SettingsRootScreen(
    prefs: PrefsRepository,
    refreshTick: Int,
    onOpenPermissions: () -> Unit,
    onOpenAllowlist: () -> Unit,
    onOpenAllowlistPolicy: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenDevice: () -> Unit,
    onOpenTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val permissionsReady = remember(refreshTick) { PermissionHelper.criticalReady(context) }
    val phoneDns = remember(refreshTick) { PrivateDns.describe(context, PrivateDns.read(context)) }
    val dnsLocked = remember(refreshTick) { prefs.dnsLockEnabled }
    val uninstallLocked = remember(refreshTick) {
        !prefs.hasValidRemovalLease() && DeviceAdminHelper.isAdminActive(context)
    }

    Scaffold(
        modifier = modifier,
        topBar = { ConsumerTopBar(stringResource(R.string.settings_title)) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item { ConsumerSection(stringResource(R.string.settings_group_protection)) }
            item {
                ConsumerRow(
                    title = stringResource(R.string.permissions_title_settings),
                    subtitle = if (permissionsReady) {
                        stringResource(R.string.permissions_ready)
                    } else {
                        stringResource(R.string.permissions_missing)
                    },
                    onClick = onOpenPermissions
                )
            }
            item { ConsumerLineDivider() }
            item {
                ConsumerRow(
                    title = stringResource(R.string.settings_allowlist_entry),
                    subtitle = stringResource(R.string.settings_allowlist_entry_sub),
                    onClick = onOpenAllowlist
                )
            }
            item { ConsumerLineDivider() }
            item {
                ConsumerRow(
                    title = stringResource(R.string.settings_reinstall_approval_title),
                    subtitle = stringResource(R.string.settings_reinstall_entry_sub),
                    onClick = onOpenAllowlistPolicy
                )
            }

            item { ConsumerSection(stringResource(R.string.settings_group_appearance)) }
            item {
                ConsumerRow(
                    title = stringResource(R.string.settings_theme_title),
                    subtitle = stringResource(
                        when (prefs.appThemeMode) {
                            AppThemeMode.LIGHT -> R.string.settings_theme_light
                            AppThemeMode.DARK -> R.string.settings_theme_dark
                            AppThemeMode.AMOLED -> R.string.settings_theme_amoled
                        }
                    ),
                    onClick = onOpenTheme
                )
            }

            item { ConsumerSection(stringResource(R.string.settings_group_security)) }
            item {
                ConsumerRow(
                    title = stringResource(R.string.settings_security_hub_title),
                    subtitle = stringResource(R.string.settings_security_hub_sub),
                    onClick = onOpenSecurity
                )
            }

            item { ConsumerSection(stringResource(R.string.settings_group_advanced)) }
            item {
                ConsumerRow(
                    title = stringResource(R.string.dns_title),
                    subtitle = stringResource(
                        if (dnsLocked) R.string.settings_dns_summary_locked else R.string.settings_dns_summary_open,
                        phoneDns
                    ),
                    onClick = onOpenDns
                )
            }
            item { ConsumerLineDivider() }
            item {
                ConsumerRow(
                    title = stringResource(R.string.settings_device_entry),
                    subtitle = stringResource(
                        if (uninstallLocked) R.string.unlock_status_locked else R.string.unlock_status_open
                    ),
                    onClick = onOpenDevice
                )
            }
            item { Spacer(Modifier.padding(bottom = 32.dp)) }
        }
    }
}

@Composable
fun SettingsSecurityHubScreen(
    onOpenChangePin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            ConsumerTopBar(
                title = stringResource(R.string.settings_security_hub_title),
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Text(
                    stringResource(R.string.settings_security_hub_lead),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
            item { ConsumerSection(stringResource(R.string.settings_security_codes_section)) }
            item {
                ConsumerRow(
                    title = stringResource(R.string.change_pin),
                    subtitle = stringResource(R.string.settings_change_pin_sub),
                    onClick = onOpenChangePin
                )
            }
            item { ConsumerLineDivider() }
            item {
                ConsumerRow(
                    title = stringResource(R.string.settings_recovery_info_title),
                    subtitle = stringResource(R.string.settings_recovery_info_sub),
                    onClick = null
                )
            }
            item { ConsumerSection(stringResource(R.string.settings_security_behavior_section)) }
            item {
                ConsumerRow(
                    title = stringResource(R.string.settings_lock_after_exit_title),
                    subtitle = stringResource(R.string.settings_lock_after_exit_sub),
                    onClick = null
                )
            }
            item { Spacer(Modifier.padding(bottom = 32.dp)) }
        }
    }
}

@Composable
fun SettingsChangePinScreen(
    prefs: PrefsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var currentPin by rememberSaveable { mutableStateOf("") }
    var newPin by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }
    var shortCode by rememberSaveable { mutableStateOf("") }
    var confirmShortCode by rememberSaveable { mutableStateOf("") }
    var shortLoginEnabled by remember { mutableStateOf(prefs.shortCodeLoginEnabled) }

    Scaffold(
        modifier = modifier,
        topBar = {
            ConsumerTopBar(
                title = stringResource(R.string.change_pin),
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                ConsumerFormBlock {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { currentPin = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.current_pin)) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        label = { Text(stringResource(R.string.new_pin)) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { confirmPin = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        label = { Text(stringResource(R.string.confirm_new_pin)) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    ConsumerPrimaryButton(
                        text = stringResource(R.string.save_pin),
                        onClick = {
                            if (!prefs.verifyPin(currentPin)) {
                                Toast.makeText(context, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
                                return@ConsumerPrimaryButton
                            }
                            if (newPin.length < 4) {
                                Toast.makeText(context, R.string.pin_too_short, Toast.LENGTH_SHORT).show()
                                return@ConsumerPrimaryButton
                            }
                            if (newPin != confirmPin) {
                                Toast.makeText(context, R.string.pin_mismatch, Toast.LENGTH_SHORT).show()
                                return@ConsumerPrimaryButton
                            }
                            prefs.setPin(newPin)
                            currentPin = ""
                            newPin = ""
                            confirmPin = ""
                            Toast.makeText(context, R.string.pin_changed, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            item { ConsumerSection(stringResource(R.string.settings_short_code_section)) }
            item {
                Text(
                    stringResource(R.string.settings_short_code_lead),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            item {
                ConsumerRow(
                    title = stringResource(R.string.settings_short_code_enable),
                    subtitle = stringResource(
                        if (prefs.hasShortCode()) R.string.settings_short_code_enable_sub_on
                        else R.string.settings_short_code_enable_sub_off
                    ),
                    trailing = {
                        Switch(
                            checked = shortLoginEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !prefs.hasShortCode()) {
                                    Toast.makeText(
                                        context,
                                        R.string.settings_short_code_set_first,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@Switch
                                }
                                shortLoginEnabled = enabled
                                prefs.shortCodeLoginEnabled = enabled
                            }
                        )
                    }
                )
            }
            item { ConsumerLineDivider() }
            item {
                ConsumerFormBlock(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = shortCode,
                        onValueChange = { shortCode = it.filter { ch -> ch.isDigit() }.take(PrefsRepository.MAX_SHORT_CODE_LENGTH) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_short_code_new)) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = confirmShortCode,
                        onValueChange = {
                            confirmShortCode = it.filter { ch -> ch.isDigit() }.take(PrefsRepository.MAX_SHORT_CODE_LENGTH)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        label = { Text(stringResource(R.string.settings_short_code_confirm)) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    ConsumerPrimaryButton(
                        text = stringResource(R.string.settings_short_code_save),
                        onClick = {
                            if (!prefs.verifyPin(currentPin)) {
                                Toast.makeText(context, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
                                return@ConsumerPrimaryButton
                            }
                            if (shortCode.length !in PrefsRepository.MIN_SHORT_CODE_LENGTH..PrefsRepository.MAX_SHORT_CODE_LENGTH) {
                                Toast.makeText(context, R.string.settings_short_code_invalid, Toast.LENGTH_SHORT).show()
                                return@ConsumerPrimaryButton
                            }
                            if (shortCode != confirmShortCode) {
                                Toast.makeText(context, R.string.pin_mismatch, Toast.LENGTH_SHORT).show()
                                return@ConsumerPrimaryButton
                            }
                            prefs.setShortCode(shortCode)
                            shortLoginEnabled = true
                            prefs.shortCodeLoginEnabled = true
                            shortCode = ""
                            confirmShortCode = ""
                            Toast.makeText(context, R.string.settings_short_code_saved, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    if (prefs.hasShortCode()) {
                        TextButton(
                            onClick = {
                                if (!prefs.verifyPin(currentPin)) {
                                    Toast.makeText(context, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
                                    return@TextButton
                                }
                                prefs.clearShortCode()
                                prefs.devTempShortCodeOneActive = false
                                shortLoginEnabled = false
                                Toast.makeText(context, R.string.settings_short_code_removed, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(stringResource(R.string.settings_short_code_remove))
                        }
                    }
                }
            }
            item { Spacer(Modifier.padding(bottom = 32.dp)) }
        }
    }
}

@Composable
fun SettingsAllowlistPolicyScreen(
    prefs: PrefsRepository,
    refreshTick: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var rememberReinstall by remember(refreshTick) {
        mutableStateOf(prefs.rememberApprovalAfterReinstall)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            ConsumerTopBar(
                title = stringResource(R.string.settings_reinstall_approval_title),
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Text(
                    stringResource(R.string.settings_reinstall_approval_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
            item {
                ConsumerRow(
                    title = stringResource(R.string.settings_reinstall_approval_switch),
                    trailing = {
                        Switch(
                            checked = rememberReinstall,
                            onCheckedChange = {
                                rememberReinstall = it
                                prefs.rememberApprovalAfterReinstall = it
                            }
                        )
                    }
                )
            }
            item { Spacer(Modifier.padding(bottom = 32.dp)) }
        }
    }
}

@Composable
fun SettingsDnsScreen(
    prefs: PrefsRepository,
    refreshTick: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var dnsMode by remember(refreshTick) { mutableStateOf(prefs.dnsMode) }
    var dnsHost by remember(refreshTick) { mutableStateOf(prefs.dnsHost) }
    val phoneDns = remember(refreshTick) { PrivateDns.read(context) }
    val canWriteDns = remember(refreshTick) { PrivateDns.canWrite(context) }

    LaunchedEffect(refreshTick) { PrivateDnsEnforcer.sync(context) }

    Scaffold(
        modifier = modifier,
        topBar = {
            ConsumerTopBar(
                title = stringResource(R.string.dns_title),
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.dns_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.dns_phone_now, PrivateDns.describe(context, phoneDns)),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        stringResource(if (prefs.dnsLockEnabled) R.string.dns_lock_on else R.string.dns_lock_off),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        stringResource(if (canWriteDns) R.string.dns_perm_ok else R.string.dns_perm_missing),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (!canWriteDns) {
                        SelectionContainer {
                            Text(
                                PrivateDns.adbGrant(context),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val clip = context.getSystemService(ClipboardManager::class.java)
                                clip?.setPrimaryClip(
                                    ClipData.newPlainText("adb", PrivateDns.adbGrant(context))
                                )
                                Toast.makeText(context, R.string.dns_adb_copied, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text(stringResource(R.string.dns_copy_adb)) }
                    }
                    DnsModeRow(R.string.dns_mode_off, dnsMode == PrivateDns.MODE_OFF) {
                        dnsMode = PrivateDns.MODE_OFF
                    }
                    DnsModeRow(R.string.dns_mode_auto, dnsMode == PrivateDns.MODE_OPPORTUNISTIC) {
                        dnsMode = PrivateDns.MODE_OPPORTUNISTIC
                    }
                    DnsModeRow(R.string.dns_mode_host, dnsMode == PrivateDns.MODE_HOSTNAME) {
                        dnsMode = PrivateDns.MODE_HOSTNAME
                    }
                    if (dnsMode == PrivateDns.MODE_HOSTNAME) {
                        OutlinedTextField(
                            value = dnsHost,
                            onValueChange = { dnsHost = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            label = { Text(stringResource(R.string.dns_host_hint)) }
                        )
                    }
                    androidx.compose.foundation.layout.Row(Modifier.padding(top = 8.dp)) {
                        listOf(
                            R.string.dns_preset_cloudflare to PrivateDns.HOST_CLOUDFLARE,
                            R.string.dns_preset_google to PrivateDns.HOST_GOOGLE,
                            R.string.dns_preset_quad9 to PrivateDns.HOST_QUAD9,
                            R.string.dns_preset_adguard to PrivateDns.HOST_ADGUARD
                        ).forEach { (label, host) ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    dnsMode = PrivateDns.MODE_HOSTNAME
                                    dnsHost = host
                                },
                                label = { Text(stringResource(label)) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                    ConsumerPrimaryButton(
                        text = stringResource(R.string.dns_apply),
                        onClick = {
                            val result = PrivateDnsEnforcer.lock(context, dnsMode, dnsHost)
                            val msg = when (result) {
                                PrivateDns.ApplyResult.OK -> R.string.dns_applied
                                PrivateDns.ApplyResult.NO_PERMISSION -> R.string.dns_saved_no_write
                                PrivateDns.ApplyResult.BAD_HOSTNAME -> R.string.dns_bad_host
                                PrivateDns.ApplyResult.WRITE_FAILED -> R.string.dns_write_failed
                                PrivateDns.ApplyResult.UNSUPPORTED -> R.string.dns_unsupported
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    if (prefs.dnsLockEnabled) {
                        OutlinedButton(
                            onClick = {
                                val result = PrivateDnsEnforcer.unlock(context)
                                val msg = when (result) {
                                    PrivateDns.ApplyResult.OK -> R.string.dns_unlocked
                                    PrivateDns.ApplyResult.NO_PERMISSION -> R.string.dns_unlock_no_write
                                    PrivateDns.ApplyResult.WRITE_FAILED -> R.string.dns_write_failed
                                    PrivateDns.ApplyResult.UNSUPPORTED -> R.string.dns_unsupported
                                    PrivateDns.ApplyResult.BAD_HOSTNAME -> R.string.dns_bad_host
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text(stringResource(R.string.dns_unlock)) }
                    }
                }
            }
            item { Spacer(Modifier.padding(bottom = 32.dp)) }
        }
    }
}

@Composable
fun SettingsDeviceScreen(
    prefs: PrefsRepository,
    refreshTick: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showUnlockDialog by remember { mutableStateOf(false) }
    var unlockPin by remember { mutableStateOf("") }
    val unlocked = remember(refreshTick) {
        prefs.hasValidRemovalLease() || !DeviceAdminHelper.isAdminActive(context)
    }

    if (showUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showUnlockDialog = false },
            title = { Text(stringResource(R.string.unlock_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.unlock_confirm_message))
                    OutlinedTextField(
                        value = unlockPin,
                        onValueChange = { unlockPin = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text(stringResource(R.string.pin_enter_hint)) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!prefs.verifyPin(unlockPin) && !prefs.verifyRecoveryCode(unlockPin)) {
                        Toast.makeText(context, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    ProtectionController.onPinSuccess(context, TamperReason.MANUAL_UNLOCK)
                    showUnlockDialog = false
                    unlockPin = ""
                    Toast.makeText(context, R.string.challenge_ok_lease, Toast.LENGTH_LONG).show()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            ConsumerTopBar(
                title = stringResource(R.string.settings_device_entry),
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.unlock_uninstall_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.unlock_uninstall_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        stringResource(if (unlocked) R.string.unlock_status_open else R.string.unlock_status_locked),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    ConsumerPrimaryButton(
                        text = stringResource(R.string.unlock_uninstall),
                        onClick = { showUnlockDialog = true },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    if (unlocked) {
                        OutlinedButton(
                            onClick = {
                                ProtectionController.clearLease(context)
                                if (!DeviceAdminHelper.isAdminActive(context)) {
                                    DeviceAdminHelper.requestEnable(context)
                                }
                                Toast.makeText(context, R.string.device_admin_on, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text(stringResource(R.string.relock_uninstall)) }
                    }
                }
            }
            item { Spacer(Modifier.padding(bottom = 32.dp)) }
        }
    }
}

@Composable
fun SettingsThemeScreen(
    prefs: PrefsRepository,
    onBack: () -> Unit,
    onThemeChanged: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(prefs.appThemeMode) }

    Scaffold(
        modifier = modifier,
        topBar = {
            ConsumerTopBar(
                title = stringResource(R.string.settings_theme_title),
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Text(
                    stringResource(R.string.settings_theme_lead),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
            item {
                ThemeModeRow(
                    title = stringResource(R.string.settings_theme_light),
                    subtitle = stringResource(R.string.settings_theme_light_sub),
                    selected = selected == AppThemeMode.LIGHT,
                    onSelect = {
                        selected = AppThemeMode.LIGHT
                        prefs.appThemeMode = AppThemeMode.LIGHT
                        onThemeChanged(AppThemeMode.LIGHT)
                    }
                )
            }
            item { ConsumerLineDivider() }
            item {
                ThemeModeRow(
                    title = stringResource(R.string.settings_theme_dark),
                    subtitle = stringResource(R.string.settings_theme_dark_sub),
                    selected = selected == AppThemeMode.DARK,
                    onSelect = {
                        selected = AppThemeMode.DARK
                        prefs.appThemeMode = AppThemeMode.DARK
                        onThemeChanged(AppThemeMode.DARK)
                    }
                )
            }
            item { ConsumerLineDivider() }
            item {
                ThemeModeRow(
                    title = stringResource(R.string.settings_theme_amoled),
                    subtitle = stringResource(R.string.settings_theme_amoled_sub),
                    selected = selected == AppThemeMode.AMOLED,
                    onSelect = {
                        selected = AppThemeMode.AMOLED
                        prefs.appThemeMode = AppThemeMode.AMOLED
                        onThemeChanged(AppThemeMode.AMOLED)
                    }
                )
            }
            item { Spacer(Modifier.padding(bottom = 32.dp)) }
        }
    }
}

@Composable
private fun ThemeModeRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    ConsumerRow(
        title = title,
        subtitle = subtitle,
        onClick = onSelect,
        trailing = { RadioButton(selected = selected, onClick = onSelect) }
    )
}

@Composable
private fun DnsModeRow(labelRes: Int, selected: Boolean, onSelect: () -> Unit) {
    ConsumerRow(
        title = stringResource(labelRes),
        onClick = onSelect,
        trailing = { RadioButton(selected = selected, onClick = onSelect) }
    )
}
