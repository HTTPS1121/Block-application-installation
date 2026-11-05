package com.appguard.blocker.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appguard.blocker.R

@Composable
fun OnboardingRail(activeStep: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StepDot(1, stringResource(R.string.step_pin), activeStep)
        StepDot(2, stringResource(R.string.step_recovery), activeStep)
        StepDot(3, stringResource(R.string.step_permissions), activeStep)
        StepDot(4, stringResource(R.string.step_allowlist), activeStep)
    }
}

@Composable
private fun StepDot(index: Int, label: String, active: Int) {
    val done = index < active
    val current = index == active
    val bg = when {
        current -> MaterialTheme.colorScheme.primary
        done -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val fg = when {
        current -> MaterialTheme.colorScheme.onPrimary
        done -> MaterialTheme.colorScheme.onTertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (done) "✓" else index.toString(),
                color = fg,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
