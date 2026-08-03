package com.rotiropi.pos_erpnext.ui.opening

import com.rotiropi.pos_erpnext.data.BootstrapRefreshTrigger
import com.rotiropi.pos_erpnext.data.CurrentSessionResult
import com.rotiropi.pos_erpnext.data.api.OpenSessionRequestDto
import com.rotiropi.pos_erpnext.recovery.RecoveryExecution

enum class OpeningDestination { OPENING, SHELL }

data class OpeningFlowResult(
    val destination: OpeningDestination,
    val reconciled: Boolean = false,
)

class OpeningFlowCoordinator(
    private val currentSession: () -> CurrentSessionResult,
    private val submitOpening: (OpenSessionRequestDto) -> RecoveryExecution,
    private val refreshCapabilities: (BootstrapRefreshTrigger) -> Unit,
) {
    private val refreshedTransactions = mutableSetOf<String>()

    fun reconcile(): OpeningDestination = when (val result = currentSession()) {
        is CurrentSessionResult.Success -> if (result.opening == null) OpeningDestination.OPENING else OpeningDestination.SHELL
        is CurrentSessionResult.Failure, CurrentSessionResult.Discarded -> OpeningDestination.OPENING
    }

    fun submit(request: OpenSessionRequestDto): OpeningFlowResult = handleExecution(submitOpening(request))

    fun handleExecution(result: RecoveryExecution): OpeningFlowResult = when (result) {
        is RecoveryExecution.Completed -> reconcileSuccess(result.transactionId)
        else -> OpeningFlowResult(OpeningDestination.OPENING)
    }

    fun onRecoveredOpening(transactionId: String): OpeningFlowResult =
        if (transactionId in refreshedTransactions) {
            OpeningFlowResult(OpeningDestination.SHELL, reconciled = true)
        } else {
            reconcileSuccess(transactionId)
        }

    fun onRecoveredRejection(transactionId: String, code: String): OpeningFlowResult {
        if (code != "SESSION_ALREADY_OPEN") return OpeningFlowResult(OpeningDestination.OPENING)
        return if (transactionId in refreshedTransactions) {
            OpeningFlowResult(OpeningDestination.SHELL, reconciled = true)
        } else {
            reconcileSuccess(transactionId)
        }
    }

    private fun reconcileSuccess(transactionId: String): OpeningFlowResult {
        val destination = reconcile()
        if (destination == OpeningDestination.SHELL) refreshOnce(transactionId)
        return OpeningFlowResult(destination, reconciled = destination == OpeningDestination.SHELL)
    }

    private fun refreshOnce(transactionId: String) {
        if (refreshedTransactions.add(transactionId)) {
            refreshCapabilities(BootstrapRefreshTrigger.OPENING_COMPLETED)
        }
    }
}
