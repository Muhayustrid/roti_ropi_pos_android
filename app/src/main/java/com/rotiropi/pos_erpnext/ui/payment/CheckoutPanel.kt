package com.rotiropi.pos_erpnext.ui.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

@Composable
fun CheckoutPanel(
    state: CheckoutUiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onOpenCheckout: () -> Unit = {},
    canReviewCheckout: Boolean = false,
    onUpdatePaymentAmount: (String, String) -> Unit = { _, _ -> },
    onSubmit: () -> Unit = {},
) {
    val message = when (state) {
        CheckoutUiState.Unavailable -> "Authoritative payable and payment modes unavailable"
        is CheckoutUiState.Ready -> "Payable: ${state.quote.currency} ${state.quote.payable}"
        is CheckoutUiState.PaymentInvalid -> state.message
        CheckoutUiState.OfflineNotSubmitted -> "Offline — sale not submitted"
        is CheckoutUiState.PriceChanged -> listOf(state.message, state.details.entries.joinToString("\n") { "${it.key}: ${it.value}" }.takeIf { state.details.isNotEmpty() }).filterNotNull().joinToString("\n")
        CheckoutUiState.Submitting -> "Submitting sale"
        is CheckoutUiState.Error -> state.message
    }
    val announced = state is CheckoutUiState.PriceChanged || state is CheckoutUiState.Error || state is CheckoutUiState.PaymentInvalid
    val retryable = state is CheckoutUiState.PriceChanged || state is CheckoutUiState.Error

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Checkout",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        val rows = when (state) {
            is CheckoutUiState.Ready -> state.payments
            is CheckoutUiState.PaymentInvalid -> state.payments
            else -> emptyList()
        }
        rows.forEach { payment ->
                OutlinedTextField(
                    value = payment.amount,
                    onValueChange = { onUpdatePaymentAmount(payment.modeOfPayment, it) },
                    label = { Text(if (payment.isDefault) "${payment.modeOfPayment} (default)" else payment.modeOfPayment) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("payment-${payment.modeOfPayment}"),
                )
            }
        if (state == CheckoutUiState.Submitting) {
            CircularProgressIndicator(modifier = Modifier.testTag("checkout-progress"))
        }
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics {
                if (announced) liveRegion = LiveRegionMode.Assertive
            },
        )
        if (retryable) {
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = PosDimensions.touchTarget),
            ) {
                Text("Retry")
            }
        }
        if (state == CheckoutUiState.Unavailable && canReviewCheckout) Button(
            onClick = onOpenCheckout,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PosDimensions.touchTarget)
                .testTag("checkout-review"),
        ) {
            Text("Review checkout")
        }
        Button(
            onClick = onSubmit,
            enabled = state is CheckoutUiState.Ready && state.validation is PaymentValidationResult.Valid,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PosDimensions.touchTarget)
                .testTag("checkout-confirm"),
        ) {
            Text("Confirm exact payment")
        }
    }
}
