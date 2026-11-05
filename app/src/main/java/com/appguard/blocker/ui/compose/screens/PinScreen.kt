package com.appguard.blocker.ui.compose.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.ui.MainActivity
import com.appguard.blocker.ui.OnboardingSteps
import com.appguard.blocker.ui.RecoveryActivity
import com.appguard.blocker.ui.compose.components.OnboardingRail
import com.appguard.blocker.ui.compose.navigation.Routes
import com.appguard.blocker.ui.compose.ui.ConsumerFormBlock
import com.appguard.blocker.ui.compose.ui.ConsumerPrimaryButton
import com.appguard.blocker.util.AuthSession
import com.appguard.blocker.util.PermissionHelper

@Composable
fun PinScreen(
    prefs: PrefsRepository,
    setupModeInitial: Boolean,
    versionName: String,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var setupMode by remember { mutableStateOf(setupModeInitial) }
    val shortLoginAvailable = !setupMode && prefs.shortCodeLoginEnabled && prefs.hasShortCode()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showRecovery by remember { mutableStateOf(false) }
    var recoveryCode by remember { mutableStateOf("") }

    if (showRecovery) {
        AlertDialog(
            onDismissRequest = { showRecovery = false },
            title = { Text(stringResource(R.string.recovery_use_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.recovery_use_message))
                    OutlinedTextField(
                        value = recoveryCode,
                        onValueChange = { recoveryCode = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        label = { Text(stringResource(R.string.recovery_enter)) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!prefs.verifyRecoveryCode(recoveryCode)) {
                        Toast.makeText(context, R.string.recovery_wrong, Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    Toast.makeText(context, R.string.recovery_ok_set_pin, Toast.LENGTH_LONG).show()
                    setupMode = true
                    showRecovery = false
                    pin = ""
                    confirm = ""
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRecovery = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 88.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (setupMode) OnboardingRail(activeStep = OnboardingSteps.PIN)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            stringResource(if (setupMode) R.string.pin_setup_title else R.string.pin_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            stringResource(
                when {
                    setupMode -> R.string.pin_setup_hint
                    shortLoginAvailable -> R.string.pin_enter_hint_short_code
                    else -> R.string.pin_enter_hint
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        ConsumerFormBlock(modifier = Modifier.padding(top = 24.dp)) {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        stringResource(
                            if (setupMode) R.string.pin_setup_hint else R.string.pin_enter_hint
                        )
                    )
                },
                visualTransformation = PasswordVisualTransformation()
            )
            if (setupMode) {
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text(stringResource(R.string.pin_confirm_hint)) },
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        }
        ConsumerPrimaryButton(
            text = stringResource(if (setupMode) R.string.pin_continue else R.string.pin_unlock),
            onClick = {
                if (setupMode) {
                    if (pin.length < 4) {
                        Toast.makeText(context, R.string.pin_too_short, Toast.LENGTH_SHORT).show()
                        return@ConsumerPrimaryButton
                    }
                    if (pin != confirm) {
                        Toast.makeText(context, R.string.pin_mismatch, Toast.LENGTH_SHORT).show()
                        return@ConsumerPrimaryButton
                    }
                    prefs.setPin(pin)
                    AuthSession.unlock()
                    if (!prefs.recoveryShown) {
                        val code = prefs.generateAndStoreRecoveryCode()
                        context.startActivity(
                            Intent(context, RecoveryActivity::class.java)
                                .putExtra(RecoveryActivity.EXTRA_CODE, code)
                        )
                        (context as? android.app.Activity)?.finish()
                    } else {
                        navigateAfterPinUnlock(context, prefs)
                        onUnlocked()
                    }
                    return@ConsumerPrimaryButton
                }
                if (!shortLoginAvailable && pin.length < 4) {
                    Toast.makeText(context, R.string.pin_too_short, Toast.LENGTH_SHORT).show()
                    return@ConsumerPrimaryButton
                }
                if (pin.isBlank()) {
                    Toast.makeText(context, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
                    return@ConsumerPrimaryButton
                }
                if (!prefs.verifyForLogin(pin)) {
                    Toast.makeText(context, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
                    pin = ""
                    return@ConsumerPrimaryButton
                }
                AuthSession.unlock()
                navigateAfterPinUnlock(context, prefs)
                onUnlocked()
            },
            modifier = Modifier.padding(top = 16.dp)
        )
        if (!setupMode) {
            TextButton(onClick = { showRecovery = true }) {
                Text(stringResource(R.string.recovery_forgot))
            }
        }
        Text(
            stringResource(R.string.app_version_label, versionName),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}

fun navigateAfterPinUnlock(context: android.content.Context, prefs: PrefsRepository) {
    val next = when {
        !prefs.setupCompleted || !PermissionHelper.criticalReady(context) ->
            MainActivity.intent(context, Routes.WIZARD_SETUP)
        else -> Intent(context, MainActivity::class.java)
    }
    next.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    context.startActivity(next)
}
