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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
            Text("Recovery", style = MaterialTheme.typography.titleMedium)
            when (state) {
                RecoveryScreenState.Hidden -> Unit
                is RecoveryScreenState.AuthenticationRequired -> {
                    Text(
                        "Sign in again to continue this recovery action.",
                        modifier = Modifier.testTag("recovery-authentication-required"),
                    )
                    Button(
                        onClick = onReauthenticate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = PosDimensions.touchTarget)
                            .testTag("recovery-reauthenticate"),
                    ) {
                        Text("Sign in again")
                    }
                }
                is RecoveryScreenState.RetrySchedulingFailed -> Text(
                    "Recovery retry could not be scheduled. Keep the app open and try again later.",
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
                            Text("Recover Closing")
                        }
                    }
                }
                is RecoveryScreenState.Terminal -> {
                    when (val result = state.result) {
                        is RecoveryTerminalResult.Completed -> {
                            Text("${result.operation} completed")
                            Text("Reference: ${result.reference}")
                            Text("Status: ${result.status}")
                            result.amount?.let { Text("Amount: $it") }
                        }
                        is RecoveryTerminalResult.Rejected -> {
                            Text("Action rejected")
                            Text("${result.code}: ${result.message}")
                            result.reference?.let { Text("Reference: $it") }
                        }
                    }
                    Button(
                        onClick = onAcknowledge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = PosDimensions.touchTarget)
                            .testTag("recovery-acknowledge"),
                    ) {
                        Text("Acknowledge result")
                    }
                }
            }
        }
    }
}
