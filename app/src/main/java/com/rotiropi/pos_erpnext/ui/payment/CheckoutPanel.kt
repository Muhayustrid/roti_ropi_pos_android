package com.rotiropi.pos_erpnext.ui.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
) {
    val message = when (state) {
        CheckoutUiState.Unavailable -> "Authoritative payable and payment modes unavailable"
        CheckoutUiState.OfflineNotSubmitted -> "Offline — sale not submitted"
        is CheckoutUiState.PriceChanged -> state.message
        CheckoutUiState.Submitting -> "Submitting sale"
        is CheckoutUiState.Error -> state.message
    }
    val announced = state is CheckoutUiState.PriceChanged || state is CheckoutUiState.Error
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
        Button(
            onClick = {},
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PosDimensions.touchTarget)
                .testTag("checkout-confirm"),
        ) {
            Text("Confirm exact payment")
        }
    }
}
