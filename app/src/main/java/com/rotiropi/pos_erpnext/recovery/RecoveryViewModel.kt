package com.rotiropi.pos_erpnext.recovery

import com.rotiropi.pos_erpnext.auth.AuthenticationSnapshot
import com.rotiropi.pos_erpnext.auth.AuthenticationState
import com.rotiropi.pos_erpnext.data.api.ClosingStatus
import com.rotiropi.pos_erpnext.data.api.CreateReturnResponseDto
import com.rotiropi.pos_erpnext.data.api.FrappeResponse
import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import com.rotiropi.pos_erpnext.data.api.OpenSessionResponseDto
import com.rotiropi.pos_erpnext.data.api.SubmitClosingResponseDto
import com.rotiropi.pos_erpnext.data.api.SubmitSaleResponseDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface RecoveryTerminalResult {
    data class Completed(
        val operation: String,
        val reference: String,
        val status: String,
        val amount: String?,
    ) : RecoveryTerminalResult

    data class Rejected(
        val code: String,
        val message: String,
        val reference: String?,
    ) : RecoveryTerminalResult
}

sealed interface RecoveryScreenState {
    data object Hidden : RecoveryScreenState

    data class AuthenticationRequired(val transactionId: String) : RecoveryScreenState

    data class RetrySchedulingFailed(val transactionId: String) : RecoveryScreenState

    data class Terminal(
        val identity: RecoveryIdentity,
        val generation: Long,
        val transactionId: String,
        val result: RecoveryTerminalResult,
        internal val token: TerminalReadToken,
    ) : RecoveryScreenState

    data class ManualRecovery(
        val transactionId: String,
        val message: String,
        val canAcknowledge: Boolean = false,
        val canRecoverClosing: Boolean = false,
    ) : RecoveryScreenState
}

class RecoveryViewModel(
    private val authenticationSnapshot: () -> AuthenticationSnapshot,
    private val currentIdentity: () -> RecoveryIdentity?,
    private val recoveryState: () -> RecoveryUiState,
    private val readTerminalResult: (String) -> ValidatedTerminalResult?,
    private val acknowledgeTerminal: (TerminalReadToken) -> RecoveryAcknowledgement,
    private val quarantine: (String) -> Unit,
) {
    private val mutableState = MutableStateFlow<RecoveryScreenState>(RecoveryScreenState.Hidden)
    val state: StateFlow<RecoveryScreenState> = mutableState.asStateFlow()

    fun onAuthenticationChanged(snapshot: AuthenticationSnapshot) {
        if (snapshot.state != AuthenticationState.Authenticated) {
            mutableState.value = RecoveryScreenState.Hidden
            return
        }
        val terminal = state.value as? RecoveryScreenState.Terminal
        if (terminal != null && terminal.generation != snapshot.generation) {
            mutableState.value = RecoveryScreenState.Hidden
        }
    }

    fun refresh(snapshot: AuthenticationSnapshot = authenticationSnapshot(), identity: RecoveryIdentity? = currentIdentity()) {
        if (!isCurrent(snapshot, identity)) {
            mutableState.value = RecoveryScreenState.Hidden
            return
        }
        val recovery = recoveryState()
        if (recovery.identity != null && recovery.identity != identity) {
            mutableState.value = RecoveryScreenState.Hidden
            return
        }
        val terminal = recovery.terminal
        if (terminal == null) {
            mutableState.value = recovery.authenticationRequiredTransactionId?.let(RecoveryScreenState::AuthenticationRequired)
                ?: recovery.retrySchedulingFailedTransactionId?.let(RecoveryScreenState::RetrySchedulingFailed)
                ?: recovery.manualClosingTransactionId?.let {
                    RecoveryScreenState.ManualRecovery(
                        it,
                        "Closing result needs authoritative recovery.",
                        canRecoverClosing = true,
                    )
                }
                ?: RecoveryScreenState.Hidden
            return
        }
        val validated = readTerminalResult(terminal.transactionId)
        if (!isCurrent(snapshot, identity)) return
        val result = validated?.let(RecoveryTerminalMapper::parse)
        if (validated == null || result == null) {
            quarantine(terminal.transactionId)
            mutableState.value = RecoveryScreenState.ManualRecovery(
                terminal.transactionId,
                "Recovery result could not be validated. Contact support and do not retry this action.",
            )
            return
        }
        mutableState.value = RecoveryScreenState.Terminal(
            identity = requireNotNull(identity),
            generation = snapshot.generation,
            transactionId = terminal.transactionId,
            result = result,
            token = validated.token,
        )
    }

    fun acknowledge(): RecoveryAcknowledgement {
        val terminal = state.value as? RecoveryScreenState.Terminal
            ?: return RecoveryAcknowledgement.NotAcknowledged("")
        if (!isCurrent(AuthenticationSnapshot(terminal.generation, AuthenticationState.Authenticated), terminal.identity)) {
            mutableState.value = RecoveryScreenState.Hidden
            return RecoveryAcknowledgement.NotAcknowledged(terminal.transactionId)
        }
        return acknowledgeTerminal(terminal.token).also { result ->
            if (result is RecoveryAcknowledgement.Acknowledged) mutableState.value = RecoveryScreenState.Hidden
        }
    }

    private fun isCurrent(snapshot: AuthenticationSnapshot, identity: RecoveryIdentity?): Boolean {
        val current = authenticationSnapshot()
        return snapshot.state == AuthenticationState.Authenticated &&
            current.state == AuthenticationState.Authenticated &&
            current.generation == snapshot.generation &&
            identity != null && currentIdentity() == identity
    }
}

object RecoveryTerminalMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(result: ValidatedTerminalResult): RecoveryTerminalResult? {
        return try {
            val envelope = json.decodeFromString<FrappeResponse>(result.responseText).message
            if (envelope.meta.api_version != "v1") return null
            if (!envelope.ok) {
                val error = envelope.error ?: return null
                return RecoveryTerminalResult.Rejected(error.code, error.message, envelope.meta.request_id)
            }
            val data = envelope.data ?: return null
            when (result.endpoint) {
                MobilePosEndpoint.SESSIONS_OPEN -> json.decodeFromJsonElement(OpenSessionResponseDto.serializer(), data)
                    .opening_session.let { RecoveryTerminalResult.Completed("Opening", it.name, it.status.name, null) }
                MobilePosEndpoint.SALES_SUBMIT -> json.decodeFromJsonElement(SubmitSaleResponseDto.serializer(), data)
                    .sale.summary.let { RecoveryTerminalResult.Completed("Sale", it.name, it.status.name, it.grand_total) }
                MobilePosEndpoint.SALES_CREATE_RETURN -> json.decodeFromJsonElement(CreateReturnResponseDto.serializer(), data)
                    .return_sale.summary.let { RecoveryTerminalResult.Completed("Return", it.name, it.status.name, it.grand_total) }
                MobilePosEndpoint.CLOSING_SUBMIT -> json.decodeFromJsonElement(SubmitClosingResponseDto.serializer(), data)
                    .closing.let {
                        if (it.status == ClosingStatus.UNSUPPORTED) null
                        else RecoveryTerminalResult.Completed("Closing", it.name, it.status.name, null)
                    }
                else -> null
            }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
