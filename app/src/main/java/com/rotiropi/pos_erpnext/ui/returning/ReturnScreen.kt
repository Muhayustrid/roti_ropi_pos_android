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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.ui.receipt.ReceiptScreen
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

/**
 * `ReturnUiState.Editing.error` carries a stable code, not a sentence. Most codes are ours;
 * the ineligible-row case may instead carry a server `rejection_reason` verbatim, and an
 * unrecognized value falls through unchanged rather than being hidden.
 */
@Composable
private fun returnErrorMessage(code: String): String = when (code) {
    "row_ineligible" -> stringResource(R.string.return_error_row_ineligible)
    "serialized_return_not_supported" -> stringResource(R.string.return_unsupported_serial)
    "multiple_batch_return_not_supported" -> stringResource(R.string.return_unsupported_multi_batch)
    "reason_required" -> stringResource(R.string.return_error_reason_required)
    "selection_required" -> stringResource(R.string.return_error_selection_required)
    "refund_mode_required" -> stringResource(R.string.return_error_refund_mode_required)
    "refund_mode_not_allowed" -> stringResource(R.string.return_error_refund_mode_not_allowed)
    "quote_required" -> stringResource(R.string.return_error_quote_required)
    "stale_quote" -> stringResource(R.string.return_error_stale_quote)
    "submission_unavailable" -> stringResource(R.string.return_error_submission_unavailable)
    "rejected" -> stringResource(R.string.return_error_rejected)
    "return_limit_exceeded_refreshed" -> stringResource(R.string.return_error_limit_refreshed)
    "return_limit_exceeded" -> stringResource(R.string.return_error_limit_exceeded)
    "unsupported_quantity_policy" -> stringResource(R.string.return_error_unsupported_policy)
    "malformed_decimal" -> stringResource(R.string.return_error_malformed_decimal)
    "excessive_scale" -> stringResource(R.string.return_error_excessive_scale)
    "invalid_remaining_quantity" -> stringResource(R.string.return_error_invalid_remaining)
    "zero_or_negative_quantity" -> stringResource(R.string.return_error_zero_or_negative)
    "below_minimum_quantity" -> stringResource(R.string.return_error_below_minimum)
    "above_maximum_quantity" -> stringResource(R.string.return_error_above_maximum)
    else -> code
}

@Composable
fun ReturnScreen(state: ReturnUiState, onReasonChanged: (String) -> Unit, onQuantityChanged: (String, String) -> Unit, onRefundModeChanged: (String?) -> Unit, onQuote: () -> Unit, onSubmit: () -> Unit, onCloseReceipt: () -> Unit, modifier: Modifier = Modifier) = when (state) {
    ReturnUiState.Unavailable -> ReturnMessage(stringResource(R.string.return_unavailable), modifier)
    is ReturnUiState.Submitted -> ReturnMessage(stringResource(R.string.return_submitted), modifier)
    is ReturnUiState.Receipt -> ReceiptScreen(state.content, modifier, onCloseReceipt)
    is ReturnUiState.Editing -> LazyColumn(modifier.fillMaxSize().testTag("return-screen"), contentPadding = androidx.compose.foundation.layout.PaddingValues(PosDimensions.screenPadding), verticalArrangement = Arrangement.spacedBy(PosDimensions.sectionSpacing)) {
        item { Text(stringResource(R.string.return_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
        item { Text(stringResource(R.string.return_source, state.sourceName)) }
        items(state.rows, key = { it.original_row_id }) { row ->
            val selected = state.selections.firstOrNull { it.rowId == row.original_row_id }?.quantity.orEmpty()
            Column { Text(row.item_code, style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.return_row_quantities, row.original_qty, row.returned_qty, row.remaining_qty)); val unsupported = when { row.serial_numbers.isNotEmpty() -> stringResource(R.string.return_unsupported_serial); row.batch_numbers.size > 1 -> stringResource(R.string.return_unsupported_multi_batch); !row.eligible -> row.rejection_reason ?: stringResource(R.string.sale_detail_not_returnable); else -> null }; if (unsupported == null) OutlinedTextField(selected, { onQuantityChanged(row.original_row_id, it) }, label = { Text(stringResource(R.string.return_quantity_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("return-qty-${row.original_row_id}")) else Text(unsupported, color = MaterialTheme.colorScheme.error) }
        }
        item { OutlinedTextField(state.reason, onReasonChanged, label = { Text(stringResource(R.string.return_reason_label)) }, modifier = Modifier.fillMaxWidth().testTag("return-reason")) }
        if (state.refundModeRequired) item { Column { Text(stringResource(R.string.return_refund_mode)); state.allowedRefundModes.forEach { mode -> FilterChip(selected = state.refundMode == mode, onClick = { onRefundModeChanged(mode) }, label = { Text(mode) }, modifier = Modifier.heightIn(min = PosDimensions.touchTarget)) } } }
        if (state.error != null) item { Text(returnErrorMessage(state.error), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        if (state.quote != null) item { Column { Text(stringResource(R.string.return_quote_title), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.return_quote_amount, state.quote.refund_amount)); state.quote.refund_allocations.forEach { Text(stringResource(R.string.return_quote_allocation, it.mode_of_payment, it.amount)) } } }
        if (state.loadingQuote) item { CircularProgressIndicator() }
        item { Button(onQuote, enabled = !state.loadingQuote && !state.submitting, modifier = Modifier.fillMaxWidth().heightIn(min = PosDimensions.touchTarget).testTag("return-quote")) { Text(stringResource(R.string.return_get_quote)) } }
        item { Button(onSubmit, enabled = state.quote != null && !state.loadingQuote && !state.submitting, modifier = Modifier.fillMaxWidth().heightIn(min = PosDimensions.touchTarget).testTag("return-submit")) { Text(stringResource(R.string.return_submit)) } }
    }
}

@Composable private fun ReturnMessage(value: String, modifier: Modifier) = Column(modifier.fillMaxSize().padding(PosDimensions.screenPadding)) { Text(value) }
