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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.data.ClosingReceipt
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

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
                "Closing",
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
                    Text("Back")
                }
            }
        }
        when (state) {
            ClosingUiState.Unavailable -> Text("Closing unavailable")
            is ClosingUiState.Loading -> Loading("Loading authoritative preview")
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
                    Text("Sign in again")
                }
            } else {
                Loading("Recovering Closing")
            }
            is ClosingUiState.Queued -> Queued(state, onCheckStatus, onReauthenticate)
            is ClosingUiState.Receipt -> Terminal(
                title = "Closing submitted",
                receipt = state.receipt,
                onDone = onDone,
            )
            is ClosingUiState.Failed -> Terminal(
                title = "Closing ${state.receipt.status.name.lowercase()}",
                receipt = state.receipt,
                onDone = onDone,
            )
            is ClosingUiState.StalePreview -> {
                Text("Closing preview changed. Load current totals before submitting again.")
                Button(
                    onClick = { onRetryPreview(state.posProfile) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = PosDimensions.touchTarget)
                        .testTag("closing-reload-preview"),
                ) {
                    Text("Reload preview")
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
            Text("Authoritative preview", style = MaterialTheme.typography.titleMedium)
            AmountRow("Invoices", preview.invoiceCount.toString())
            AmountRow("Grand total", preview.grandTotal)
            AmountRow("Net total", preview.netTotal)
            AmountRow("Taxes and charges", preview.totalTaxesAndCharges)
            AmountRow("Total quantity", preview.totalQuantity)
        }
    }
    preview.expectedPayments.forEach { payment ->
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(payment.modeOfPayment, style = MaterialTheme.typography.titleMedium)
                AmountRow("Opening", payment.openingAmount)
                AmountRow("Expected", payment.expectedAmount)
                OutlinedTextField(
                    value = state.countedAmounts[payment.modeOfPayment].orEmpty(),
                    onValueChange = { onCountedAmountChanged(payment.modeOfPayment, it) },
                    enabled = !state.submitting,
                    singleLine = true,
                    label = { Text("Counted amount") },
                    supportingText = { Text("${preview.countedAmountPolicy.currency}; max ${preview.countedAmountPolicy.maxScale} decimals") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("closing-counted-${payment.modeOfPayment.lowercase().replace(' ', '-') }"),
                )
            }
        }
    }
    state.error?.let {
        Text(
            it.replace('_', ' '),
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
        Text(if (state.submitting) "Submitting" else "Submit Closing")
    }
}

@Composable
private fun Queued(
    state: ClosingUiState.Queued,
    onCheckStatus: () -> Unit,
    onReauthenticate: () -> Unit,
) {
    Text(
        if (state.polling) "Closing queued. Checking authoritative status." else "Closing queued.",
        modifier = Modifier
            .testTag("closing-queued")
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
    Text("Reference: ${state.receipt.name}")
    state.error?.let { Text(it.replace('_', ' '), color = MaterialTheme.colorScheme.error) }
    if (state.error == "AUTH_REQUIRED") {
        Button(
            onClick = onReauthenticate,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PosDimensions.touchTarget)
                .testTag("closing-reauthenticate"),
        ) {
            Text("Sign in again")
        }
    } else if (state.checkStatusAvailable) {
        Button(
            onClick = onCheckStatus,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PosDimensions.touchTarget)
                .testTag("closing-check-status"),
        ) {
            Text("Check status")
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
    Text("Reference: ${receipt.name}")
    AmountRow("Expected", receipt.reconciliation.expectedTotal)
    AmountRow("Counted", receipt.reconciliation.countedTotal)
    AmountRow("Difference", receipt.reconciliation.differenceTotal)
    receipt.failureCode?.let { Text("$it: ${receipt.failureMessage.orEmpty()}") }
    Button(
        onClick = onDone,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PosDimensions.touchTarget)
            .testTag("closing-done"),
    ) {
        Text("Done")
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
