package com.rotiropi.pos_erpnext.ui.returning

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
import androidx.compose.material3.FilterChip
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
import com.rotiropi.pos_erpnext.ui.receipt.ReceiptScreen
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

@Composable
fun ReturnScreen(state: ReturnUiState, onReasonChanged: (String) -> Unit, onQuantityChanged: (String, String) -> Unit, onRefundModeChanged: (String?) -> Unit, onQuote: () -> Unit, onSubmit: () -> Unit, onCloseReceipt: () -> Unit, modifier: Modifier = Modifier) = when (state) {
    ReturnUiState.Unavailable -> ReturnMessage("Return unavailable", modifier)
    is ReturnUiState.Submitted -> ReturnMessage("Return submitted. Awaiting terminal result.", modifier)
    is ReturnUiState.Receipt -> ReceiptScreen(state.content, modifier, onCloseReceipt)
    is ReturnUiState.Editing -> LazyColumn(modifier.fillMaxSize().testTag("return-screen"), contentPadding = androidx.compose.foundation.layout.PaddingValues(PosDimensions.screenPadding), verticalArrangement = Arrangement.spacedBy(PosDimensions.sectionSpacing)) {
        item { Text("Return sale", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
        item { Text("Source: ${state.sourceName}") }
        items(state.rows, key = { it.original_row_id }) { row ->
            val selected = state.selections.firstOrNull { it.rowId == row.original_row_id }?.quantity.orEmpty()
            Column { Text(row.item_code, style = MaterialTheme.typography.titleMedium); Text("Original ${row.original_qty} · Returned ${row.returned_qty} · Remaining ${row.remaining_qty}"); val unsupported = when { row.serial_numbers.isNotEmpty() -> "Serialized return is not supported in this app."; row.batch_numbers.size > 1 -> "Multiple-batch return is not supported in this app."; !row.eligible -> row.rejection_reason ?: "Not returnable"; else -> null }; if (unsupported == null) OutlinedTextField(selected, { onQuantityChanged(row.original_row_id, it) }, label = { Text("Return quantity") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("return-qty-${row.original_row_id}")) else Text(unsupported, color = MaterialTheme.colorScheme.error) }
        }
        item { OutlinedTextField(state.reason, onReasonChanged, label = { Text("Return reason") }, modifier = Modifier.fillMaxWidth().testTag("return-reason")) }
        if (state.refundModeRequired) item { Column { Text("Refund mode"); state.allowedRefundModes.forEach { mode -> FilterChip(selected = state.refundMode == mode, onClick = { onRefundModeChanged(mode) }, label = { Text(mode) }, modifier = Modifier.heightIn(min = PosDimensions.touchTarget)) } } }
        if (state.error != null) item { Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        if (state.quote != null) item { Column { Text("Server refund quote", style = MaterialTheme.typography.titleMedium); Text("Refund: ${state.quote.refund_amount}"); state.quote.refund_allocations.forEach { Text("${it.mode_of_payment}: ${it.amount}") } } }
        if (state.loadingQuote) item { CircularProgressIndicator() }
        item { Button(onQuote, enabled = !state.loadingQuote && !state.submitting, modifier = Modifier.fillMaxWidth().heightIn(min = PosDimensions.touchTarget).testTag("return-quote")) { Text("Get server quote") } }
        item { Button(onSubmit, enabled = state.quote != null && !state.loadingQuote && !state.submitting, modifier = Modifier.fillMaxWidth().heightIn(min = PosDimensions.touchTarget).testTag("return-submit")) { Text("Submit return") } }
    }
}

@Composable private fun ReturnMessage(value: String, modifier: Modifier) = Column(modifier.fillMaxSize().padding(PosDimensions.screenPadding)) { Text(value) }
