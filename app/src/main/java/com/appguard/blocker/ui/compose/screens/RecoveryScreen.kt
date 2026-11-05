package com.appguard.blocker.ui.compose.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.ui.MainActivity
import com.appguard.blocker.ui.OnboardingSteps
import com.appguard.blocker.ui.compose.components.OnboardingRail
import com.appguard.blocker.ui.compose.navigation.Routes
import com.appguard.blocker.ui.compose.ui.ConsumerFormBlock
import com.appguard.blocker.ui.compose.ui.ConsumerPrimaryButton
import com.appguard.blocker.util.PermissionHelper

@Composable
fun RecoveryScreen(
    prefs: PrefsRepository,
    code: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OnboardingRail(activeStep = OnboardingSteps.RECOVERY)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.recovery_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            stringResource(R.string.recovery_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        ConsumerFormBlock(modifier = Modifier.padding(top = 24.dp)) {
            Text(
                code,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
        Text(
            stringResource(R.string.recovery_screenshot),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp)
        )
        ConsumerPrimaryButton(
            text = stringResource(R.string.recovery_confirm),
            onClick = {
                prefs.recoveryShown = true
                Toast.makeText(context, R.string.recovery_saved_hint, Toast.LENGTH_LONG).show()
                val next = if (!prefs.setupCompleted || !PermissionHelper.criticalReady(context)) {
                    MainActivity.intent(context, Routes.WIZARD_SETUP)
                } else {
                    Intent(context, MainActivity::class.java)
                }
                context.startActivity(next)
                onDone()
            },
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}
