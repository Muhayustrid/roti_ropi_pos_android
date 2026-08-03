package com.rotiropi.pos_erpnext.ui.opening

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.ui.recovery.RecoveryScreen
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

@Composable
fun OpeningScreen(
    state: OpeningUiState,
    onAmountChanged: (String, String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    recoveryState: com.rotiropi.pos_erpnext.recovery.RecoveryScreenState = com.rotiropi.pos_erpnext.recovery.RecoveryScreenState.Hidden,
    onAcknowledgeRecovery: () -> Unit = {},
    onReauthenticateRecovery: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("opening-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Open POS session", style = MaterialTheme.typography.headlineSmall)
        state.profileName?.let { Text(it) }
        state.currency?.let { Text("Opening amounts ($it)") }
        RecoveryScreen(
            state = recoveryState,
            onAcknowledge = onAcknowledgeRecovery,
            onReauthenticate = onReauthenticateRecovery,
        )
        if (state.unavailable) {
            Text(
                state.error ?: "Opening configuration is unavailable.",
                modifier = Modifier
                    .testTag("opening-unavailable")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.rows, key = { it.modeOfPayment }) { row ->
                OutlinedTextField(
                    value = row.input,
                    onValueChange = { onAmountChanged(row.modeOfPayment, it) },
                    label = { Text(row.modeOfPayment) },
                    enabled = row.editable && !state.submitting && !state.recoveryPending,
                    isError = row.error != null,
                    supportingText = row.error?.let { message -> { Text(message) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opening-amount-${row.modeOfPayment}"),
                    singleLine = true,
                )
            }
        }
        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Button(
            onClick = onSubmit,
            enabled = state.canSubmit && !state.submitting && !state.recoveryPending,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PosDimensions.touchTarget)
                .testTag("opening-submit"),
        ) {
            Text(if (state.submitting) "Opening…" else if (state.recoveryPending) "Recovery pending" else "Open session")
        }
    }
}
