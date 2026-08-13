package com.rotiropi.pos_erpnext.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.recovery.RecoveryScreenState
import com.rotiropi.pos_erpnext.recovery.RecoveryTerminalResult
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

@Composable
fun RecoveryScreen(
    state: RecoveryScreenState,
    onAcknowledge: () -> Unit,
    onReauthenticate: () -> Unit = {},
    onRecoverClosing: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (state == RecoveryScreenState.Hidden) return
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recovery-card")
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.titleMedium)
            when (state) {
                RecoveryScreenState.Hidden -> Unit
                is RecoveryScreenState.AuthenticationRequired -> {
                    Text(
                        stringResource(R.string.recovery_sign_in_again_detail),
                        modifier = Modifier.testTag("recovery-authentication-required"),
                    )
                    Button(
                        onClick = onReauthenticate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = PosDimensions.touchTarget)
                            .testTag("recovery-reauthenticate"),
                    ) {
                        Text(stringResource(R.string.action_sign_in_again))
                    }
                }
                is RecoveryScreenState.RetrySchedulingFailed -> Text(
                    stringResource(R.string.recovery_retry_unscheduled),
                    modifier = Modifier.testTag("recovery-scheduling-failed"),
                )
                is RecoveryScreenState.ManualRecovery -> {
                    Text(state.message, modifier = Modifier.testTag("recovery-manual"))
                    if (state.canRecoverClosing) {
                        Button(
                            onClick = onRecoverClosing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = PosDimensions.touchTarget)
                                .testTag("recovery-closing"),
                        ) {
                            Text(stringResource(R.string.recovery_recover_closing))
                        }
                    }
                }
                is RecoveryScreenState.Terminal -> {
                    when (val result = state.result) {
                        is RecoveryTerminalResult.Completed -> {
                            Text(stringResource(R.string.recovery_completed, result.operation))
                            Text(stringResource(R.string.recovery_reference, result.reference))
                            Text(stringResource(R.string.recovery_status, result.status))
                            result.amount?.let { Text(stringResource(R.string.recovery_amount, it)) }
                        }
                        is RecoveryTerminalResult.Rejected -> {
                            Text(stringResource(R.string.recovery_action_rejected))
                            Text("${result.code}: ${result.message}")
                            result.reference?.let { Text(stringResource(R.string.recovery_reference, it)) }
                        }
                    }
                    Button(
                        onClick = onAcknowledge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = PosDimensions.touchTarget)
                            .testTag("recovery-acknowledge"),
                    ) {
                        Text(stringResource(R.string.recovery_acknowledge))
                    }
                }
            }
        }
    }
}
