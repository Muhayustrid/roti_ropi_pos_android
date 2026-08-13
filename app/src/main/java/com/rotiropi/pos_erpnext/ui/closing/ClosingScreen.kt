package com.rotiropi.pos_erpnext.ui.closing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.data.ClosingReceipt
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

/**
 * `ClosingUiState` carries a stable failure code, not a sentence: some codes are ours and
 * some arrive verbatim from the server. Known codes resolve to a translated string here at
 * the UI edge; an unrecognized server code is shown as-is rather than hidden.
 */
@Composable
private fun closingErrorMessage(code: String): String = when (code) {
    "payment_mode_unknown" -> stringResource(R.string.closing_error_payment_mode_unknown)
    "counted_amount_required" -> stringResource(R.string.closing_error_counted_amount_required)
    "rejected" -> stringResource(R.string.closing_error_rejected)
    "submission_unavailable" -> stringResource(R.string.closing_error_submission_unavailable)
    "AUTH_REQUIRED" -> stringResource(R.string.closing_error_auth_required)
    else -> code.replace('_', ' ')
}

@Composable
fun ClosingScreen(
    state: ClosingUiState,
    onCountedAmountChanged: (String, String) -> Unit = { _, _ -> },
    onSubmit: () -> Unit = {},
    onCheckStatus: () -> Unit = {},
    onReauthenticate: () -> Unit = {},
    onRetryPreview: (String) -> Unit = {},
    onDone: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("closing-screen")
            .padding(PosDimensions.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.closing_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (onBack != null) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier
                        .heightIn(min = PosDimensions.touchTarget)
                        .testTag("closing-back"),
                ) {
                    Text(stringResource(R.string.action_back))
                }
            }
        }
        when (state) {
            ClosingUiState.Unavailable -> Text(stringResource(R.string.closing_unavailable))
            is ClosingUiState.Loading -> Loading(stringResource(R.string.closing_loading_preview))
            is ClosingUiState.Editing -> Editing(
                state,
                onCountedAmountChanged,
                onSubmit,
            )
            is ClosingUiState.Recovering -> if (state.transactionId == "auth_required") {
                Button(
                    onClick = onReauthenticate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = PosDimensions.touchTarget)
                        .testTag("closing-reauthenticate"),
                ) {
                    Text(stringResource(R.string.action_sign_in_again))
                }
            } else {
                Loading(stringResource(R.string.closing_recovering))
            }
            is ClosingUiState.Queued -> Queued(state, onCheckStatus, onReauthenticate)
            is ClosingUiState.Receipt -> Terminal(
                title = stringResource(R.string.closing_submitted),
                receipt = state.receipt,
                onDone = onDone,
            )
            is ClosingUiState.Failed -> Terminal(
                title = stringResource(R.string.closing_receipt_title, state.receipt.status.name.lowercase()),
                receipt = state.receipt,
                onDone = onDone,
            )
            is ClosingUiState.StalePreview -> {
                Text(stringResource(R.string.closing_preview_stale))
                Button(
                    onClick = { onRetryPreview(state.posProfile) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = PosDimensions.touchTarget)
                        .testTag("closing-reload-preview"),
                ) {
                    Text(stringResource(R.string.closing_reload_preview))
                }
            }
        }
    }
}

@Composable
private fun Editing(
    state: ClosingUiState.Editing,
    onCountedAmountChanged: (String, String) -> Unit,
    onSubmit: () -> Unit,
) {
    val preview = state.preview
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.closing_preview_title), style = MaterialTheme.typography.titleMedium)
            AmountRow(stringResource(R.string.closing_row_invoices), preview.invoiceCount.toString())
            AmountRow(stringResource(R.string.closing_row_grand_total), preview.grandTotal)
            AmountRow(stringResource(R.string.closing_row_net_total), preview.netTotal)
            AmountRow(stringResource(R.string.closing_row_taxes), preview.totalTaxesAndCharges)
            AmountRow(stringResource(R.string.closing_row_quantity), preview.totalQuantity)
        }
    }
    preview.expectedPayments.forEach { payment ->
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(payment.modeOfPayment, style = MaterialTheme.typography.titleMedium)
                AmountRow(stringResource(R.string.closing_row_opening), payment.openingAmount)
                AmountRow(stringResource(R.string.closing_row_expected), payment.expectedAmount)
                OutlinedTextField(
                    value = state.countedAmounts[payment.modeOfPayment].orEmpty(),
                    onValueChange = { onCountedAmountChanged(payment.modeOfPayment, it) },
                    enabled = !state.submitting,
                    singleLine = true,
                    label = { Text(stringResource(R.string.closing_counted_label)) },
                    supportingText = {
                        Text(
                            stringResource(
                                R.string.closing_counted_policy,
                                preview.countedAmountPolicy.currency,
                                preview.countedAmountPolicy.maxScale,
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("closing-counted-${payment.modeOfPayment.lowercase().replace(' ', '-') }"),
                )
            }
        }
    }
    state.error?.let {
        Text(
            closingErrorMessage(it),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .testTag("closing-error")
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Button(
        onClick = onSubmit,
        enabled = !state.submitting && preview.binding.paymentModes.all {
            !state.countedAmounts[it].isNullOrBlank()
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PosDimensions.touchTarget)
            .testTag("closing-submit"),
    ) {
        Text(if (state.submitting) stringResource(R.string.closing_submitting) else stringResource(R.string.closing_submit))
    }
}

@Composable
private fun Queued(
    state: ClosingUiState.Queued,
    onCheckStatus: () -> Unit,
    onReauthenticate: () -> Unit,
) {
    Text(
        if (state.polling) {
            stringResource(R.string.closing_queued_polling)
        } else {
            stringResource(R.string.closing_queued)
        },
        modifier = Modifier
            .testTag("closing-queued")
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
    Text(stringResource(R.string.closing_reference, state.receipt.name))
    state.error?.let { Text(closingErrorMessage(it), color = MaterialTheme.colorScheme.error) }
    if (state.error == "AUTH_REQUIRED") {
        Button(
            onClick = onReauthenticate,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PosDimensions.touchTarget)
                .testTag("closing-reauthenticate"),
        ) {
            Text(stringResource(R.string.action_sign_in_again))
        }
    } else if (state.checkStatusAvailable) {
        Button(
            onClick = onCheckStatus,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PosDimensions.touchTarget)
                .testTag("closing-check-status"),
        ) {
            Text(stringResource(R.string.closing_check_status))
        }
    }
}

@Composable
private fun Terminal(title: String, receipt: ClosingReceipt, onDone: () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Text(stringResource(R.string.closing_reference, receipt.name))
    AmountRow(stringResource(R.string.closing_row_expected), receipt.reconciliation.expectedTotal)
    AmountRow(stringResource(R.string.closing_row_counted), receipt.reconciliation.countedTotal)
    AmountRow(stringResource(R.string.closing_row_difference), receipt.reconciliation.differenceTotal)
    receipt.failureCode?.let { Text("$it: ${receipt.failureMessage.orEmpty()}") }
    Button(
        onClick = onDone,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PosDimensions.touchTarget)
            .testTag("closing-done"),
    ) {
        Text(stringResource(R.string.action_done))
    }
}

@Composable
private fun Loading(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.testTag("closing-progress"))
        Text(text, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun AmountRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
