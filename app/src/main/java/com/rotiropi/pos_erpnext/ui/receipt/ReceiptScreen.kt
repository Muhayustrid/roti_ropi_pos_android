package com.rotiropi.pos_erpnext.ui.receipt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Receipt",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(content.saleId, style = MaterialTheme.typography.titleMedium)
                }
                if (content.demoData) DemoBadge()
            }
        }
        item { ReceiptRow("Customer", content.customerLabel) }
        item { ReceiptRow("Total", content.total) }
        item { ReceiptRow("Paid", content.paid) }
        item { ReceiptRow("Server change", content.changeAmount) }
        item { ReceiptRow("Status", content.status) }
        item {
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PosDimensions.touchTarget)
                    .testTag("receipt-close"),
            ) {
                Text("Close receipt")
            }
        }
    }
}

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

@Composable
private fun DemoBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = "Demo data",
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
