package com.rotiropi.pos_erpnext.ui.opening

import com.rotiropi.pos_erpnext.data.BootstrapFailure
import com.rotiropi.pos_erpnext.data.BootstrapRefreshTrigger
import com.rotiropi.pos_erpnext.data.CurrentSessionResult
import com.rotiropi.pos_erpnext.data.api.OpenSessionRequestDto
import com.rotiropi.pos_erpnext.recovery.RecoveryExecution
import com.rotiropi.pos_erpnext.recovery.RecoveryIdentity

enum class OpeningDestination { OPENING, SHELL }

data class OpeningFlowResult(
    val destination: OpeningDestination,
    val reconciled: Boolean = false,
    val failure: BootstrapFailure? = null,
)

sealed interface RecoveredOpeningTerminal {
    val identity: RecoveryIdentity
    val generation: Long
    val transactionId: String

    data class Completed(
        override val identity: RecoveryIdentity,
        override val generation: Long,
        override val transactionId: String,
    ) : RecoveredOpeningTerminal

    data class Rejected(
        override val identity: RecoveryIdentity,
        override val generation: Long,
        override val transactionId: String,
        val code: String,
    ) : RecoveredOpeningTerminal
}

class OpeningReconciliationRunner(
    private val flow: OpeningFlowCoordinator,
    private val dispatch: ((() -> Unit) -> Unit),
    private val isCurrent: (RecoveredOpeningTerminal) -> Boolean,
    private val onResult: (String, OpeningFlowResult) -> Unit,
) {
    private val lock = Any()
    private val observedTransactions = mutableSetOf<String>()

    fun immediate(execution: RecoveryExecution) {
        val completed = execution as? RecoveryExecution.Completed ?: return
        dispatchOnce(completed.transactionId) {
            flow.handleExecution(execution)
        }
    }

    fun recovered(terminal: RecoveredOpeningTerminal) {
        if (!isCurrent(terminal)) return
        dispatchOnce(terminal.transactionId) {
            if (!isCurrent(terminal)) return@dispatchOnce null
            when (terminal) {
                is RecoveredOpeningTerminal.Completed -> flow.onRecoveredOpening(
                    terminal.transactionId,
                ) { isCurrent(terminal) }
                is RecoveredOpeningTerminal.Rejected -> flow.onRecoveredRejection(
                    terminal.transactionId,
                    terminal.code,
                ) { isCurrent(terminal) }
            }.takeIf { isCurrent(terminal) }
        }
    }

    private fun dispatchOnce(transactionId: String, reconcile: () -> OpeningFlowResult?) {
        synchronized(lock) {
            if (!observedTransactions.add(transactionId)) return
        }
        dispatch {
            reconcile()?.let { onResult(transactionId, it) }
        }
    }
}

class OpeningFlowCoordinator(
    private val currentSession: () -> CurrentSessionResult,
    private val submitOpening: (OpenSessionRequestDto) -> RecoveryExecution,
    private val refreshCapabilities: (BootstrapRefreshTrigger) -> Unit,
) {
    private val refreshedTransactions = mutableSetOf<String>()

    fun reconcile(): OpeningDestination = reconciliation().destination

    private fun reconciliation(): OpeningFlowResult = when (val result = currentSession()) {
        is CurrentSessionResult.Success -> OpeningFlowResult(
            if (result.opening == null) OpeningDestination.OPENING else OpeningDestination.SHELL,
        )
        is CurrentSessionResult.Failure -> OpeningFlowResult(
            OpeningDestination.OPENING,
            failure = result.reason,
        )
        CurrentSessionResult.Discarded -> OpeningFlowResult(OpeningDestination.OPENING)
    }

    fun submit(request: OpenSessionRequestDto): OpeningFlowResult = handleExecution(submitOpening(request))

    fun handleExecution(
        result: RecoveryExecution,
        canPublish: () -> Boolean = { true },
    ): OpeningFlowResult = when (result) {
        is RecoveryExecution.Completed -> reconcileSuccess(result.transactionId, canPublish)
        else -> OpeningFlowResult(OpeningDestination.OPENING)
    }

    fun onRecoveredOpening(
        transactionId: String,
        canPublish: () -> Boolean = { true },
    ): OpeningFlowResult = if (transactionId in refreshedTransactions) {
        OpeningFlowResult(OpeningDestination.SHELL, reconciled = true)
    } else {
        reconcileSuccess(transactionId, canPublish)
    }

    fun onRecoveredRejection(
        transactionId: String,
        code: String,
        canPublish: () -> Boolean = { true },
    ): OpeningFlowResult {
        if (code != "SESSION_ALREADY_OPEN") return OpeningFlowResult(OpeningDestination.OPENING)
        return if (transactionId in refreshedTransactions) {
            OpeningFlowResult(OpeningDestination.SHELL, reconciled = true)
        } else {
            reconcileSuccess(transactionId, canPublish)
        }
    }

    private fun reconcileSuccess(
        transactionId: String,
        canPublish: () -> Boolean,
    ): OpeningFlowResult {
        val result = reconciliation()
        if (!canPublish()) return OpeningFlowResult(OpeningDestination.OPENING)
        if (result.destination == OpeningDestination.SHELL) refreshOnce(transactionId)
        return result.copy(reconciled = result.destination == OpeningDestination.SHELL)
    }

    private fun refreshOnce(transactionId: String) {
        if (refreshedTransactions.add(transactionId)) {
            refreshCapabilities(BootstrapRefreshTrigger.OPENING_COMPLETED)
        }
    }
}
