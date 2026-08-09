package com.rotiropi.pos_erpnext.recovery

import com.rotiropi.pos_erpnext.data.ConnectivityStatus
import com.rotiropi.pos_erpnext.data.api.ApiResult
import com.rotiropi.pos_erpnext.data.api.ClosingStatus
import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import com.rotiropi.pos_erpnext.data.api.MobilePosRequest
import com.rotiropi.pos_erpnext.data.api.SubmitClosingResponseDto
import com.rotiropi.pos_erpnext.data.api.SubmitClosingRequestDto
import com.rotiropi.pos_erpnext.data.api.RetryAfter
import com.rotiropi.pos_erpnext.data.api.TransportFailureKind
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

sealed interface RecoveryExecution {
    data object NotStartedOffline : RecoveryExecution
    data object AuthRequired : RecoveryExecution
    data object BlockedIdentity : RecoveryExecution
    data class Completed(val transactionId: String) : RecoveryExecution
    data class ClosingQueued(val transactionId: String) : RecoveryExecution
    data class Rejected(val transactionId: String) : RecoveryExecution
    data class WaitingRetry(val transactionId: String) : RecoveryExecution
    data class RetrySchedulingFailed(val transactionId: String) : RecoveryExecution
    data class ManualRecovery(val transactionId: String) : RecoveryExecution
}

sealed interface RecoveryAcknowledgement {
    data class Acknowledged(val transactionId: String) : RecoveryAcknowledgement
    data class NotAcknowledged(val transactionId: String) : RecoveryAcknowledgement
}

data class RecoveryUiState(
    val identity: RecoveryIdentity? = null,
    val terminal: TerminalRecovery? = null,
    val closingQueuedTransactionId: String? = null,
    val manualClosingTransactionId: String? = null,
    val authenticationRequiredTransactionId: String? = null,
    val retrySchedulingFailedTransactionId: String? = null,
    val retrySchedulingFailure: RecoveryExecution.RetrySchedulingFailed? = null,
)

data class RecoverySpec<T, R>(
    val endpoint: MobilePosEndpoint,
    val body: T,
    val bodySerializer: SerializationStrategy<T>,
    val responseDeserializer: DeserializationStrategy<R>,
    val json: Json,
)

interface RecoveryTransport {
    fun <T> execute(request: PendingMutation, deserializer: DeserializationStrategy<T>): ApiResult<T>
}

interface RetryScheduler {
    fun schedule(transactionId: String, nextEligibleAtMillis: Long, completion: (Throwable?) -> Unit)
}

/** Cold worker gate. Retry occurs only after valid auth and authoritative bootstrap. */
class ColdRecovery(
    private val hasStoredAuth: () -> Boolean,
    private val currentBootstrapIdentity: () -> RecoveryIdentity?,
    private val bootstrap: () -> Boolean,
    private val recoverStaleSending: (String) -> Boolean = { false },
    private val retryAction: (String) -> RecoveryExecution,
) {
    fun retry(transactionId: String): RecoveryExecution? {
        if (!hasStoredAuth()) return null
        if (currentBootstrapIdentity() == null && !bootstrap()) return null
        recoverStaleSending(transactionId)
        return retryAction(transactionId)
    }
}

class RecoveryCoordinator(
    private val store: PendingMutationStore,
    private val transport: RecoveryTransport,
    private val connectivity: () -> ConnectivityStatus,
    private val identity: () -> RecoveryIdentity?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val randomUuid: () -> String = { java.util.UUID.randomUUID().toString() },
    private val jitterMillis: () -> Long = { kotlin.random.Random.nextLong(501) },
    private val scheduler: RetryScheduler = object : RetryScheduler {
        override fun schedule(transactionId: String, nextEligibleAtMillis: Long, completion: (Throwable?) -> Unit) = completion(null)
    },
) {
    private var recoveredStartupIdentity: RecoveryIdentity? = null
    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()

    fun <T, R> execute(spec: RecoverySpec<T, R>): RecoveryExecution {
        require(spec.endpoint in MUTATION_ENDPOINTS)
        if (connectivity() == ConnectivityStatus.KnownOffline) return RecoveryExecution.NotStartedOffline
        val admission = synchronized(RecoveryLogoutGate.lock) {
            val owner = identity() ?: return@synchronized null
            val body = spec.json.encodeToString(spec.bodySerializer, spec.body).encodeToByteArray()
            MobilePosRequest.validatePostBodyBytes(spec.endpoint, body, spec.json)
            val id = randomUuid()
            require(LOWERCASE_UUID.matches(id))
            PendingMutation(
                transactionId = id,
                identity = owner,
                endpoint = spec.endpoint,
                body = body,
                contentType = "application/json",
                serializerIdentity = spec.endpoint.serializerIdentity,
                bodyFormatVersion = PendingMutation.BODY_FORMAT_VERSION,
                createdAtMillis = clock(),
            ).let { it to store.prepare(it) }
        } ?: return RecoveryExecution.BlockedIdentity
        val (prepared, result) = admission
        return when (result) {
            PendingMutationAdmission.ACCEPTED -> dispatch(prepared, spec.responseDeserializer)
            PendingMutationAdmission.UNRESOLVED_EXISTS -> RecoveryExecution.BlockedIdentity
            is PendingMutationAdmission.MANUAL_RECOVERY_REQUIRED ->
                RecoveryExecution.ManualRecovery(result.evidenceTransactionId)
            PendingMutationAdmission.CRYPTO_FAILURE -> RecoveryExecution.BlockedIdentity
        }
    }

    /** Call only after bootstrap authoritatively established this process identity. */
    @Synchronized
    fun recoverAtAuthenticatedStartup(nowMillis: Long = clock()): List<RecoveryExecution> {
        val owner = identity() ?: return emptyList()
        if (recoveredStartupIdentity == owner) return emptyList()
        store.unresolved(owner).forEach { record ->
            if (record.state == PendingMutationState.SENDING) {
                store.recoverStaleSending(record.transactionId, owner)
            }
        }
        val outcomes = store.unresolved(owner)
            .filter { it.state in DELAYED_DISPATCH_STATES - PendingMutationState.AUTH_REQUIRED }
            .map { scheduleOrFailure(it.transactionId, it.nextEligibleAtMillis, owner) }
        if (outcomes.none { it is RecoveryExecution.RetrySchedulingFailed }) {
            recoveredStartupIdentity = owner
        }
        refreshUiState(owner, outcomes.filterIsInstance<RecoveryExecution.RetrySchedulingFailed>().firstOrNull())
        return outcomes
    }

    /** Call only after new credentials and same-cashier bootstrap succeeded. */
    fun resumeAfterSuccessfulReauthentication(): List<RecoveryExecution> {
        val owner = identity() ?: return emptyList()
        val outcomes = store.unresolved(owner)
            .filter { it.state == PendingMutationState.AUTH_REQUIRED }
            .map { scheduleOrFailure(it.transactionId, it.nextEligibleAtMillis, owner) }
        refreshUiState(owner, outcomes.filterIsInstance<RecoveryExecution.RetrySchedulingFailed>().firstOrNull())
        return outcomes
    }

    /** Process-restart boundary. Ordinary retry never reclaims an active sender. */
    fun recoverStaleSending(transactionId: String): Boolean {
        val owner = identity() ?: return false
        return store.recoverStaleSending(transactionId, owner)
    }

    fun acknowledge(transactionId: String): RecoveryAcknowledgement =
        readTerminalResult(transactionId)?.let { acknowledge(it.token) }
            ?: RecoveryAcknowledgement.NotAcknowledged(transactionId)

    fun acknowledge(token: TerminalReadToken): RecoveryAcknowledgement {
        val owner = identity() ?: return RecoveryAcknowledgement.NotAcknowledged(token.transactionId)
        val outcome = if (store.acknowledge(token, owner)) {
            RecoveryAcknowledgement.Acknowledged(token.transactionId)
        } else {
            RecoveryAcknowledgement.NotAcknowledged(token.transactionId)
        }
        refreshUiState(owner)
        return outcome
    }

    fun readTerminalResult(transactionId: String): ValidatedTerminalResult? =
        identity()?.let { store.readTerminalResult(transactionId, it) }

    fun readClosingResult(transactionId: String): ValidatedClosingResult? =
        identity()?.let { store.readClosingResult(transactionId, it) }

    fun persistClosingTerminal(
        transactionId: String,
        status: ClosingStatus,
        response: ByteArray,
        reference: String,
    ): Boolean {
        if (status !in setOf(ClosingStatus.SUBMITTED, ClosingStatus.FAILED, ClosingStatus.CANCELLED)) return false
        val owner = identity() ?: return false
        val persisted = store.persistClosingStatusTerminal(transactionId, owner, response, reference)
        if (persisted) refreshUiState(owner)
        return persisted
    }

    fun refreshUiState() {
        identity()?.let(::refreshUiState) ?: clearUiState()
    }

    fun clearUiState() {
        _uiState.value = RecoveryUiState()
    }

    fun quarantine(transactionId: String): Boolean =
        identity()?.let { store.markManualRecovery(transactionId, it) } ?: false

    fun retry(transactionId: String, nowMillis: Long = clock()): RecoveryExecution =
        retry(transactionId, kotlinx.serialization.json.JsonElement.serializer(), nowMillis)

    fun <T> retry(
        transactionId: String,
        deserializer: DeserializationStrategy<T>,
        nowMillis: Long = clock(),
    ): RecoveryExecution {
        val owner = identity() ?: return RecoveryExecution.BlockedIdentity
        val record = store.find(transactionId, owner) ?: return RecoveryExecution.BlockedIdentity
        if (record.state == PendingMutationState.MANUAL_RECOVERY) return RecoveryExecution.ManualRecovery(record.transactionId)
        if (record.state == PendingMutationState.SENDING) return RecoveryExecution.WaitingRetry(record.transactionId)
        if (record.state == PendingMutationState.CLOSING_QUEUED) return RecoveryExecution.ClosingQueued(record.transactionId)
        if (record.state.terminal) return RecoveryExecution.BlockedIdentity
        if (record.attemptCount >= MAX_DISPATCHES) return manual(record)
        if (nowMillis < record.nextEligibleAtMillis) {
            return scheduleOrFailure(record.transactionId, record.nextEligibleAtMillis, record.identity)
        }
        return dispatch(record, deserializer)
    }

    /** Explicitly reconciles only a persisted manual Closing through same-key/body replay. */
    @Synchronized
    fun recoverManualClosing(
        transactionId: String,
        expectedProfile: String,
        currentProfile: () -> String? = { expectedProfile },
    ): RecoveryExecution {
        if (connectivity() == ConnectivityStatus.KnownOffline) return RecoveryExecution.NotStartedOffline
        val owner = identity() ?: return RecoveryExecution.BlockedIdentity
        if (currentProfile() != expectedProfile) return RecoveryExecution.BlockedIdentity
        val record = store.find(transactionId, owner) ?: return RecoveryExecution.BlockedIdentity
        val request = manualClosingRequest(record, expectedProfile)
            ?: return RecoveryExecution.ManualRecovery(transactionId)
        val result = transport.execute(record, SubmitClosingResponseDto.serializer())
        if (result !is ApiResult.Success || !result.meta.replayed) {
            return RecoveryExecution.ManualRecovery(transactionId)
        }
        if (identity() != owner || currentProfile() != expectedProfile) return RecoveryExecution.BlockedIdentity
        val closing = result.data.closing
        if (closing.pos_profile != request.pos_profile ||
            closing.status !in setOf(ClosingStatus.SUBMITTED, ClosingStatus.FAILED, ClosingStatus.CANCELLED) ||
            result.rawResponse == null
        ) return RecoveryExecution.ManualRecovery(transactionId)
        return if (store.persistClosingStatusTerminal(transactionId, owner, result.rawResponse, closing.name)) {
            refreshUiState(owner)
            RecoveryExecution.Completed(transactionId)
        } else {
            durableState(transactionId, owner)
        }
    }

    private fun <T> dispatch(record: PendingMutation, deserializer: DeserializationStrategy<T>): RecoveryExecution {
        if (record.attemptCount >= MAX_DISPATCHES) return manual(record)
        if (record.state == PendingMutationState.MANUAL_RECOVERY) return RecoveryExecution.ManualRecovery(record.transactionId)
        val sending = store.beginSending(
            record.transactionId,
            record.identity,
            record.state,
            record.attemptCount,
            record.nextEligibleAtMillis,
        ) ?: return durableState(record.transactionId, record.identity)
        if (sending.state != PendingMutationState.SENDING || sending.body.isEmpty()) {
            store.markManualRecovery(sending.transactionId, sending.identity)
            return RecoveryExecution.ManualRecovery(sending.transactionId)
        }
        return when (val response = transport.execute(sending, deserializer)) {
            is ApiResult.Success -> {
                val closing = (response.data as? SubmitClosingResponseDto)?.closing
                if (sending.endpoint == MobilePosEndpoint.CLOSING_SUBMIT &&
                    closing?.status == ClosingStatus.QUEUED
                ) {
                    persistClosingQueued(sending, response.rawResponse, closing.name)
                } else if (sending.endpoint == MobilePosEndpoint.CLOSING_SUBMIT &&
                    closing?.status == ClosingStatus.DRAFT
                ) {
                    manual(sending)
                } else {
                    persistTerminal(
                        sending,
                        PendingMutationState.COMPLETED,
                        response.rawResponse,
                        response.meta.request_id,
                    )
                }
            }
            is ApiResult.ExpectedFailure -> when {
                response.error.code == "IDEMPOTENCY_KEY_REUSED" -> manual(sending)
                response.error.code == "REQUEST_IN_PROGRESS" && sending.endpoint in setOf(
                    MobilePosEndpoint.CLOSING_SUBMIT,
                    MobilePosEndpoint.SALES_CREATE_RETURN,
                ) -> {
                    wait(
                        sending,
                        retryAfterSeconds(response.error.details["retry_after_seconds"]),
                        response.retryAfter,
                        PendingMutationState.REQUEST_IN_PROGRESS,
                    )
                }
                response.error.code == "REQUEST_IN_PROGRESS" -> manual(sending)
                response.statusCode == 401 -> pauseForAuthentication(sending)
                response.statusCode in TRANSIENT_HTTP_STATUS_CODES -> wait(sending, httpRetryAfter = response.retryAfter)
                else -> persistTerminal(
                    sending,
                    PendingMutationState.REJECTED,
                    response.rawResponse,
                    response.meta.request_id,
                )
            }
            is ApiResult.TransportFailure -> when (response.kind) {
                TransportFailureKind.AUTHENTICATION_REQUIRED -> pauseForAuthentication(sending)
                TransportFailureKind.ROUTE_FORBIDDEN,
                TransportFailureKind.ROUTE_NOT_FOUND,
                TransportFailureKind.CANCELLED -> manual(sending)
                TransportFailureKind.RATE_LIMITED,
                TransportFailureKind.SERVER_UNAVAILABLE,
                TransportFailureKind.NETWORK_FAILURE,
                TransportFailureKind.TIMEOUT -> wait(sending, httpRetryAfter = response.retryAfter)
            }
            is ApiResult.ProtocolFailure -> manual(sending)
        }
    }

    private fun pauseForAuthentication(record: PendingMutation): RecoveryExecution {
        check(store.finishSending(
            record.transactionId,
            record.identity,
            PendingMutationState.AUTH_REQUIRED,
            record.attemptCount,
            record.nextEligibleAtMillis,
            record.reference,
        ))
        refreshUiState(record.identity)
        return RecoveryExecution.AuthRequired
    }

    private fun persistClosingQueued(
        record: PendingMutation,
        response: ByteArray?,
        reference: String,
    ): RecoveryExecution {
        val bytes = response ?: return manual(record)
        return if (store.persistClosingQueued(record.transactionId, record.identity, bytes, reference)) {
            refreshUiState(record.identity)
            RecoveryExecution.ClosingQueued(record.transactionId)
        } else {
            durableState(record.transactionId, record.identity)
        }
    }

    private fun persistTerminal(
        record: PendingMutation,
        state: PendingMutationState,
        response: ByteArray?,
        reference: String?,
    ): RecoveryExecution {
        val bytes = response ?: return manual(record)
        return if (store.persistTerminal(record.transactionId, record.identity, state, bytes, reference)) {
            refreshUiState(record.identity)
            when (state) {
                PendingMutationState.COMPLETED -> RecoveryExecution.Completed(record.transactionId)
                PendingMutationState.REJECTED -> RecoveryExecution.Rejected(record.transactionId)
                else -> error("Terminal state required")
            }
        } else {
            durableState(record.transactionId, record.identity)
        }
    }

    private fun wait(
        record: PendingMutation,
        semanticRetryAfter: RetryAfter? = null,
        httpRetryAfter: RetryAfter? = null,
        state: PendingMutationState = PendingMutationState.WAITING_RETRY,
    ): RecoveryExecution {
        if (record.attemptCount >= MAX_DISPATCHES) return manual(record)
        val now = clock()
        val localDelay = (1L shl (record.attemptCount - 1)) * 1_000 + jitterMillis()
        val nextEligibleAtMillis = saturatingAdd(now, maxOf(
            localDelay,
            semanticRetryAfter?.delayMillis(now) ?: 0L,
            httpRetryAfter?.delayMillis(now) ?: 0L,
        ))
        check(store.finishSending(
            record.transactionId,
            record.identity,
            state,
            record.attemptCount,
            nextEligibleAtMillis,
            record.reference,
        ))
        return scheduleOrFailure(record.transactionId, nextEligibleAtMillis, record.identity)
    }

    private fun scheduleOrFailure(
        transactionId: String,
        nextEligibleAtMillis: Long,
        scheduledIdentity: RecoveryIdentity,
    ): RecoveryExecution = try {
        scheduler.schedule(transactionId, nextEligibleAtMillis) { failure ->
            if (identity() != scheduledIdentity) return@schedule
            if (failure != null) {
                recoveredStartupIdentity = null
                refreshUiState(scheduledIdentity, RecoveryExecution.RetrySchedulingFailed(transactionId))
            } else if (_uiState.value.retrySchedulingFailedTransactionId == transactionId) {
                _uiState.value = RecoveryUiState(
                    identity = scheduledIdentity,
                    terminal = store.terminalRecovery(scheduledIdentity),
                )
            }
        }
        RecoveryExecution.WaitingRetry(transactionId)
    } catch (_: RuntimeException) {
        val failure = RecoveryExecution.RetrySchedulingFailed(transactionId)
        if (identity() == scheduledIdentity) refreshUiState(scheduledIdentity, failure)
        failure
    }

    private fun refreshUiState(
        owner: RecoveryIdentity,
        schedulingFailure: RecoveryExecution.RetrySchedulingFailed? = null,
    ) {
        val unresolved = store.unresolved(owner)
        _uiState.value = RecoveryUiState(
            identity = owner,
            terminal = store.terminalRecovery(owner),
            closingQueuedTransactionId = unresolved
                .firstOrNull { it.state == PendingMutationState.CLOSING_QUEUED }
                ?.transactionId,
            manualClosingTransactionId = unresolved
                .firstOrNull { manualClosingRequest(it, null) != null }
                ?.transactionId,
            authenticationRequiredTransactionId = unresolved
                .firstOrNull { it.state == PendingMutationState.AUTH_REQUIRED }
                ?.transactionId,
            retrySchedulingFailedTransactionId = schedulingFailure?.transactionId
                ?: _uiState.value.retrySchedulingFailedTransactionId,
            retrySchedulingFailure = schedulingFailure ?: _uiState.value.retrySchedulingFailure,
        )
    }

    private fun manualClosingRequest(record: PendingMutation, expectedProfile: String?): SubmitClosingRequestDto? {
        if (record.state != PendingMutationState.MANUAL_RECOVERY ||
            record.endpoint != MobilePosEndpoint.CLOSING_SUBMIT ||
            record.attemptCount < 1 ||
            record.bodyFormatVersion != PendingMutation.BODY_FORMAT_VERSION ||
            record.contentType != "application/json" ||
            record.serializerIdentity != MobilePosEndpoint.CLOSING_SUBMIT.serializerIdentity ||
            record.body.isEmpty()
        ) return null
        return runCatching {
            MobilePosRequest.validatePostBodyBytes(record.endpoint, record.body, kotlinx.serialization.json.Json)
            kotlinx.serialization.json.Json.decodeFromString(SubmitClosingRequestDto.serializer(), record.body.decodeToString())
        }.getOrNull()?.takeIf { expectedProfile == null || it.pos_profile == expectedProfile }
    }

    private fun retryAfterSeconds(value: kotlinx.serialization.json.JsonElement?): RetryAfter? =
        (value as? JsonPrimitive)?.content?.let(RetryAfter::parse)

    private fun RetryAfter.delayMillis(nowMillis: Long): Long = raw.toLongOrNull()
        ?.takeIf { it >= 0L }
        ?.let(::saturatingSecondsToMillis)
        ?: runCatching { synchronized(HTTP_DATE_FORMAT) { HTTP_DATE_FORMAT.parse(raw)?.time?.minus(nowMillis) } }
            .getOrNull()?.takeIf { it >= 0L } ?: 0L

    private fun saturatingSecondsToMillis(seconds: Long): Long =
        if (seconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else seconds * 1_000L

    private fun saturatingAdd(base: Long, delay: Long): Long =
        if (delay > Long.MAX_VALUE - base) Long.MAX_VALUE else base + delay

    private fun manual(record: PendingMutation): RecoveryExecution {
        store.markManualRecovery(record.transactionId, record.identity)
        return RecoveryExecution.ManualRecovery(record.transactionId)
    }

    private fun durableState(transactionId: String, owner: RecoveryIdentity): RecoveryExecution = when (
        store.find(transactionId, owner)?.state
    ) {
        PendingMutationState.MANUAL_RECOVERY -> RecoveryExecution.ManualRecovery(transactionId)
        PendingMutationState.AUTH_REQUIRED -> RecoveryExecution.AuthRequired
        PendingMutationState.WAITING_RETRY, PendingMutationState.SENDING -> RecoveryExecution.WaitingRetry(transactionId)
        PendingMutationState.CLOSING_QUEUED -> RecoveryExecution.ClosingQueued(transactionId)
        PendingMutationState.REJECTED -> RecoveryExecution.Rejected(transactionId)
        PendingMutationState.COMPLETED -> RecoveryExecution.Completed(transactionId)
        else -> RecoveryExecution.BlockedIdentity
    }

    companion object {
        private val LOWERCASE_UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        private val HTTP_DATE_FORMAT = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        private const val MAX_DISPATCHES = 6
        private val DELAYED_DISPATCH_STATES = setOf(
            PendingMutationState.PREPARED,
            PendingMutationState.WAITING_RETRY,
            PendingMutationState.AUTH_REQUIRED,
            PendingMutationState.REQUEST_IN_PROGRESS,
        )
        private val TRANSIENT_HTTP_STATUS_CODES = setOf(429, 500, 503)
        private val MUTATION_ENDPOINTS = setOf(
            MobilePosEndpoint.SESSIONS_OPEN,
            MobilePosEndpoint.SALES_SUBMIT,
            MobilePosEndpoint.SALES_CREATE_RETURN,
            MobilePosEndpoint.CLOSING_SUBMIT,
        )
    }
}
