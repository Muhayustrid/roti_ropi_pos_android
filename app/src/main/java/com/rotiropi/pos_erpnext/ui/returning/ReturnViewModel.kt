package com.rotiropi.pos_erpnext.ui.returning

import com.rotiropi.pos_erpnext.data.SaleReadResult
import com.rotiropi.pos_erpnext.data.api.ApiCallCancellation
import com.rotiropi.pos_erpnext.data.api.CreateReturnRequestDto
import com.rotiropi.pos_erpnext.data.api.QuoteReturnRequestDto
import com.rotiropi.pos_erpnext.data.api.ReturnItemInputDto
import com.rotiropi.pos_erpnext.data.api.ReturnQuoteDto
import com.rotiropi.pos_erpnext.data.api.ReturnabilityDto
import com.rotiropi.pos_erpnext.data.api.SaleDetailDto
import com.rotiropi.pos_erpnext.recovery.RecoveryExecution
import com.rotiropi.pos_erpnext.ui.payment.ReceiptMapper
import com.rotiropi.pos_erpnext.ui.receipt.ReceiptContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReturnSelection(val rowId: String, val quantity: String)

sealed interface ReturnUiState {
    data object Unavailable : ReturnUiState
    data class Editing(
        val sourceName: String,
        val rows: List<ReturnabilityDto>,
        val policy: ReturnQuantityPolicy,
        val allowedRefundModes: List<String>,
        val refundModeRequired: Boolean,
        val reason: String = "",
        val selections: List<ReturnSelection> = emptyList(),
        val refundMode: String? = null,
        val quote: ReturnQuoteDto? = null,
        val loadingQuote: Boolean = false,
        val submitting: Boolean = false,
        val error: String? = null,
    ) : ReturnUiState
    data class Submitted(val transactionId: String) : ReturnUiState
    data class Receipt(val content: ReceiptContent) : ReturnUiState
}

class ReturnViewModel(
    dispatcher: CoroutineDispatcher,
    private val quoteReturn: (QuoteReturnRequestDto, ApiCallCancellation) -> SaleReadResult<ReturnQuoteDto>,
    private val createReturn: (CreateReturnRequestDto) -> RecoveryExecution,
    private val completedReturn: (String) -> SaleDetailDto? = { null },
    private val rejectedReturn: (String) -> String? = { null },
    private val refreshRemaining: (String) -> List<ReturnabilityDto>? = { null },
    private val cancellationFactory: () -> ApiCallCancellation = ::ApiCallCancellation,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow<ReturnUiState>(ReturnUiState.Unavailable)
    val state: StateFlow<ReturnUiState> = _state.asStateFlow()
    private var quoteCancellation: ApiCallCancellation? = null
    private var draftVersion = 0L

    fun show(
        sourceName: String,
        rows: List<ReturnabilityDto>,
        policy: ReturnQuantityPolicy,
        allowedRefundModes: List<String>,
        refundModeRequired: Boolean,
    ) {
        quoteCancellation?.cancel()
        draftVersion++
        _state.value = ReturnUiState.Editing(sourceName, rows, policy, allowedRefundModes, refundModeRequired)
    }

    fun updateReason(value: String) = update { copy(reason = value, quote = null, error = null) }
    fun updateRefundMode(value: String?) = update { copy(refundMode = value, quote = null, error = null) }

    fun updateQuantity(rowId: String, value: String) = update {
        val row = rows.firstOrNull { it.original_row_id == rowId } ?: return@update this
        if (!row.eligible) return@update copy(error = row.rejection_reason ?: "row_ineligible", quote = null)
        if (row.serial_numbers.isNotEmpty()) return@update copy(error = "serialized_return_not_supported", quote = null)
        if (row.batch_numbers.size > 1) return@update copy(error = "multiple_batch_return_not_supported", quote = null)
        if (value.isBlank()) return@update copy(selections = selections.filterNot { it.rowId == rowId }, quote = null, error = null)
        val validation = validateReturnInput("selected", value, row.remaining_qty, policy)
        if (!validation.isValid) return@update copy(error = (validation as ReturnInputValidation.Invalid).reason, quote = null)
        copy(selections = selections.filterNot { it.rowId == rowId } + ReturnSelection(rowId, value), quote = null, error = null)
    }

    fun requestQuote() {
        val current = _state.value as? ReturnUiState.Editing ?: return
        val request = current.requestOrError() ?: return
        val requestVersion = draftVersion
        quoteCancellation?.cancel()
        val cancellation = cancellationFactory().also { quoteCancellation = it }
        _state.value = current.copy(loadingQuote = true, error = null)
        scope.launch {
            val result = quoteReturn(request, cancellation)
            val latest = _state.value as? ReturnUiState.Editing ?: return@launch
            if (latest.sourceName != current.sourceName || draftVersion != requestVersion || cancellation.isCancelled) return@launch
            _state.value = when (result) {
                is SaleReadResult.Success -> latest.copy(quote = result.data, loadingQuote = false)
                is SaleReadResult.Failure -> latest.copy(loadingQuote = false, error = result.code)
            }
        }
    }

    fun submit() {
        val current = _state.value as? ReturnUiState.Editing ?: return
        if (current.submitting) return
        val quote = current.quote ?: return update { copy(error = "quote_required") }
        val request = current.requestOrError() ?: return
        if (quote.source_name != current.sourceName) return update { copy(error = "stale_quote") }
        _state.value = current.copy(submitting = true, error = null)
        scope.launch {
            when (val result = createReturn(CreateReturnRequestDto(request.source_name, request.items, current.reason.trim(), request.refund_mode))) {
                is RecoveryExecution.Completed -> completed(result.transactionId)
                is RecoveryExecution.Rejected -> rejected(result.transactionId)
                is RecoveryExecution.AuthRequired -> _state.value = ReturnUiState.Submitted("auth_required")
                is RecoveryExecution.WaitingRetry -> _state.value = ReturnUiState.Submitted(result.transactionId)
                is RecoveryExecution.RetrySchedulingFailed -> _state.value = ReturnUiState.Submitted(result.transactionId)
                is RecoveryExecution.ManualRecovery -> _state.value = ReturnUiState.Submitted(result.transactionId)
                else -> _state.value = current.copy(error = "submission_unavailable")
            }
        }
    }

    fun returnLimitExceeded(refreshedRows: List<ReturnabilityDto>) {
        val current = _state.value as? ReturnUiState.Editing ?: return
        _state.value = current.copy(rows = refreshedRows, quote = null, error = "return_limit_exceeded_refreshed")
    }

    fun completed(transactionId: String) {
        val sale = completedReturn(transactionId) ?: run {
            _state.value = ReturnUiState.Submitted(transactionId)
            return
        }
        _state.value = ReturnUiState.Receipt(ReceiptMapper.map(sale))
    }

    fun rejected(transactionId: String) {
        val current = _state.value as? ReturnUiState.Editing ?: return
        if (rejectedReturn(transactionId) == "RETURN_LIMIT_EXCEEDED") {
            refreshRemaining(current.sourceName)?.let(::returnLimitExceeded)
        } else {
            _state.value = current.copy(error = "rejected")
        }
    }

    fun closeReceipt() { _state.value = ReturnUiState.Unavailable }

    fun clear() {
        quoteCancellation?.cancel()
        draftVersion++
        _state.value = ReturnUiState.Unavailable
    }

    private fun update(transform: ReturnUiState.Editing.() -> ReturnUiState.Editing) {
        val current = _state.value as? ReturnUiState.Editing ?: return
        if (current.submitting) return
        draftVersion++
        _state.value = current.transform()
    }

    private fun ReturnUiState.Editing.requestOrError(): QuoteReturnRequestDto? {
        if (reason.isBlank()) { _state.value = copy(error = "reason_required"); return null }
        if (selections.isEmpty()) { _state.value = copy(error = "selection_required"); return null }
        val selectedMode = refundMode ?: allowedRefundModes.singleOrNull()
        if (refundModeRequired && selectedMode == null) { _state.value = copy(error = "refund_mode_required"); return null }
        if (selectedMode != null && selectedMode !in allowedRefundModes) { _state.value = copy(error = "refund_mode_not_allowed"); return null }
        return QuoteReturnRequestDto(sourceName, selections.map { ReturnItemInputDto(it.rowId, it.quantity) }, selectedMode.takeIf { refundModeRequired })
    }
}
