package com.rotiropi.pos_erpnext.ui.receipt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

@Composable
fun ReceiptScreen(
    content: ReceiptContent,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(PosDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.receipt_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(content.saleId, style = MaterialTheme.typography.titleMedium)
                content.sourceReference?.let { Text(stringResource(R.string.receipt_return_against, it)) }
            }
        }
        item { ReceiptRow(stringResource(R.string.receipt_row_customer), content.customerLabel) }
        item { ReceiptRow(stringResource(R.string.receipt_row_total), content.total) }
        item { ReceiptRow(stringResource(R.string.receipt_row_paid), content.paid) }
        item { ReceiptRow(stringResource(R.string.receipt_row_change), content.changeAmount) }
        item { ReceiptRow(stringResource(R.string.receipt_row_status), stringResource(content.status)) }
        items(content.items.size) { Text(content.items[it].render()) }
        items(content.taxes.size) { Text(content.taxes[it]) }
        items(content.payments.size) { Text(content.payments[it]) }
        item {
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PosDimensions.touchTarget)
                    .testTag("receipt-close"),
            ) {
                Text(stringResource(R.string.receipt_close))
            }
        }
    }
}

/**
 * Only the batch and serial prefixes are this app's words; the summary and the numbers
 * themselves are server-owned and pass through verbatim.
 */
@Composable
private fun ReceiptItemLine.render(): String = listOfNotNull(
    summary,
    batches?.let { stringResource(R.string.sale_detail_batches, it) },
    serials?.let { stringResource(R.string.sale_detail_serials, it) },
).joinToString(" · ")

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
