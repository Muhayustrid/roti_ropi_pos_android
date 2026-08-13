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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.ui.resolve
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
        CheckoutUiState.Unavailable -> stringResource(R.string.checkout_unavailable)
        is CheckoutUiState.Ready -> stringResource(R.string.checkout_payable, state.quote.currency, state.quote.payable)
        is CheckoutUiState.PaymentInvalid -> state.message.resolve()
        CheckoutUiState.OfflineNotSubmitted -> stringResource(R.string.checkout_offline)
        is CheckoutUiState.PriceChanged -> listOfNotNull(
            state.message.resolve(),
            state.details.entries.joinToString("\n") { "${it.key}: ${it.value}" }.takeIf { state.details.isNotEmpty() },
        ).joinToString("\n")
        CheckoutUiState.Submitting -> stringResource(R.string.checkout_submitting)
        is CheckoutUiState.Error -> state.message.resolve()
    }
    val announced = state is CheckoutUiState.PriceChanged || state is CheckoutUiState.Error || state is CheckoutUiState.PaymentInvalid
    val retryable = state is CheckoutUiState.PriceChanged || state is CheckoutUiState.Error

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.checkout_title),
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
                    label = {
                        Text(
                            if (payment.isDefault) {
                                stringResource(R.string.checkout_default_mode, payment.modeOfPayment)
                            } else {
                                payment.modeOfPayment
                            },
                        )
                    },
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
                Text(stringResource(R.string.action_retry))
            }
        }
        if (state == CheckoutUiState.Unavailable && canReviewCheckout) Button(
            onClick = onOpenCheckout,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PosDimensions.touchTarget)
                .testTag("checkout-review"),
        ) {
            Text(stringResource(R.string.checkout_review))
        }
        Button(
            onClick = onSubmit,
            enabled = state is CheckoutUiState.Ready && state.validation is PaymentValidationResult.Valid,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PosDimensions.touchTarget)
                .testTag("checkout-confirm"),
        ) {
            Text(stringResource(R.string.checkout_confirm))
        }
    }
}
