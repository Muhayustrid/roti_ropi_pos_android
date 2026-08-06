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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
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
    onEditQuantity: (CartLine, String) -> Unit = { _, _ -> },
    onRemoveLine: (CartLine) -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenCheckout: () -> Unit = {},
    onUpdatePaymentAmount: (String, String) -> Unit = { _, _ -> },
    onSubmitPayment: () -> Unit = {},
    invalidQuantityForLine: String? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("cart-list"),
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
                CartLineCard(
                    line = line,
                    quantityInvalid = invalidQuantityForLine == line.id,
                    onDecreaseQuantity = onDecreaseQuantity,
                    onIncreaseQuantity = onIncreaseQuantity,
                    onEditQuantity = onEditQuantity,
                    onRemoveLine = onRemoveLine,
                )
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
                onOpenCheckout = onOpenCheckout,
                canReviewCheckout = cart.visibleLines.isNotEmpty(),
                onUpdatePaymentAmount = onUpdatePaymentAmount,
                onSubmit = onSubmitPayment,
            )
        }
    }
}

@Composable
private fun CartLineCard(
    line: CartLine,
    quantityInvalid: Boolean,
    onDecreaseQuantity: (CartLine) -> Unit,
    onIncreaseQuantity: (CartLine) -> Unit,
    onEditQuantity: (CartLine, String) -> Unit,
    onRemoveLine: (CartLine) -> Unit,
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
            line.batchNo?.let { Text("Batch $it", style = MaterialTheme.typography.bodySmall) }
            line.serialNo?.let { Text("Serial $it", style = MaterialTheme.typography.bodySmall) }
            line.warningLabel?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onDecreaseQuantity(line) },
                    enabled = line.serialNo == null,
                    modifier = Modifier
                        .heightIn(min = PosDimensions.touchTarget)
                        .testTag("cart-decrease-${line.serialNo ?: line.itemCode}"),
                ) {
                    Text("−")
                }
                if (line.serialNo == null) {
                    OutlinedTextField(
                        value = line.quantity,
                        onValueChange = { onEditQuantity(line, it) },
                        isError = quantityInvalid,
                        supportingText = if (quantityInvalid) {
                            { Text("Quantity is not valid") }
                        } else {
                            null
                        },
                        label = { Text(line.uom) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .heightIn(min = PosDimensions.touchTarget)
                            .testTag("cart-qty-${line.serialNo ?: line.itemCode}"),
                    )
                } else {
                    Text(
                        text = "${line.quantity} ${line.uom}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedButton(
                    onClick = { onIncreaseQuantity(line) },
                    enabled = line.serialNo == null,
                    modifier = Modifier
                        .heightIn(min = PosDimensions.touchTarget)
                        .testTag("cart-increase-${line.serialNo ?: line.itemCode}"),
                ) {
                    Text("+")
                }
                OutlinedButton(
                    onClick = { onRemoveLine(line) },
                    modifier = Modifier
                        .heightIn(min = PosDimensions.touchTarget)
                        .testTag("cart-remove-${line.serialNo ?: line.itemCode}"),
                ) {
                    Text("Remove")
                }
            }
        }
    }
}
