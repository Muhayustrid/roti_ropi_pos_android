package com.rotiropi.pos_erpnext.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.rotiropi.pos_erpnext.data.api.SaleDetailDto
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

@Composable
fun SaleDetailScreen(state: SaleDetailUiState, onReturn: (SaleDetailDto) -> Unit, modifier: Modifier = Modifier) = when (state) {
    SaleDetailUiState.Unavailable -> DetailMessage("Sale unavailable", modifier)
    SaleDetailUiState.Loading -> Column(modifier.fillMaxSize().padding(PosDimensions.screenPadding)) { CircularProgressIndicator() }
    is SaleDetailUiState.Error -> DetailMessage(state.code, modifier)
    is SaleDetailUiState.Content -> {
        val sale = state.sale
        LazyColumn(modifier.fillMaxSize().testTag("sale-detail"), contentPadding = androidx.compose.foundation.layout.PaddingValues(PosDimensions.screenPadding), verticalArrangement = Arrangement.spacedBy(PosDimensions.sectionSpacing)) {
            item { Text("Sale detail", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
            item { Text(sale.summary.name) }
            item { Text("${sale.summary.currency} ${sale.summary.grand_total}") }
            items(sale.items, key = { it.row_id ?: it.item_code }) { item ->
                Column { Text(item.item_name, style = MaterialTheme.typography.titleMedium); Text("${item.qty} ${item.uom}"); item.returnability?.let { Text("Original ${it.original_qty} · Returned ${it.returned_qty} · Remaining ${it.remaining_qty}"); if (it.batch_numbers.isNotEmpty()) Text("Batch: ${it.batch_numbers.joinToString()}"); if (it.serial_numbers.isNotEmpty()) Text("Serial: ${it.serial_numbers.joinToString()}"); if (!it.eligible) Text(it.rejection_reason ?: "Not returnable", color = MaterialTheme.colorScheme.error) } }
            }
            val contract = sale.return_contract
            if (contract != null && sale.items.any { it.returnability?.eligible == true }) item { Button(onClick = { onReturn(sale) }, modifier = Modifier.fillMaxWidth().heightIn(min = PosDimensions.touchTarget).testTag("start-return")) { Text("Start return") } }
        }
    }
}

@Composable private fun DetailMessage(value: String, modifier: Modifier) = Column(modifier.fillMaxSize().padding(PosDimensions.screenPadding)) { Text(value) }
