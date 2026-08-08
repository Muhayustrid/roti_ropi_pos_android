package com.rotiropi.pos_erpnext.recovery

import com.rotiropi.pos_erpnext.data.ConnectivityStatus
import com.rotiropi.pos_erpnext.data.api.ApiErrorData
import com.rotiropi.pos_erpnext.data.api.ApiMeta
import com.rotiropi.pos_erpnext.data.api.ApiResult
import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import com.rotiropi.pos_erpnext.data.api.OpenSessionRequestDto
import com.rotiropi.pos_erpnext.data.openingRecoverySpec
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningRecoveryCoordinatorTest {
    @Test
    fun `opening replay uses identical UUID and serialized request body`() {
        val store = RecordingStore()
        val transport = SequencedTransport(
            ApiResult.TransportFailure(com.rotiropi.pos_erpnext.data.api.TransportFailureKind.TIMEOUT),
            success(),
        )
        val coordinator = coordinator(store, transport)
        val first = coordinator.execute(openingRecoverySpec(request(), Json))
        val id = (first as RecoveryExecution.WaitingRetry).transactionId

        val replay = coordinator.retry(id, nowMillis = Long.MAX_VALUE)

        assertTrue(replay is RecoveryExecution.Completed)
        assertEquals(listOf(id, id), transport.ids)
        assertEquals(transport.bodies[0].toList(), transport.bodies[1].toList())
        assertEquals(
            """{"pos_profile":"PROFILE-EXAMPLE","opening_balances":[]}""",
            transport.bodies[0].decodeToString(),
        )
    }

    @Test
    fun `session already open remains terminal for current-session reconciliation`() {
        val store = RecordingStore()
        val transport = SequencedTransport(
            ApiResult.ExpectedFailure(
                ApiErrorData(
                    code = "SESSION_ALREADY_OPEN",
                    message = "Session already open.",
                    details = emptyMap(),
                    retryable = false,
                ),
                ApiMeta("v1", "request", "now"),
                "already-open".encodeToByteArray(),
                statusCode = 409,
            ),
        )

        val result = coordinator(store, transport).execute(openingRecoverySpec(request(), Json))

        assertTrue(result is RecoveryExecution.Rejected)
        assertEquals(PendingMutationState.REJECTED, store.record?.state)
        assertEquals(1, transport.ids.size)
    }

    private fun coordinator(store: RecordingStore, transport: SequencedTransport) = RecoveryCoordinator(
        store = store,
        transport = transport,
        connectivity = { ConnectivityStatus.Online },
        identity = { RecoveryIdentity("cashier@example.test", "https://example.test", "client") },
        clock = { 0L },
        randomUuid = { "123e4567-e89b-42d3-a456-426614174000" },
        jitterMillis = { 0L },
    )

    private fun request() = OpenSessionRequestDto("PROFILE-EXAMPLE", emptyList())

    private fun success() = ApiResult.Success(
        data = com.rotiropi.pos_erpnext.data.api.OpenSessionResponseDto(
            com.rotiropi.pos_erpnext.data.api.OpeningSessionDto(
                "OPENING-EXAMPLE-0001",
                "PROFILE-EXAMPLE",
                "Example Company",
                "cashier@example.test",
                com.rotiropi.pos_erpnext.data.api.OpeningStatus.OPEN,
                "2026-08-03",
                "2026-08-03T08:00:00+07:00",
                emptyList(),
                emptyList(),
            )
        ),
        meta = ApiMeta("v1", "request", "now"),
        rawResponse = "opened".encodeToByteArray(),
    )

    private class SequencedTransport(vararg results: ApiResult<*>) : RecoveryTransport {
        private val results = ArrayDeque(results.toList())
        val ids = mutableListOf<String>()
        val bodies = mutableListOf<ByteArray>()

        @Suppress("UNCHECKED_CAST")
        override fun <T> execute(request: PendingMutation, deserializer: DeserializationStrategy<T>): ApiResult<T> {
            ids += request.transactionId
            bodies += request.body
            return results.removeFirst() as ApiResult<T>
        }
    }

    private class RecordingStore : PendingMutationStore {
        var record: PendingMutation? = null

        override fun prepare(record: PendingMutation): PendingMutationAdmission {
            if (this.record != null) return PendingMutationAdmission.UNRESOLVED_EXISTS
            this.record = record
            return PendingMutationAdmission.ACCEPTED
        }

        override fun beginSending(transactionId: String, expectedIdentity: RecoveryIdentity, expectedState: PendingMutationState, expectedAttemptCount: Int, expectedNextEligibleAtMillis: Long): PendingMutation? {
            val current = record ?: return null
            if (current.transactionId != transactionId || current.identity != expectedIdentity || current.state != expectedState || current.attemptCount != expectedAttemptCount) return null
            return current.copy(state = PendingMutationState.SENDING, attemptCount = current.attemptCount + 1).also { record = it }
        }

        override fun finishSending(transactionId: String, expectedIdentity: RecoveryIdentity, state: PendingMutationState, attemptCount: Int, nextEligibleAtMillis: Long, reference: String?): Boolean {
            val current = record ?: return false
            if (current.transactionId != transactionId || current.identity != expectedIdentity || current.state != PendingMutationState.SENDING) return false
            record = current.copy(state = state, attemptCount = attemptCount, nextEligibleAtMillis = nextEligibleAtMillis, reference = reference)
            return true
        }

        override fun markManualRecovery(transactionId: String, expectedIdentity: RecoveryIdentity): Boolean {
            val current = record ?: return false
            record = current.copy(state = PendingMutationState.MANUAL_RECOVERY)
            return true
        }

        override fun persistTerminal(transactionId: String, expectedIdentity: RecoveryIdentity, state: PendingMutationState, terminalResponse: ByteArray, reference: String?): Boolean {
            val current = record ?: return false
            record = current.copy(state = state, terminalResponse = terminalResponse, reference = reference)
            return true
        }

        override fun persistClosingQueued(transactionId: String, expectedIdentity: RecoveryIdentity, response: ByteArray, reference: String) = false
        override fun persistClosingStatusTerminal(transactionId: String, expectedIdentity: RecoveryIdentity, response: ByteArray, reference: String) = false
        override fun find(transactionId: String, expectedIdentity: RecoveryIdentity) = record?.takeIf { it.transactionId == transactionId && it.identity == expectedIdentity }
        override fun unresolved(expectedIdentity: RecoveryIdentity) = listOfNotNull(record?.takeIf { it.identity == expectedIdentity })
        override fun terminalRecovery(expectedIdentity: RecoveryIdentity) = record?.takeIf { it.identity == expectedIdentity && it.state.terminal }?.let { TerminalRecovery(it.transactionId, it.state) }
        override fun readTerminalResult(transactionId: String, expectedIdentity: RecoveryIdentity): ValidatedTerminalResult? = null
        override fun readClosingResult(transactionId: String, expectedIdentity: RecoveryIdentity): ValidatedClosingResult? = null
        override fun recoverStaleSending(transactionId: String, expectedIdentity: RecoveryIdentity) = false
        override fun acknowledge(token: TerminalReadToken, expectedIdentity: RecoveryIdentity) = false
        override fun logoutIfNoRecords(cleanup: () -> Unit): RecoveryLogoutBlocker? = null
    }
}
