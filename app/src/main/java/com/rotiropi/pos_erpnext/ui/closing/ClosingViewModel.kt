package com.rotiropi.pos_erpnext.ui.closing

import com.rotiropi.pos_erpnext.data.ClosingPreview
import com.rotiropi.pos_erpnext.data.ClosingReadResult
import com.rotiropi.pos_erpnext.data.ClosingReceipt
import com.rotiropi.pos_erpnext.data.api.ApiCallCancellation
import com.rotiropi.pos_erpnext.data.api.ClosingBalanceInputDto
import com.rotiropi.pos_erpnext.data.api.ClosingStatus
import com.rotiropi.pos_erpnext.data.api.SubmitClosingRequestDto
import com.rotiropi.pos_erpnext.recovery.RecoveryExecution
import android.os.SystemClock
import java.math.BigDecimal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ClosingAuthority(
    val cashier: String,
    val posProfile: String,
    val authenticationGeneration: Long,
    val repositoryGeneration: Long,
)

internal class ClosingRecoverySynchronizer(
    private val recover: (String) -> Unit,
) {
    var currentTransactionId: String? = null
        private set
    private var currentAuthority: ClosingAuthority? = null

    /**
     * Recovery identity excludes the POS profile, so the queued transaction id alone cannot
     * detect an authority switch. Switching away clears Closing UI; switching back to matching
     * authority must rehydrate the same durable transaction exactly once.
     */
    fun synchronize(transactionId: String?, authority: ClosingAuthority?) {
        if (transactionId == currentTransactionId && authority == currentAuthority) return
        currentTransactionId = transactionId
        currentAuthority = authority
        transactionId?.let(recover)
    }
}

sealed interface ClosingUiState {
    data object Unavailable : ClosingUiState
    data class Loading(val posProfile: String) : ClosingUiState
    data class Editing(
        val preview: ClosingPreview,
        val countedAmounts: Map<String, String> = emptyMap(),
        val submitting: Boolean = false,
        val error: String? = null,
    ) : ClosingUiState
    data class Recovering(val transactionId: String) : ClosingUiState
    data class Queued(
        val transactionId: String,
        val receipt: ClosingReceipt,
        val polling: Boolean,
        val checkStatusAvailable: Boolean,
        val error: String? = null,
    ) : ClosingUiState
    data class Receipt(val transactionId: String, val receipt: ClosingReceipt) : ClosingUiState
    data class Failed(val transactionId: String, val receipt: ClosingReceipt) : ClosingUiState
    data class StalePreview(val posProfile: String) : ClosingUiState
}

class ClosingViewModel(
    dispatcher: CoroutineDispatcher,
    private val previewClosing: (String, ApiCallCancellation) -> ClosingReadResult<ClosingPreview>,
    private val submitClosing: (SubmitClosingRequestDto) -> RecoveryExecution,
    private val completedClosing: (String) -> ClosingReceipt?,
    private val rejectedClosing: (String) -> String?,
    private val closingStatus: (String, ApiCallCancellation) -> ClosingReadResult<ClosingReceipt>,
    private val persistTerminal: (String, ClosingReceipt, ByteArray) -> Boolean,
    private val cancellationFactory: () -> ApiCallCancellation = ::ApiCallCancellation,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
    private val monotonicMillis: () -> Long = SystemClock::elapsedRealtime,
    private val onQueued: (ClosingReceipt) -> Unit = {},
    private val onTerminal: (ClosingReceipt) -> Unit = {},
    private val onCancelled: (ClosingReceipt) -> Unit = {},
    private val acknowledge: (String) -> Boolean = { false },
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow<ClosingUiState>(ClosingUiState.Unavailable)
    val state: StateFlow<ClosingUiState> = mutableState.asStateFlow()
    private var request: ApiCallCancellation? = null
    private var pollingJob: Job? = null
    private var notifiedQueuedTransactionId: String? = null
    private var notifiedTerminalTransactionId: String? = null
    private var notifiedCancelledTransactionId: String? = null
    private var authority: ClosingAuthority? = null
    @Volatile private var foreground = true

    fun setForeground(foreground: Boolean) {
        this.foreground = foreground
        if (!foreground) {
            request?.cancel()
            pollingJob?.cancel()
            val current = mutableState.value as? ClosingUiState.Queued
            if (current != null) {
                mutableState.value = current.copy(
                    polling = false,
                    checkStatusAvailable = true,
                )
            }
        }
    }

    fun synchronizeAuthority(authority: ClosingAuthority?) {
        if (this.authority == authority) return
        this.authority = authority
        clearPreview()
    }

    fun load(posProfile: String) {
        val requestAuthority = authority
        if (requestAuthority == null || requestAuthority.posProfile != posProfile) {
            clearPreview()
            return
        }
        cancelWork()
        mutableState.value = ClosingUiState.Loading(posProfile)
        val cancellation = cancellationFactory().also { request = it }
        scope.launch {
            val result = previewClosing(posProfile, cancellation)
            if (authority != requestAuthority) return@launch
            mutableState.value = when (result) {
                is ClosingReadResult.Success -> ClosingUiState.Editing(result.data)
                is ClosingReadResult.Failure -> ClosingUiState.Unavailable
            }
        }
    }

    fun updateCountedAmount(mode: String, value: String) {
        val current = mutableState.value as? ClosingUiState.Editing ?: return
        if (current.submitting) return
        if (mode !in current.preview.binding.paymentModes) {
            mutableState.value = current.copy(error = "payment_mode_unknown")
            return
        }
        val error = validateAmount(value, current.preview)
        mutableState.value = if (error != null) {
            current.copy(
                countedAmounts = current.countedAmounts - mode,
                error = error,
            )
        } else {
            current.copy(countedAmounts = current.countedAmounts + (mode to value), error = null)
        }
    }

    fun submit() {
        val current = mutableState.value as? ClosingUiState.Editing ?: return
        if (current.submitting) return
        val requestAuthority = authority
        if (requestAuthority == null || requestAuthority.posProfile != current.preview.binding.posProfile) {
            clearPreview()
            return
        }
        val modes = current.preview.binding.paymentModes
        if (modes.any { current.countedAmounts[it].isNullOrBlank() }) {
            mutableState.value = current.copy(error = "counted_amount_required")
            return
        }
        mutableState.value = current.copy(submitting = true, error = null)
        scope.launch {
            val execution = submitClosing(
                SubmitClosingRequestDto(
                    pos_profile = current.preview.binding.posProfile,
                    preview_id = current.preview.previewId,
                    closing_balances = modes.map { ClosingBalanceInputDto(it, current.countedAmounts.getValue(it)) },
                ),
            )
            if (authority != requestAuthority) return@launch
            handleExecution(execution, current, requestAuthority)
        }
    }

    fun recover(transactionId: String) {
        val receipt = completedClosing(transactionId) ?: run {
            mutableState.value = ClosingUiState.Recovering(transactionId)
            return
        }
        acceptReceipt(transactionId, receipt)
    }

    fun recoverProjection(name: String) {
        val requestAuthority = authority ?: return
        cancelWork()
        mutableState.value = ClosingUiState.Recovering(PROJECTION_TRANSACTION_ID)
        pollingJob = scope.launch {
            pollProjection(name, requestAuthority)
        }
    }

    fun checkStatus() {
        val current = mutableState.value as? ClosingUiState.Queued ?: return
        if (current.polling) return
        if (current.transactionId == PROJECTION_TRANSACTION_ID) {
            recoverProjection(current.receipt.name)
        } else {
            startPolling(current.transactionId, current.receipt)
        }
    }

    fun closeReceipt(): Boolean {
        val transactionId = when (val current = mutableState.value) {
            is ClosingUiState.Receipt -> current.transactionId
            is ClosingUiState.Failed -> current.transactionId
            else -> return false
        }
        if (transactionId != PROJECTION_TRANSACTION_ID && !acknowledge(transactionId)) return false
        clearPreview()
        return true
    }

    fun clear() {
        authority = null
        clearPreview()
        notifiedQueuedTransactionId = null
        notifiedTerminalTransactionId = null
        notifiedCancelledTransactionId = null
    }

    private fun clearPreview() {
        cancelWork()
        mutableState.value = ClosingUiState.Unavailable
    }

    private fun handleExecution(
        execution: RecoveryExecution,
        editing: ClosingUiState.Editing,
        requestAuthority: ClosingAuthority,
    ) {
        if (authority != requestAuthority) return
        when (execution) {
            is RecoveryExecution.Completed -> recover(execution.transactionId)
            is RecoveryExecution.ClosingQueued -> recover(execution.transactionId)
            is RecoveryExecution.Rejected -> when (rejectedClosing(execution.transactionId)) {
                "CLOSING_PREVIEW_STALE" -> mutableState.value = ClosingUiState.StalePreview(editing.preview.binding.posProfile)
                else -> mutableState.value = editing.copy(submitting = false, error = "rejected")
            }
            is RecoveryExecution.AuthRequired -> mutableState.value = ClosingUiState.Recovering("auth_required")
            is RecoveryExecution.WaitingRetry -> mutableState.value = ClosingUiState.Recovering(execution.transactionId)
            is RecoveryExecution.RetrySchedulingFailed -> mutableState.value = ClosingUiState.Recovering(execution.transactionId)
            is RecoveryExecution.ManualRecovery -> mutableState.value = ClosingUiState.Recovering(execution.transactionId)
            else -> mutableState.value = editing.copy(submitting = false, error = "submission_unavailable")
        }
    }

    private suspend fun pollProjection(name: String, requestAuthority: ClosingAuthority) {
        val result = closingStatus(name, cancellationFactory().also { request = it })
        if (authority != requestAuthority) return
        when (result) {
            is ClosingReadResult.Success -> when (result.data.status) {
                ClosingStatus.QUEUED, ClosingStatus.DRAFT ->
                    mutableState.value = ClosingUiState.Queued(
                        PROJECTION_TRANSACTION_ID,
                        result.data,
                        polling = false,
                        checkStatusAvailable = true,
                    )
                ClosingStatus.SUBMITTED -> {
                    mutableState.value = ClosingUiState.Receipt(PROJECTION_TRANSACTION_ID, result.data)
                    notifySubmittedOnce(PROJECTION_TRANSACTION_ID, result.data)
                }
                ClosingStatus.FAILED ->
                    mutableState.value = ClosingUiState.Failed(PROJECTION_TRANSACTION_ID, result.data)
                ClosingStatus.CANCELLED -> {
                    mutableState.value = ClosingUiState.Failed(PROJECTION_TRANSACTION_ID, result.data)
                    notifyCancelledOnce(PROJECTION_TRANSACTION_ID, result.data)
                }
                ClosingStatus.UNSUPPORTED -> mutableState.value = ClosingUiState.Unavailable
            }
            is ClosingReadResult.Failure -> mutableState.value = ClosingUiState.Unavailable
        }
    }

    private fun acceptReceipt(transactionId: String, receipt: ClosingReceipt) {
        when (receipt.status) {
            ClosingStatus.QUEUED, ClosingStatus.DRAFT -> {
                notifyQueuedOnce(transactionId, receipt)
                startPolling(transactionId, receipt)
            }
            ClosingStatus.SUBMITTED -> {
                mutableState.value = ClosingUiState.Receipt(transactionId, receipt)
                notifySubmittedOnce(transactionId, receipt)
            }
            ClosingStatus.FAILED -> mutableState.value = ClosingUiState.Failed(transactionId, receipt)
            ClosingStatus.CANCELLED -> {
                mutableState.value = ClosingUiState.Failed(transactionId, receipt)
                notifyCancelledOnce(transactionId, receipt)
            }
            ClosingStatus.UNSUPPORTED -> mutableState.value = ClosingUiState.Recovering(transactionId)
        }
    }

    private fun startPolling(transactionId: String, initial: ClosingReceipt) {
        pollingJob?.cancel()
        if (!foreground) {
            mutableState.value = ClosingUiState.Queued(
                transactionId,
                initial,
                polling = false,
                checkStatusAvailable = true,
            )
            return
        }
        pollingJob = scope.launch {
            var receipt = initial
            var attempt = 0
            val deadline = monotonicMillis() + MAX_POLL_WINDOW_MS
            mutableState.value = ClosingUiState.Queued(transactionId, receipt, polling = true, checkStatusAvailable = false)
            while (foreground && monotonicMillis() < deadline) {
                val wait = pollDelay(attempt++)
                if (monotonicMillis() + wait > deadline) break
                delayMillis(wait)
                if (!foreground || monotonicMillis() >= deadline) break
                val cancellation = cancellationFactory().also { request = it }
                when (val result = closingStatus(receipt.name, cancellation)) {
                    is ClosingReadResult.Success -> {
                        receipt = result.data
                        when (receipt.status) {
                            ClosingStatus.SUBMITTED -> {
                                val rawResponse = result.rawResponse
                                if (rawResponse == null || !persistTerminal(transactionId, receipt, rawResponse)) {
                                    mutableState.value = ClosingUiState.Recovering(transactionId)
                                    return@launch
                                }
                                mutableState.value = ClosingUiState.Receipt(transactionId, receipt)
                                notifySubmittedOnce(transactionId, receipt)
                                return@launch
                            }
                            ClosingStatus.FAILED, ClosingStatus.CANCELLED -> {
                                val rawResponse = result.rawResponse
                                if (rawResponse == null || !persistTerminal(transactionId, receipt, rawResponse)) {
                                    mutableState.value = ClosingUiState.Recovering(transactionId)
                                    return@launch
                                }
                                mutableState.value = ClosingUiState.Failed(transactionId, receipt)
                                if (receipt.status == ClosingStatus.CANCELLED) {
                                    notifyCancelledOnce(transactionId, receipt)
                                }
                                return@launch
                            }
                            ClosingStatus.QUEUED, ClosingStatus.DRAFT -> {
                                notifyQueuedOnce(transactionId, receipt)
                                mutableState.value = ClosingUiState.Queued(transactionId, receipt, polling = true, checkStatusAvailable = false)
                            }
                            ClosingStatus.UNSUPPORTED -> break
                        }
                    }
                    is ClosingReadResult.Failure -> {
                        mutableState.value = ClosingUiState.Queued(
                            transactionId,
                            receipt,
                            polling = false,
                            checkStatusAvailable = result.code != "AUTH_REQUIRED",
                            error = result.code,
                        )
                        return@launch
                    }
                }
            }
            mutableState.value = ClosingUiState.Queued(transactionId, receipt, polling = false, checkStatusAvailable = true)
        }
    }

    private fun notifyQueuedOnce(transactionId: String, receipt: ClosingReceipt) {
        if (notifiedQueuedTransactionId == transactionId) return
        notifiedQueuedTransactionId = transactionId
        onQueued(receipt)
    }

    private fun notifySubmittedOnce(
        transactionId: String,
        receipt: ClosingReceipt,
    ) {
        if (notifiedTerminalTransactionId == transactionId) return
        notifiedTerminalTransactionId = transactionId
        onTerminal(receipt)
    }

    private fun notifyCancelledOnce(transactionId: String, receipt: ClosingReceipt) {
        if (notifiedCancelledTransactionId == transactionId) return
        notifiedCancelledTransactionId = transactionId
        onCancelled(receipt)
    }

    private fun validateAmount(value: String, preview: ClosingPreview): String? {
        val policy = preview.countedAmountPolicy
        if (!DECIMAL.matches(value)) return "counted_amount_malformed"
        val scale = value.substringAfter('.', "").length
        if (scale > policy.maxScale) return "counted_amount_scale_exceeded"
        val amount = runCatching { BigDecimal(value) }.getOrNull() ?: return "counted_amount_malformed"
        if (amount < BigDecimal(policy.minimum) || amount > BigDecimal(policy.maximum)) return "counted_amount_out_of_bounds"
        return null
    }

    private fun cancelWork() {
        request?.cancel()
        pollingJob?.cancel()
        request = null
        pollingJob = null
    }

    private fun pollDelay(attempt: Int): Long = when (attempt) {
        0 -> 2_000L
        1 -> 4_000L
        2 -> 8_000L
        3 -> 16_000L
        else -> 30_000L
    }

    companion object {
        private val DECIMAL = Regex("^[0-9]+(?:\\.[0-9]+)?$")
        private const val PROJECTION_TRANSACTION_ID = "server-projection"
        private const val MAX_POLL_WINDOW_MS = 300_000L
    }
}
