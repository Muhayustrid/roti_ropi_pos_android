package com.rotiropi.pos_erpnext.ui.cashier

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.ui.payment.CheckoutPanel
import com.rotiropi.pos_erpnext.ui.payment.CheckoutUiState
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

@Composable
fun CartContent(
    cart: CartSnapshot,
    checkoutState: CheckoutUiState,
    modifier: Modifier = Modifier,
    onDecreaseQuantity: (CartLine) -> Unit = {},
    onIncreaseQuantity: (CartLine) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(PosDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Cart",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (cart.visibleLines.isEmpty()) {
            item {
                Text(
                    text = "Cart is empty",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(cart.visibleLines, key = { it.id }) { line ->
                CartLineCard(line, onDecreaseQuantity, onIncreaseQuantity)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(cart.itemCountLabel, style = MaterialTheme.typography.labelLarge)
                Text(cart.payableLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            CheckoutPanel(
                state = checkoutState,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun CartLineCard(
    line: CartLine,
    onDecreaseQuantity: (CartLine) -> Unit,
    onIncreaseQuantity: (CartLine) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(line.itemName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${line.itemCode} · ${line.priceLabel}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onDecreaseQuantity(line) },
                    modifier = Modifier
                        .heightIn(min = PosDimensions.touchTarget)
                        .testTag("cart-decrease-${line.itemCode}"),
                ) {
                    Text("−")
                }
                Text("${line.quantity} ${line.uom}", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = { onIncreaseQuantity(line) },
                    modifier = Modifier
                        .heightIn(min = PosDimensions.touchTarget)
                        .testTag("cart-increase-${line.itemCode}"),
                ) {
                    Text("+")
                }
            }
        }
    }
}
