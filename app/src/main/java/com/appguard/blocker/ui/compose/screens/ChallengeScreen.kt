package com.appguard.blocker.ui.compose.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appguard.blocker.R
import com.appguard.blocker.ui.compose.ui.ConsumerFormBlock
import com.appguard.blocker.ui.compose.ui.ConsumerPrimaryButton

@Composable
fun ChallengeScreen(
    titleRes: Int,
    messageRes: Int,
    onSubmit: (pin: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pin by remember { mutableStateOf("") }
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            stringResource(messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        ConsumerFormBlock(modifier = Modifier.padding(top = 20.dp)) {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.pin_enter_hint)) },
                visualTransformation = PasswordVisualTransformation()
            )
        }
        ConsumerPrimaryButton(
            text = stringResource(R.string.challenge_continue),
            onClick = { onSubmit(pin) },
            modifier = Modifier.padding(top = 16.dp)
        )
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        Spacer(Modifier.height(16.dp))
    }
}
