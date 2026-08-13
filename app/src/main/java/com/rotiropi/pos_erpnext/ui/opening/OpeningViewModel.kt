package com.rotiropi.pos_erpnext.ui.opening

import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.data.PosProfile
import com.rotiropi.pos_erpnext.data.api.OpenSessionRequestDto
import com.rotiropi.pos_erpnext.data.api.OpeningBalanceInputDto
import com.rotiropi.pos_erpnext.recovery.RecoveryExecution
import com.rotiropi.pos_erpnext.ui.UiText
import com.rotiropi.pos_erpnext.ui.uiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OpeningRowUiState(
    val modeOfPayment: String,
    val input: String,
    val editable: Boolean,
    val error: UiText? = null,
)

data class OpeningUiState(
    val profileName: String? = null,
    val currency: String? = null,
    val cashier: String? = null,
    val company: String? = null,
    val warehouse: String? = null,
    val rows: List<OpeningRowUiState> = emptyList(),
    val unavailable: Boolean = false,
    val canSubmit: Boolean = false,
    val submitting: Boolean = false,
    val recoveryPending: Boolean = false,
    val authenticationRequired: Boolean = false,
    val reconciling: Boolean = false,
    val error: UiText? = null,
)

class OpeningViewModel(
    private val cashier: String,
    private val profile: PosProfile,
    private val submit: (OpenSessionRequestDto) -> RecoveryExecution,
) {
    private val policy = profile.openingAmountPolicy?.takeIf {
        it.apiSyntax == "ascii_decimal_dot" &&
            it.rounding == "reject" &&
            it.decimalPlaces >= 0 &&
            runCatching { java.math.BigDecimal(it.minimum) }.isSuccess
    }
    private val mutableState = MutableStateFlow(initialState())
    val state: StateFlow<OpeningUiState> = mutableState.asStateFlow()

    fun belongsTo(cashier: String, profileName: String): Boolean =
        this.cashier == cashier && profile.name == profileName

    fun updateAmount(modeOfPayment: String, input: String) {
        val current = mutableState.value
        if (current.submitting || current.recoveryPending || current.authenticationRequired) return
        val rows = current.rows.map { row ->
            if (row.modeOfPayment != modeOfPayment || !row.editable) row else validate(row.copy(input = input))
        }
        mutableState.value = current.copy(rows = rows, canSubmit = canSubmit(rows), error = null)
    }

    fun submit(): RecoveryExecution? {
        val current = mutableState.value
        if (!current.canSubmit || current.submitting || current.recoveryPending || current.authenticationRequired) return null
        val inputPolicy = policy ?: return null
        val canonical = current.rows.map { row ->
            val result = canonicalizeOpeningAmount(row.input, inputPolicy.toInputPolicy())
            if (result !is OpeningAmountResult.Valid) return null
            OpeningBalanceInputDto(row.modeOfPayment, result.canonical)
        }
        mutableState.value = current.copy(submitting = true, canSubmit = false, error = null)
        val execution = submit(OpenSessionRequestDto(profile.name, canonical))
        mutableState.value = when (execution) {
            is RecoveryExecution.Completed -> mutableState.value.copy(submitting = false, recoveryPending = true)
            // Developer-facing programming error, never surfaced to a cashier.
            is RecoveryExecution.ClosingQueued -> error("Closing result cannot complete Opening")
            is RecoveryExecution.WaitingRetry,
            is RecoveryExecution.RetrySchedulingFailed,
            is RecoveryExecution.ManualRecovery -> mutableState.value.copy(submitting = false, recoveryPending = true)
            RecoveryExecution.AuthRequired -> mutableState.value.copy(submitting = false, authenticationRequired = true)
            RecoveryExecution.NotStartedOffline -> mutableState.value.copy(
                submitting = false,
                canSubmit = true,
                error = uiText(R.string.opening_error_offline),
            )
            RecoveryExecution.BlockedIdentity -> mutableState.value.copy(
                submitting = false,
                error = uiText(R.string.opening_error_other_recovery),
            )
            is RecoveryExecution.Rejected -> mutableState.value.copy(
                submitting = false,
                error = uiText(R.string.opening_error_rejected),
            )
        }
        return execution
    }

    fun allowResubmitAfterTerminalReconciliation() {
        val current = mutableState.value
        mutableState.value = current.copy(
            submitting = false,
            recoveryPending = false,
            authenticationRequired = false,
            canSubmit = canSubmit(current.rows),
        )
    }

    fun reconciliationFailed() {
        mutableState.value = mutableState.value.copy(
            submitting = false,
            recoveryPending = true,
            authenticationRequired = false,
            canSubmit = false,
            error = uiText(R.string.opening_error_recovery_unverified),
        )
    }

    fun currentSessionFailed() {
        mutableState.value = mutableState.value.copy(
            unavailable = true,
            canSubmit = false,
            error = uiText(R.string.opening_error_verification_failed),
        )
    }

    fun clear() {
        mutableState.value = OpeningUiState(unavailable = true)
    }

    private fun initialState(): OpeningUiState {
        val unavailable = profile.openingPaymentModes.isEmpty() || policy == null
        val rows = if (unavailable) emptyList() else profile.openingPaymentModes.map { mode ->
            validate(OpeningRowUiState(mode.modeOfPayment, mode.suggestedOpeningAmount, mode.amountEditable))
        }
        return OpeningUiState(
            profileName = profile.name,
            currency = policy?.currency,
            cashier = cashier,
            company = profile.company,
            warehouse = profile.warehouse,
            rows = rows,
            unavailable = unavailable,
            canSubmit = canSubmit(rows),
            error = if (unavailable) uiText(R.string.opening_unavailable) else null,
        )
    }

    private fun validate(row: OpeningRowUiState): OpeningRowUiState {
        val inputPolicy = policy?.toInputPolicy()
            ?: return row.copy(error = uiText(R.string.opening_unavailable))
        return when (val result = canonicalizeOpeningAmount(row.input, inputPolicy)) {
            is OpeningAmountResult.Valid -> row.copy(error = null)
            is OpeningAmountResult.Invalid -> row.copy(error = result.reason)
        }
    }

    private fun canSubmit(rows: List<OpeningRowUiState>): Boolean =
        policy != null && rows.isNotEmpty() && rows.all { it.error == null }

    private fun com.rotiropi.pos_erpnext.data.OpeningAmountPolicy.toInputPolicy() =
        OpeningAmountInputPolicy(decimalPlaces, minimum)
}
