package com.rotiropi.pos_erpnext.ui.opening

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.recovery.RecoveryScreenState
import com.rotiropi.pos_erpnext.ui.recovery.RecoveryScreen
import com.rotiropi.pos_erpnext.ui.theme.WarmCommerceDimensions

/**
 * Opening Balance surface (Warm Commerce).
 *
 * The screen is presentation only: it renders [state], emits amount edits through
 * [onAmountChanged], and opens a confirmation sheet before the single durable Opening
 * mutation is requested via [onSubmit]. No Opening mutation is prepared or sent until
 * the cashier confirms inside the sheet.
 */
@Composable
fun OpeningScreen(
    state: OpeningUiState,
    onAmountChanged: (String, String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    recoveryState: RecoveryScreenState = RecoveryScreenState.Hidden,
    onAcknowledgeRecovery: () -> Unit = {},
    onReauthenticateRecovery: () -> Unit = {},
    onRecoverClosing: () -> Unit = {},
) {
    val fieldsEnabled = !state.submitting && !state.recoveryPending && !state.reconciling
    var confirmVisible by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WarmCommerceDimensions.screenMargin)
                    .testTag("opening-screen"),
                verticalArrangement = Arrangement.spacedBy(WarmCommerceDimensions.stackGap),
            ) {
                Text(
                    text = "Opening Balance",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Enter the starting amounts to begin today's shift.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.reconciling) {
                    OpeningReconcilingCard()
                    return@Column
                }

                OpeningSessionDetails(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opening-summary"),
                )

                RecoveryScreen(
                    state = recoveryState,
                    onAcknowledge = onAcknowledgeRecovery,
                    onReauthenticate = onReauthenticateRecovery,
                    onRecoverClosing = onRecoverClosing,
                )

                if (state.unavailable) {
                    Text(
                        state.error ?: "Opening configuration is unavailable.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .testTag("opening-unavailable")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    return@Column
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(WarmCommerceDimensions.gutter),
                ) {
                    items(state.rows, key = { it.modeOfPayment }) { row ->
                        OpeningAmountField(
                            value = row.input,
                            label = row.modeOfPayment,
                            enabled = row.editable && fieldsEnabled,
                            isError = row.error != null,
                            supportingText = row.error ?: if (!row.editable) "Set by server" else null,
                            editableStateDescription = if (!row.editable) "Set by server" else null,
                            onValueChange = { onAmountChanged(row.modeOfPayment, it) },
                        )
                    }
                }

                state.error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }

                Button(
                    onClick = { confirmVisible = true },
                    enabled = state.canSubmit && fieldsEnabled,
                    contentPadding = PaddingValues(horizontal = WarmCommerceDimensions.containerPadding),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = WarmCommerceDimensions.touchTarget)
                        .testTag("opening-submit"),
                ) {
                    Text(
                        if (state.submitting) "Opening…" else if (state.recoveryPending) "Recovery pending" else "Start Shift",
                    )
                }
            }
        }

        if (confirmVisible && !state.reconciling && !state.unavailable) {
            OpeningConfirmSheet(
                state = state,
                onDismiss = { if (!state.submitting) confirmVisible = false },
                onEditAmounts = { confirmVisible = false },
                onConfirm = onSubmit,
            )
        }
    }
}

/** Warm Commerce presentation of the Opening recovery / reconciliation state. */
@Composable
private fun OpeningReconcilingCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("opening-reconciling"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = WarmCommerceDimensions.elevationSurface,
    ) {
        Row(
            modifier = Modifier.padding(WarmCommerceDimensions.containerPadding),
            horizontalArrangement = Arrangement.spacedBy(WarmCommerceDimensions.stackGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Checking Shift Status",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "We're checking the current session before continuing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Read-only authoritative session details. The POS Profile is rendered as text only and
 * is never editable here; selection happens in the dedicated profile-selection flow.
 */
@Composable
private fun OpeningSessionDetails(state: OpeningUiState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = WarmCommerceDimensions.elevationSurface,
    ) {
        Column(
            modifier = Modifier.padding(WarmCommerceDimensions.containerPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Session Details",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OpeningDetailRow("Cashier", state.cashier)
            OpeningDetailRow("POS Profile", state.profileName)
            OpeningDetailRow("Company", state.company)
            OpeningDetailRow("Warehouse", state.warehouse)
            OpeningDetailRow("Currency", state.currency)
        }
    }
}

@Composable
private fun OpeningDetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WarmCommerceDimensions.gutter),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Confirm Opening bottom sheet. The first tap on the primary Opening action only opens
 * this sheet; [onConfirm] is invoked solely by the sheet's confirm action, so the
 * existing durable Opening submission remains the only mutation path.
 */
@Composable
internal fun OpeningConfirmSheet(
    state: OpeningUiState,
    onDismiss: () -> Unit,
    onEditAmounts: () -> Unit,
    onConfirm: () -> Unit,
) {
    val submitting = state.submitting
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("opening-confirm-modal"),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
        ) {
            // Scrim only; tapping it dismisses the confirmation sheet.
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(
                topStart = WarmCommerceDimensions.sheetCorner,
                topEnd = WarmCommerceDimensions.sheetCorner,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = WarmCommerceDimensions.elevationOverlap,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = WarmCommerceDimensions.containerPadding)
                    .padding(
                        top = WarmCommerceDimensions.containerPadding,
                        bottom = WarmCommerceDimensions.screenMargin,
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(WarmCommerceDimensions.stackGap),
                ) {
                    Text(
                        text = "Confirm Opening Balance",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Review the starting funds before starting this shift.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = "These opening amounts cannot be changed after the shift starts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(WarmCommerceDimensions.containerPadding),
                        )
                    }

                    OpeningDetailRow("POS Profile", state.profileName)
                    OpeningDetailRow("Cashier", state.cashier)

                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag("opening-confirm-rows"),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = WarmCommerceDimensions.elevationSurface,
                    ) {
                        Column(
                            modifier = Modifier.padding(WarmCommerceDimensions.containerPadding),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            state.rows.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = row.modeOfPayment,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = formatOpeningAmount(row.input),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }

                if (submitting) {
                    Spacer(Modifier.height(WarmCommerceDimensions.stackGap))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(WarmCommerceDimensions.stackGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "Starting Shift…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("opening-submitting"),
                        )
                    }
                }

                Spacer(Modifier.height(WarmCommerceDimensions.stackGap))
                Button(
                    onClick = onConfirm,
                    enabled = state.canSubmit && !submitting && !state.recoveryPending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = WarmCommerceDimensions.touchTarget)
                        .testTag("opening-confirm"),
                ) {
                    Text("Confirm & Start Shift")
                }
                Spacer(Modifier.height(WarmCommerceDimensions.gutter))
                OutlinedButton(
                    onClick = onEditAmounts,
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = WarmCommerceDimensions.touchTarget)
                        .testTag("opening-edit-amounts"),
                ) {
                    Text("Edit Amounts")
                }
            }
        }
    }
}
