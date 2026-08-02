package com.rotiropi.pos_erpnext.recovery

import com.rotiropi.pos_erpnext.data.ConnectivityStatus
import com.rotiropi.pos_erpnext.data.api.ApiMeta
import com.rotiropi.pos_erpnext.data.api.ApiResult
import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import com.rotiropi.pos_erpnext.data.api.TransportFailureKind
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCoordinatorTest {
    private val json = Json

    @Test
    fun knownOfflineDoesNotAllocatePersistOrSend() {
        val store = MemoryStore()
        val transport = RecordingTransport()
        val result = coordinator(store, transport, ConnectivityStatus.KnownOffline).execute(spec("one"))

        assertEquals(RecoveryExecution.NotStartedOffline, result)
        assertTrue(store.records.isEmpty())
        assertEquals(0, transport.calls)
    }

    @Test
    fun preparedMutationPersistsExactSerializedBytesBeforeSingleDispatch() {
        val store = MemoryStore()
        val transport = RecordingTransport()
        val result = coordinator(store, transport).execute(spec("sale"))

        assertTrue(result is RecoveryExecution.Completed)
        val record = store.records.single()
        assertEquals(
            "{\"pos_profile\":\"sale\",\"client_accepted_grand_total\":\"1.00\",\"items\":[],\"payments\":[]}"
                .encodeToByteArray().toList(),
            record.body.toList(),
        )
        assertEquals(record.transactionId, transport.keys.single())
        assertEquals(record.body.toList(), transport.bodies.single().toList())
        assertEquals(PendingMutationState.COMPLETED, record.state)
        assertTrue(UUID.fromString(record.transactionId).toString() == record.transactionId)
    }

    @Test
    fun successPersistsCompleteTerminalResponseBeforeReturningCompleted() {
        val store = MemoryStore()
        val transport = RecordingTransport()

        assertTrue(coordinator(store, transport).execute(spec("sale")) is RecoveryExecution.Completed)

        assertEquals("ok", store.records.single().terminalResponse!!.decodeToString())
    }

    @Test
    fun mutation401PersistsAuthRequiredWithoutHiddenSecondDispatch() {
        val store = MemoryStore()
        val transport = RecordingTransport(ApiResult.TransportFailure(TransportFailureKind.AUTHENTICATION_REQUIRED, 401))
        val result = coordinator(store, transport).execute(spec("sale"))

        assertEquals(RecoveryExecution.AuthRequired, result)
        assertEquals(PendingMutationState.AUTH_REQUIRED, store.records.single().state)
        assertEquals(1, transport.calls)
    }

    @Test
    fun replayUsesPersistedBytesAndRejectsDifferentCashier() {
        val store = MemoryStore()
        val firstTransport = RecordingTransport(ApiResult.TransportFailure(TransportFailureKind.TIMEOUT))
        coordinator(store, firstTransport).execute(spec("original"))
        val original = store.records.single()
        val secondTransport = RecordingTransport()

        val wrongUser = coordinator(store, secondTransport, cashier = "other")
        assertEquals(RecoveryExecution.BlockedIdentity, wrongUser.retry(original.transactionId))
        assertEquals(0, secondTransport.calls)

        val sameUser = coordinator(store, secondTransport)
        assertTrue(sameUser.retry(original.transactionId, nowMillis = Long.MAX_VALUE) is RecoveryExecution.Completed)
        assertEquals(original.body.toList(), secondTransport.bodies.single().toList())
        assertEquals(original.transactionId, secondTransport.keys.single())
        assertNotEquals("\"changed\"".encodeToByteArray().toList(), secondTransport.bodies.single().toList())
    }

    @Test
    fun manualRecoveryNeverDispatchesSupportRecord() {
        val store = MemoryStore()
        val transport = RecordingTransport()
        val record = preparedStoreRecord(PendingMutationState.MANUAL_RECOVERY)
        store.records += record

        assertEquals(RecoveryExecution.ManualRecovery(record.transactionId), coordinator(store, transport).retry(record.transactionId))
        assertEquals(0, transport.calls)
    }

    @Test
    fun stableRejectionReturnsRejectedAfterTerminalPersistence() {
        val store = MemoryStore()
        val transport = RecordingTransport(
            ApiResult.ExpectedFailure(
                com.rotiropi.pos_erpnext.data.api.ApiErrorData("INVALID", "no", emptyMap(), false),
                ApiMeta("v1", "request", "now"),
                "rejected".encodeToByteArray(),
            ),
        )

        assertEquals(RecoveryExecution.Rejected("123e4567-e89b-42d3-a456-426614174000"), coordinator(store, transport).execute(spec("sale")))
        assertEquals(PendingMutationState.REJECTED, store.records.single().state)
        assertEquals("rejected", store.records.single().terminalResponse!!.decodeToString())
    }

    @Test
    fun postCasManualSupportRecordNeverDispatches() {
        val store = MemoryStore().apply {
            beginSendingOverride = { it.copy(state = PendingMutationState.MANUAL_RECOVERY, body = ByteArray(0)) }
        }
        val record = preparedStoreRecord(PendingMutationState.WAITING_RETRY)
        store.records += record
        val transport = RecordingTransport()

        assertEquals(RecoveryExecution.ManualRecovery(record.transactionId), coordinator(store, transport).retry(record.transactionId, nowMillis = Long.MAX_VALUE))
        assertEquals(0, transport.calls)
    }

    @Test
    fun manualAdmissionReturnsDurableEvidenceIdNotNewActionId() {
        val existingId = "123e4567-e89b-42d3-a456-426614174000"
        val generatedId = "223e4567-e89b-42d3-a456-426614174000"
        val store = MemoryStore().apply {
            records += preparedStoreRecord(PendingMutationState.MANUAL_RECOVERY)
            prepareResult = PendingMutationAdmission.MANUAL_RECOVERY_REQUIRED(existingId)
        }
        val transport = RecordingTransport()
        val coordinator = RecoveryCoordinator(
            store = store,
            transport = transport,
            connectivity = { ConnectivityStatus.Online },
            identity = { RecoveryIdentity("cashier-1", "https://example.test", "client") },
            randomUuid = { generatedId },
        )

        assertEquals(RecoveryExecution.ManualRecovery(existingId), coordinator.execute(spec("sale")))
        assertEquals(PendingMutationState.MANUAL_RECOVERY, store.find(existingId, store.records.single().identity)?.state)
        assertEquals(null, store.find(generatedId, store.records.single().identity))
        assertEquals(0, transport.calls)
    }

    @Test
    fun terminalCryptoFailureReturnsDurableManualRecoveryWithoutThrowing() {
        val store = MemoryStore().apply { terminalResult = false }
        val transport = RecordingTransport()

        assertEquals(RecoveryExecution.ManualRecovery("123e4567-e89b-42d3-a456-426614174000"), coordinator(store, transport).execute(spec("sale")))
        assertEquals(PendingMutationState.MANUAL_RECOVERY, store.records.single().state)
    }

    @Test
    fun acknowledgementsRaceAndOnlyOneDeletesTerminalEvidence() {
        val store = MemoryStore()
        val completed = preparedStoreRecord(PendingMutationState.COMPLETED)
        store.records += completed
        val coordinator = coordinator(store, RecordingTransport())
        val outcomes = java.util.Collections.synchronizedList(mutableListOf<RecoveryAcknowledgement>())
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)

        repeat(2) {
            Thread {
                start.await(5, TimeUnit.SECONDS)
                outcomes += coordinator.acknowledge(completed.transactionId)
                done.countDown()
            }.start()
        }
        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))

        assertEquals(1, outcomes.count { it is RecoveryAcknowledgement.Acknowledged })
        assertEquals(1, outcomes.count { it is RecoveryAcknowledgement.NotAcknowledged })
        assertTrue(store.records.isEmpty())
    }

    @Test
    fun terminalAcknowledgementDeletesOnlyForSameCashierAfterDurableTerminal() {
        val store = MemoryStore()
        val completed = preparedStoreRecord(PendingMutationState.COMPLETED)
        store.records += completed
        val coordinator = coordinator(store, RecordingTransport())

        assertEquals(RecoveryAcknowledgement.Acknowledged(completed.transactionId), coordinator.acknowledge(completed.transactionId))
        assertTrue(store.records.isEmpty())
    }

    @Test
    fun wrongCashierCannotAcknowledgeOrRevealTerminalEvidence() {
        val store = MemoryStore()
        val completed = preparedStoreRecord(PendingMutationState.REJECTED)
        store.records += completed

        assertEquals(
            RecoveryAcknowledgement.NotAcknowledged(completed.transactionId),
            coordinator(store, RecordingTransport(), cashier = "other").acknowledge(completed.transactionId),
        )
        assertEquals(listOf(completed), store.records)
    }

    @Test
    fun failedTerminalAcknowledgementPreservesEvidence() {
        val store = MemoryStore().apply { acknowledgeResult = false }
        val completed = preparedStoreRecord(PendingMutationState.COMPLETED)
        store.records += completed

        assertEquals(
            RecoveryAcknowledgement.NotAcknowledged(completed.transactionId),
            coordinator(store, RecordingTransport()).acknowledge(completed.transactionId),
        )
        assertEquals(listOf(completed), store.records)
    }

    @Test
    fun concurrentRetriesDispatchOnceAndTimeoutCannotRegressTerminal() {
        val store = MemoryStore()
        val record = preparedStoreRecord(PendingMutationState.WAITING_RETRY)
        store.records += record
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = RecordingTransport()
        val second = RecordingTransport(ApiResult.TransportFailure(TransportFailureKind.TIMEOUT))
        first.beforeResult = { started.countDown(); release.await(5, TimeUnit.SECONDS) }
        val one = coordinator(store, first)
        val two = coordinator(store, second)
        val thread = Thread { one.retry(record.transactionId, nowMillis = Long.MAX_VALUE) }

        thread.start()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertEquals(RecoveryExecution.WaitingRetry(record.transactionId), two.retry(record.transactionId, nowMillis = Long.MAX_VALUE))
        release.countDown()
        thread.join(5_000)

        assertEquals(1, first.calls + second.calls)
        assertEquals(PendingMutationState.COMPLETED, store.records.single().state)
    }

    @Test
    fun sixthFailedDispatchEntersManualRecoveryAndNeverSendsSeventh() {
        val store = MemoryStore()
        val transport = RecordingTransport(ApiResult.TransportFailure(TransportFailureKind.SERVER_UNAVAILABLE, 500))
        val coordinator = coordinator(store, transport)
        coordinator.execute(spec("sale"))
        val id = store.records.single().transactionId
        repeat(6) { coordinator.retry(id, nowMillis = Long.MAX_VALUE) }

        assertEquals(6, transport.calls)
        assertEquals(PendingMutationState.MANUAL_RECOVERY, store.records.single().state)
    }

    @Test
    fun schedulerFailureLeavesPersistedRetryVisibleWithoutClaimingScheduled() {
        val store = MemoryStore()
        val coordinator = RecoveryCoordinator(
            store = store,
            transport = RecordingTransport(ApiResult.TransportFailure(TransportFailureKind.TIMEOUT)),
            connectivity = { ConnectivityStatus.Online },
            identity = { RecoveryIdentity("cashier-1", "https://example.test", "client") },
            clock = { 0L },
            scheduler = throwingScheduler(),
        )

        val result = coordinator.execute(spec("sale"))

        assertEquals(
            RecoveryExecution.RetrySchedulingFailed(store.records.single().transactionId),
            result,
        )
        assertEquals(PendingMutationState.WAITING_RETRY, store.records.single().state)
    }

    @Test
    fun retryAfterDelayWinsOverLocalDelayAndPersistsEligibility() {
        val store = MemoryStore()
        val transport = RecordingTransport(
            ApiResult.TransportFailure(
                TransportFailureKind.RATE_LIMITED,
                429,
                com.rotiropi.pos_erpnext.data.api.RetryAfter.parse("3"),
            ),
        )

        assertTrue(coordinator(store, transport).execute(spec("sale")) is RecoveryExecution.WaitingRetry)

        val record = store.records.single()
        assertEquals(1, record.attemptCount)
        assertEquals(4_000L, record.nextEligibleAtMillis)
    }

    @Test
    fun httpDateRetryAfterPersistsDelayOnApi23CompatiblePath() {
        val store = MemoryStore()
        val transport = RecordingTransport(
            ApiResult.TransportFailure(
                TransportFailureKind.SERVER_UNAVAILABLE,
                503,
                com.rotiropi.pos_erpnext.data.api.RetryAfter.parse("Thu, 01 Jan 1970 00:00:04 GMT"),
            ),
        )

        assertTrue(coordinator(store, transport).execute(spec("sale")) is RecoveryExecution.WaitingRetry)

        assertEquals(4_000L, store.records.single().nextEligibleAtMillis)
    }

    @Test
    fun requestInProgressPersistsServerDelayBeforeReturningWaitingRetry() {
        val store = MemoryStore()
        val transport = RecordingTransport(
            ApiResult.ExpectedFailure(
                com.rotiropi.pos_erpnext.data.api.ApiErrorData(
                    "REQUEST_IN_PROGRESS",
                    "processing",
                    mapOf("retry_after_seconds" to kotlinx.serialization.json.JsonPrimitive(3)),
                    true,
                ),
                ApiMeta("v1", "request", "now"),
                "processing".encodeToByteArray(),
            ),
        )

        assertTrue(
            coordinator(store, transport).execute(validClosingSpec()) is RecoveryExecution.WaitingRetry,
        )

        val record = store.records.single()
        assertEquals(PendingMutationState.REQUEST_IN_PROGRESS, record.state)
        assertEquals(1, record.attemptCount)
        assertEquals(4_000L, record.nextEligibleAtMillis)
    }

    @Test
    fun startupRecoveryConvertsOnlyStaleSendingAndSchedulesEligibleRecords() {
        val store = MemoryStore().apply {
            records += preparedStoreRecord(PendingMutationState.SENDING)
            records += preparedStoreRecord(PendingMutationState.WAITING_RETRY).copy(
                transactionId = "223e4567-e89b-42d3-a456-426614174000",
                nextEligibleAtMillis = 500L,
            )
            records += preparedStoreRecord(PendingMutationState.WAITING_RETRY).copy(
                transactionId = "323e4567-e89b-42d3-a456-426614174000",
                nextEligibleAtMillis = 2_000L,
            )
        }
        val scheduled = mutableListOf<Pair<String, Long>>()
        val coordinator = RecoveryCoordinator(
            store = store,
            transport = RecordingTransport(),
            connectivity = { ConnectivityStatus.Online },
            identity = { RecoveryIdentity("cashier-1", "https://example.test", "client") },
            clock = { 1_000L },
            scheduler = recordingScheduler { id, eligibleAt -> scheduled += id to eligibleAt },
        )

        coordinator.recoverAtAuthenticatedStartup()

        assertEquals(PendingMutationState.WAITING_RETRY, store.records.first().state)
        assertEquals(
            listOf(
                "123e4567-e89b-42d3-a456-426614174000" to 0L,
                "223e4567-e89b-42d3-a456-426614174000" to 500L,
                "323e4567-e89b-42d3-a456-426614174000" to 2_000L,
            ),
            scheduled,
        )
    }

    @Test
    fun invalidPreparedBytesDoNotAllocateUuidPersistOrDispatch() {
        val store = MemoryStore()
        val transport = RecordingTransport()
        var uuidCalls = 0
        val coordinator = RecoveryCoordinator(
            store = store,
            transport = transport,
            connectivity = { ConnectivityStatus.Online },
            identity = { RecoveryIdentity("cashier-1", "https://example.test", "client") },
            randomUuid = { uuidCalls++; "123e4567-e89b-42d3-a456-426614174000" },
        )

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            coordinator.execute(invalidSpec("sale"))
        }

        assertEquals(0, uuidCalls)
        assertTrue(store.records.isEmpty())
        assertEquals(0, transport.calls)
    }

    @Test
    fun hugeSemanticRetryAfterSaturatesEligibilityInsteadOfRetryingEarly() {
        val store = MemoryStore()
        val transport = RecordingTransport(
            ApiResult.ExpectedFailure(
                com.rotiropi.pos_erpnext.data.api.ApiErrorData(
                    "REQUEST_IN_PROGRESS", "processing", mapOf("retry_after_seconds" to JsonPrimitive(Long.MAX_VALUE.toString())), true,
                ),
                ApiMeta("v1", "request", "now"),
                "processing".encodeToByteArray(),
                statusCode = 409,
            ),
        )

        assertTrue(coordinatorAt(Long.MAX_VALUE - 10L, store, transport).execute(validClosingSpec()) is RecoveryExecution.WaitingRetry)
        assertEquals(Long.MAX_VALUE, store.records.single().nextEligibleAtMillis)
    }

    @Test
    fun hugeHttpRetryAfterSaturatesEligibilityInsteadOfRetryingEarly() {
        val store = MemoryStore()
        val transport = RecordingTransport(
            ApiResult.ExpectedFailure(
                com.rotiropi.pos_erpnext.data.api.ApiErrorData(
                    "REQUEST_IN_PROGRESS", "processing", emptyMap(), true,
                ),
                ApiMeta("v1", "request", "now"),
                "processing".encodeToByteArray(),
                com.rotiropi.pos_erpnext.data.api.RetryAfter.parse(Long.MAX_VALUE.toString()),
                409,
            ),
        )

        assertTrue(coordinatorAt(Long.MAX_VALUE - 10L, store, transport).execute(validClosingSpec()) is RecoveryExecution.WaitingRetry)
        assertEquals(Long.MAX_VALUE, store.records.single().nextEligibleAtMillis)
    }

    @Test
    fun semanticAndHttpRetryAfterUseLongestValidServerDelay() {
        val store = MemoryStore()
        val transport = RecordingTransport(
            ApiResult.ExpectedFailure(
                com.rotiropi.pos_erpnext.data.api.ApiErrorData(
                    "REQUEST_IN_PROGRESS", "processing", mapOf("retry_after_seconds" to JsonPrimitive(1)), true,
                ),
                ApiMeta("v1", "request", "now"),
                "processing".encodeToByteArray(),
                com.rotiropi.pos_erpnext.data.api.RetryAfter.parse("120"),
                409,
            ),
        )

        assertTrue(coordinator(store, transport).execute(validClosingSpec()) is RecoveryExecution.WaitingRetry)
        assertEquals(121_000L, store.records.single().nextEligibleAtMillis)
    }

    @Test
    fun processRestartRecoversOnlyNamedStaleSendingThenReplaysStoredBytes() {
        val store = MemoryStore().apply {
            records += preparedStoreRecord(PendingMutationState.SENDING).copy(attemptCount = 1, nextEligibleAtMillis = 2_000L)
            records += preparedStoreRecord(PendingMutationState.SENDING).copy(
                transactionId = "223e4567-e89b-42d3-a456-426614174000",
            )
        }
        val transport = RecordingTransport()
        val coordinator = coordinator(store, transport)
        val stale = store.records.first()

        assertTrue(coordinator.recoverStaleSending(stale.transactionId))
        assertEquals(PendingMutationState.WAITING_RETRY, store.records.first().state)
        assertEquals(1, store.records.first().attemptCount)
        assertEquals(2_000L, store.records.first().nextEligibleAtMillis)
        assertEquals(PendingMutationState.SENDING, store.records.last().state)
        assertTrue(coordinator.retry(stale.transactionId, nowMillis = Long.MAX_VALUE) is RecoveryExecution.Completed)
        assertEquals(1, transport.calls)
    }

    @Test
    fun activeSendingIsNotReclaimedByOrdinaryRetry() {
        val store = MemoryStore().apply { records += preparedStoreRecord(PendingMutationState.SENDING) }
        val transport = RecordingTransport()

        assertEquals(RecoveryExecution.WaitingRetry(store.records.single().transactionId), coordinator(store, transport).retry(store.records.single().transactionId))
        assertEquals(PendingMutationState.SENDING, store.records.single().state)
        assertEquals(0, transport.calls)
    }

    @Test
    fun ineligibleRetryReschedulesPersistedEligibilityWithoutDispatch() {
        val store = MemoryStore().apply {
            records += preparedStoreRecord(PendingMutationState.WAITING_RETRY).copy(nextEligibleAtMillis = 2_000L)
        }
        val transport = RecordingTransport()
        val scheduled = mutableListOf<Pair<String, Long>>()
        val coordinator = RecoveryCoordinator(
            store = store,
            transport = transport,
            connectivity = { ConnectivityStatus.Online },
            identity = { RecoveryIdentity("cashier-1", "https://example.test", "client") },
            clock = { 1_000L },
            scheduler = recordingScheduler { id, eligibleAt -> scheduled += id to eligibleAt },
        )

        assertEquals(RecoveryExecution.WaitingRetry(store.records.single().transactionId), coordinator.retry(store.records.single().transactionId))
        assertEquals(listOf(store.records.single().transactionId to 2_000L), scheduled)
        assertEquals(0, transport.calls)
    }

    @Test
    fun startupKeepsDelayedRetriesAndRequestInProgressWithoutEarlyDispatch() {
        val store = MemoryStore().apply {
            records += preparedStoreRecord(PendingMutationState.SENDING)
            records += preparedStoreRecord(PendingMutationState.WAITING_RETRY).copy(
                transactionId = "223e4567-e89b-42d3-a456-426614174000",
                nextEligibleAtMillis = 2_000L,
            )
            records += preparedStoreRecord(PendingMutationState.REQUEST_IN_PROGRESS).copy(
                transactionId = "323e4567-e89b-42d3-a456-426614174000",
                endpoint = MobilePosEndpoint.CLOSING_SUBMIT,
                nextEligibleAtMillis = 3_000L,
            )
        }
        val scheduled = mutableListOf<Pair<String, Long>>()
        val transport = RecordingTransport()
        val coordinator = RecoveryCoordinator(
            store = store,
            transport = transport,
            connectivity = { ConnectivityStatus.Online },
            identity = { RecoveryIdentity("cashier-1", "https://example.test", "client") },
            clock = { 1_000L },
            scheduler = recordingScheduler { id, eligibleAt -> scheduled += id to eligibleAt },
        )

        coordinator.recoverAtAuthenticatedStartup()

        assertEquals(PendingMutationState.WAITING_RETRY, store.records.first().state)
        assertEquals(
            listOf(
                "123e4567-e89b-42d3-a456-426614174000" to 0L,
                "223e4567-e89b-42d3-a456-426614174000" to 2_000L,
                "323e4567-e89b-42d3-a456-426614174000" to 3_000L,
            ),
            scheduled,
        )
        assertEquals(0, transport.calls)
    }

    @Test
    fun schedulerFailuresFromStartupAndEarlyRetryAreTypedAndVisible() {
        val startup = MemoryStore().apply {
            records += preparedStoreRecord(PendingMutationState.REQUEST_IN_PROGRESS).copy(
                endpoint = MobilePosEndpoint.CLOSING_SUBMIT,
                nextEligibleAtMillis = 2_000L,
            )
        }
        val startupCoordinator = RecoveryCoordinator(
            store = startup,
            transport = RecordingTransport(),
            connectivity = { ConnectivityStatus.Online },
            identity = { RecoveryIdentity("cashier-1", "https://example.test", "client") },
            clock = { 1_000L },
            scheduler = throwingScheduler(),
        )

        assertEquals(
            listOf(RecoveryExecution.RetrySchedulingFailed(startup.records.single().transactionId)),
            startupCoordinator.recoverAtAuthenticatedStartup(),
        )
        assertEquals(
            startup.records.single().transactionId,
            startupCoordinator.uiState.value.retrySchedulingFailedTransactionId,
        )

        val early = MemoryStore().apply {
            records += preparedStoreRecord(PendingMutationState.WAITING_RETRY).copy(nextEligibleAtMillis = 2_000L)
        }
        val earlyCoordinator = RecoveryCoordinator(
            store = early,
            transport = RecordingTransport(),
            connectivity = { ConnectivityStatus.Online },
            identity = { RecoveryIdentity("cashier-1", "https://example.test", "client") },
            clock = { 1_000L },
            scheduler = throwingScheduler(),
        )

        assertEquals(
            RecoveryExecution.RetrySchedulingFailed(early.records.single().transactionId),
            earlyCoordinator.retry(early.records.single().transactionId),
        )
        assertEquals(PendingMutationState.WAITING_RETRY, early.records.single().state)
    }

    @Test
    fun sameCashierReauthenticationSchedulesAuthRequiredAfterPriorStartupWithoutChangingEvidence() {
        val store = MemoryStore()
        val scheduled = mutableListOf<Pair<String, Long>>()
        val coordinator = RecoveryCoordinator(
            store = store,
            transport = RecordingTransport(),
            connectivity = { ConnectivityStatus.Online },
            identity = { RecoveryIdentity("cashier-1", "https://example.test", "client") },
            clock = { 1_000L },
            scheduler = recordingScheduler { id, eligibleAt -> scheduled += id to eligibleAt },
        )
        coordinator.recoverAtAuthenticatedStartup()
        val record = preparedStoreRecord(PendingMutationState.AUTH_REQUIRED)
        store.records += record

        assertEquals(emptyList<RecoveryExecution>(), coordinator.recoverAtAuthenticatedStartup())
        assertEquals(
            listOf(RecoveryExecution.WaitingRetry(record.transactionId)),
            coordinator.resumeAfterSuccessfulReauthentication(),
        )
        assertEquals(listOf(record.transactionId to 0L), scheduled)
        assertEquals(PendingMutationState.AUTH_REQUIRED, store.records.single().state)
        assertEquals("stored", store.records.single().body.decodeToString())
    }

    @Test
    fun asyncSchedulerFailureKeepsRowAndAllowsLaterAuthenticatedStartupReschedule() {
        val store = MemoryStore().apply { records += preparedStoreRecord(PendingMutationState.WAITING_RETRY) }
        val scheduler = DeferredScheduler()
        val coordinator = RecoveryCoordinator(
            store = store,
            transport = RecordingTransport(),
            connectivity = { ConnectivityStatus.Online },
            identity = { RecoveryIdentity("cashier-1", "https://example.test", "client") },
            scheduler = scheduler,
        )

        assertEquals(
            listOf(RecoveryExecution.WaitingRetry(store.records.single().transactionId)),
            coordinator.recoverAtAuthenticatedStartup(),
        )
        scheduler.fail()
        assertEquals(store.records.single().transactionId, coordinator.uiState.value.retrySchedulingFailedTransactionId)
        assertEquals(
            RecoveryExecution.RetrySchedulingFailed(store.records.single().transactionId),
            coordinator.uiState.value.retrySchedulingFailure,
        )
        assertEquals(PendingMutationState.WAITING_RETRY, store.records.single().state)

        assertEquals(
            listOf(RecoveryExecution.WaitingRetry(store.records.single().transactionId)),
            coordinator.recoverAtAuthenticatedStartup(),
        )
        scheduler.succeed()
        assertEquals(null, coordinator.uiState.value.retrySchedulingFailedTransactionId)
    }

    @Test
    fun asyncSchedulerFailureForOldIdentityDoesNotPublishIntoNewIdentity() {
        val store = MemoryStore().apply { records += preparedStoreRecord(PendingMutationState.WAITING_RETRY) }
        val scheduler = DeferredScheduler()
        var owner = RecoveryIdentity("cashier-1", "https://example.test", "client")
        val coordinator = RecoveryCoordinator(
            store = store,
            transport = RecordingTransport(),
            connectivity = { ConnectivityStatus.Online },
            identity = { owner },
            scheduler = scheduler,
        )
        coordinator.recoverAtAuthenticatedStartup()

        owner = owner.copy(cashier = "cashier-2")
        scheduler.fail()

        assertEquals(null, coordinator.uiState.value.retrySchedulingFailedTransactionId)
        assertEquals(RecoveryIdentity("cashier-1", "https://example.test", "client"), coordinator.uiState.value.identity)
    }

    @Test
    fun activeBootstrapIdentitySkipsColdBootstrapBeforeExactRetry() {
        val calls = mutableListOf<String>()
        val id = "123e4567-e89b-42d3-a456-426614174000"
        val coldRecovery = ColdRecovery(
            hasStoredAuth = { calls += "auth"; true },
            currentBootstrapIdentity = {
                calls += "identity"
                RecoveryIdentity("cashier-1", "https://example.test", "client")
            },
            bootstrap = { error("bootstrap must not replace active repository state") },
            retryAction = { transactionId -> calls += "retry:$transactionId"; RecoveryExecution.Completed(transactionId) },
        )

        assertEquals(RecoveryExecution.Completed(id), coldRecovery.retry(id))
        assertEquals(listOf("auth", "identity", "retry:$id"), calls)
    }

    @Test
    fun nullBootstrapIdentityUsesAuthoritativeBootstrapBeforeExactRetry() {
        val calls = mutableListOf<String>()
        val id = "123e4567-e89b-42d3-a456-426614174000"
        val coldRecovery = ColdRecovery(
            hasStoredAuth = { calls += "auth"; true },
            currentBootstrapIdentity = { calls += "identity"; null },
            bootstrap = { calls += "bootstrap"; true },
            retryAction = { transactionId -> calls += "retry:$transactionId"; RecoveryExecution.Completed(transactionId) },
        )

        assertEquals(RecoveryExecution.Completed(id), coldRecovery.retry(id))
        assertEquals(listOf("auth", "identity", "bootstrap", "retry:$id"), calls)
    }

    @Test
    fun coldRecoveryDoesNotRetryWhenAuthOrBootstrapUnavailable() {
        val unauthenticated = ColdRecovery(
            { false },
            { error("identity") },
            { error("bootstrap") },
            retryAction = { error("retry") },
        )
        val unavailable = ColdRecovery(
            { true },
            { null },
            { false },
            retryAction = { error("retry") },
        )

        assertEquals(null, unauthenticated.retry("id"))
        assertEquals(null, unavailable.retry("id"))
    }

    @Test
    fun wrongActiveBootstrapIdentityDoesNotBootstrapSwitchOrDispatch() {
        val wrongIdentity = RecoveryIdentity("other", "https://example.test", "client")
        var metadataLookup = 0
        val coldRecovery = ColdRecovery(
            hasStoredAuth = { true },
            currentBootstrapIdentity = { wrongIdentity },
            bootstrap = { error("wrong identity must not bootstrap-switch") },
            retryAction = {
                metadataLookup++
                // Coordinator's identity-bound store lookup returns before decrypt/transport.
                RecoveryExecution.BlockedIdentity
            },
        )

        assertEquals(RecoveryExecution.BlockedIdentity, coldRecovery.retry("123e4567-e89b-42d3-a456-426614174000"))
        assertEquals(1, metadataLookup)
    }

    @Test
    fun terminalRecoveryStateTracksIdentityBoundAcknowledgement() {
        val store = MemoryStore().apply {
            records += preparedStoreRecord(PendingMutationState.COMPLETED)
        }
        val coordinator = coordinator(store, RecordingTransport())

        coordinator.refreshUiState()
        assertEquals(
            TerminalRecovery("123e4567-e89b-42d3-a456-426614174000", PendingMutationState.COMPLETED),
            coordinator.uiState.value.terminal,
        )
        assertEquals(
            RecoveryAcknowledgement.Acknowledged("123e4567-e89b-42d3-a456-426614174000"),
            coordinator.acknowledge("123e4567-e89b-42d3-a456-426614174000"),
        )
        assertEquals(null, coordinator.uiState.value.terminal)
    }

    @Test
    fun retryUsesPersistedRequestInProgressState() {
        val store = MemoryStore().apply {
            records += preparedStoreRecord(PendingMutationState.REQUEST_IN_PROGRESS).copy(
                endpoint = MobilePosEndpoint.CLOSING_SUBMIT,
            )
        }
        val transport = RecordingTransport()

        assertTrue(coordinator(store, transport).retry(store.records.single().transactionId, nowMillis = Long.MAX_VALUE) is RecoveryExecution.Completed)
        assertEquals(1, transport.calls)
    }

    @Test
    fun rejectsNonMutationBeforeUuidSerializationPersistenceOrDispatch() {
        val store = MemoryStore()
        val transport = RecordingTransport()
        var uuidCalls = 0
        val coordinator = RecoveryCoordinator(
            store = store,
            transport = transport,
            connectivity = { ConnectivityStatus.Online },
            identity = { RecoveryIdentity("cashier-1", "https://example.test", "client") },
            randomUuid = { uuidCalls++; "123e4567-e89b-42d3-a456-426614174000" },
        )

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            coordinator.execute(spec("sale").copy(endpoint = MobilePosEndpoint.CATALOG_SCAN))
        }

        assertEquals(0, uuidCalls)
        assertTrue(store.records.isEmpty())
        assertEquals(0, transport.calls)
    }

    @Test
    fun lostTerminalResponseBytesEntersManualRecoveryWithoutThrowing() {
        val store = MemoryStore()
        val transport = RecordingTransport(ApiResult.Success("ok", ApiMeta("v1", "request", "now")))

        assertEquals(
            RecoveryExecution.ManualRecovery("123e4567-e89b-42d3-a456-426614174000"),
            coordinator(store, transport).execute(spec("sale")),
        )
        assertEquals(PendingMutationState.MANUAL_RECOVERY, store.records.single().state)
        assertEquals(1, transport.calls)
    }

    @Test
    fun idempotencyKeyReuseOutranksTransientHttpStatusAndNeverSchedulesReplay() {
        listOf(401, 429, 500, 503).forEach { statusCode ->
            val store = MemoryStore()
            val transport = RecordingTransport(
                ApiResult.ExpectedFailure(
                    com.rotiropi.pos_erpnext.data.api.ApiErrorData("IDEMPOTENCY_KEY_REUSED", "conflict", emptyMap(), false),
                    ApiMeta("v1", "request", "now"),
                    "conflict".encodeToByteArray(),
                    statusCode = statusCode,
                ),
            )
            val scheduled = mutableListOf<Pair<String, Long>>()
            val coordinator = RecoveryCoordinator(
                store = store,
                transport = transport,
                connectivity = { ConnectivityStatus.Online },
                identity = { RecoveryIdentity("cashier-1", "https://example.test", "client") },
                randomUuid = { "123e4567-e89b-42d3-a456-426614174000" },
                scheduler = recordingScheduler { id, eligibleAt -> scheduled += id to eligibleAt },
            )

            assertEquals(RecoveryExecution.ManualRecovery("123e4567-e89b-42d3-a456-426614174000"), coordinator.execute(spec("sale")))
            assertEquals(PendingMutationState.MANUAL_RECOVERY, store.records.single().state)
            assertTrue(scheduled.isEmpty())
        }
    }

    @Test
    fun requestInProgressOutranksTransientHttpStatusForClosingOnly() {
        val store = MemoryStore()
        val transport = RecordingTransport(
            ApiResult.ExpectedFailure(
                com.rotiropi.pos_erpnext.data.api.ApiErrorData(
                    "REQUEST_IN_PROGRESS", "processing", mapOf("retry_after_seconds" to kotlinx.serialization.json.JsonPrimitive(3)), true,
                ),
                ApiMeta("v1", "request", "now"),
                "processing".encodeToByteArray(),
                statusCode = 503,
            ),
        )

        assertTrue(coordinator(store, transport).execute(validClosingSpec()) is RecoveryExecution.WaitingRetry)
        assertEquals(PendingMutationState.REQUEST_IN_PROGRESS, store.records.single().state)
    }

    @Test
    fun stableTransientHttpFailuresPersistRetryInsteadOfTerminalRejection() {
        listOf(429, 500, 503).forEach { statusCode ->
            val store = MemoryStore()
            val transport = RecordingTransport(
                ApiResult.ExpectedFailure(
                    com.rotiropi.pos_erpnext.data.api.ApiErrorData("TEMPORARY", "retry", emptyMap(), true),
                    ApiMeta("v1", "request", "now"),
                    "retry".encodeToByteArray(),
                    statusCode = statusCode,
                ),
            )

            assertTrue(coordinator(store, transport).execute(spec("sale")) is RecoveryExecution.WaitingRetry)
            assertEquals(PendingMutationState.WAITING_RETRY, store.records.single().state)
            assertEquals(1, transport.calls)
        }
    }

    @Test
    fun authRequiredPublishesReauthenticationDirection() {
        val store = MemoryStore()
        val transport = RecordingTransport(ApiResult.TransportFailure(TransportFailureKind.AUTHENTICATION_REQUIRED, 401))
        val coordinator = coordinator(store, transport)

        assertEquals(RecoveryExecution.AuthRequired, coordinator.execute(spec("sale")))

        assertEquals(
            "123e4567-e89b-42d3-a456-426614174000",
            coordinator.uiState.value.authenticationRequiredTransactionId,
        )
    }

    @Test
    fun coldRecoveryReclaimsStaleSendingAfterIdentityBootstrapBeforeRetry() {
        val calls = mutableListOf<String>()
        val coldRecovery = ColdRecovery(
            hasStoredAuth = { true },
            currentBootstrapIdentity = { RecoveryIdentity("cashier-1", "https://example.test", "client") },
            bootstrap = { error("bootstrap must not replace active identity") },
            recoverStaleSending = { calls += "recover:$it"; true },
            retryAction = { calls += "retry:$it"; RecoveryExecution.WaitingRetry(it) },
        )

        val id = "123e4567-e89b-42d3-a456-426614174000"
        assertEquals(RecoveryExecution.WaitingRetry(id), coldRecovery.retry(id))
        assertEquals(listOf("recover:$id", "retry:$id"), calls)
    }

    @Test
    fun stable401EnvelopePersistsAuthRequiredWithoutTerminalRejection() {
        val store = MemoryStore()
        val transport = RecordingTransport(
            ApiResult.ExpectedFailure(
                com.rotiropi.pos_erpnext.data.api.ApiErrorData("AUTH", "reauthenticate", emptyMap(), false),
                ApiMeta("v1", "request", "now"),
                "auth".encodeToByteArray(),
                statusCode = 401,
            ),
        )

        assertEquals(RecoveryExecution.AuthRequired, coordinator(store, transport).execute(spec("sale")))
        assertEquals(PendingMutationState.AUTH_REQUIRED, store.records.single().state)
        assertEquals(1, transport.calls)
    }

    @Test
    fun requestInProgressOutsideClosingStopsAsProtocolAmbiguity() {
        val store = MemoryStore()
        val transport = RecordingTransport(
            ApiResult.ExpectedFailure(
                com.rotiropi.pos_erpnext.data.api.ApiErrorData(
                    "REQUEST_IN_PROGRESS",
                    "processing",
                    mapOf("retry_after_seconds" to kotlinx.serialization.json.JsonPrimitive(3)),
                    true,
                ),
                ApiMeta("v1", "request", "now"),
                "processing".encodeToByteArray(),
            ),
        )

        assertEquals(
            RecoveryExecution.ManualRecovery("123e4567-e89b-42d3-a456-426614174000"),
            coordinator(store, transport).execute(spec("sale")),
        )
        assertEquals(PendingMutationState.MANUAL_RECOVERY, store.records.single().state)
    }

    private fun coordinator(
        store: MemoryStore,
        transport: RecordingTransport,
        connectivity: ConnectivityStatus = ConnectivityStatus.Online,
        cashier: String = "cashier-1"
    ) = RecoveryCoordinator(
        store = store,
        transport = transport,
        connectivity = { connectivity },
        identity = { RecoveryIdentity(cashier, "https://example.test", "client") },
        clock = { 1_000L },
        randomUuid = { "123e4567-e89b-42d3-a456-426614174000" },
        jitterMillis = { 0L },
    )

    private fun coordinatorAt(nowMillis: Long, store: MemoryStore, transport: RecordingTransport) = RecoveryCoordinator(
        store = store,
        transport = transport,
        connectivity = { ConnectivityStatus.Online },
        identity = { RecoveryIdentity("cashier-1", "https://example.test", "client") },
        clock = { nowMillis },
        randomUuid = { "123e4567-e89b-42d3-a456-426614174000" },
        jitterMillis = { 0L },
    )

    private fun preparedStoreRecord(state: PendingMutationState) = PendingMutation(
        transactionId = "123e4567-e89b-42d3-a456-426614174000",
        identity = RecoveryIdentity("cashier-1", "https://example.test", "client"),
        endpoint = MobilePosEndpoint.SALES_SUBMIT,
        body = "stored".encodeToByteArray(),
        contentType = "application/json",
        serializerIdentity = "sale-v1",
        bodyFormatVersion = PendingMutation.BODY_FORMAT_VERSION,
        createdAtMillis = 1L,
        state = state,
    )

    private fun spec(value: String) = RecoverySpec(
        endpoint = MobilePosEndpoint.SALES_SUBMIT,
        body = JsonObject(
            mapOf(
                "pos_profile" to JsonPrimitive(value),
                "client_accepted_grand_total" to JsonPrimitive("1.00"),
                "items" to kotlinx.serialization.json.JsonArray(emptyList()),
                "payments" to kotlinx.serialization.json.JsonArray(emptyList()),
            ),
        ),
        bodySerializer = JsonObject.serializer(),
        responseDeserializer = String.serializer(),
        json = json,
    )

    private fun validClosingSpec() = RecoverySpec(
        endpoint = MobilePosEndpoint.CLOSING_SUBMIT,
        body = JsonObject(
            mapOf(
                "pos_profile" to JsonPrimitive("OUTLET-01"),
                "closing_balances" to kotlinx.serialization.json.JsonArray(emptyList()),
            ),
        ),
        bodySerializer = JsonObject.serializer(),
        responseDeserializer = String.serializer(),
        json = json,
    )

    private fun invalidSpec(value: String) = RecoverySpec(
        endpoint = MobilePosEndpoint.SALES_SUBMIT,
        body = value,
        bodySerializer = String.serializer(),
        responseDeserializer = String.serializer(),
        json = json,
    )

    private class MemoryStore : PendingMutationStore {
        val records = mutableListOf<PendingMutation>()
        var beginSendingOverride: ((PendingMutation) -> PendingMutation?)? = null
        var terminalResult = true
        var acknowledgeResult = true
        var prepareResult: PendingMutationAdmission? = null

        override fun prepare(record: PendingMutation): PendingMutationAdmission {
            prepareResult?.let { return it }
            if (records.any { !it.state.terminal && it.identity == record.identity }) {
                return PendingMutationAdmission.UNRESOLVED_EXISTS
            }
            records += record
            return PendingMutationAdmission.ACCEPTED
        }

        @Synchronized
        override fun beginSending(
            transactionId: String,
            expectedIdentity: RecoveryIdentity,
            expectedState: PendingMutationState,
            expectedAttemptCount: Int,
            expectedNextEligibleAtMillis: Long,
        ): PendingMutation? {
            val index = records.indexOfFirst {
                it.transactionId == transactionId && it.identity == expectedIdentity &&
                    it.state == expectedState && it.attemptCount == expectedAttemptCount &&
                    it.nextEligibleAtMillis == expectedNextEligibleAtMillis &&
                    !it.state.terminal && it.state != PendingMutationState.MANUAL_RECOVERY
            }
            if (index == -1) return null
            return records[index].copy(state = PendingMutationState.SENDING, attemptCount = expectedAttemptCount + 1)
                .also { sending -> records[index] = sending }
                .let { sending -> beginSendingOverride?.invoke(sending) ?: sending }
        }

        @Synchronized
        override fun finishSending(
            transactionId: String,
            expectedIdentity: RecoveryIdentity,
            state: PendingMutationState,
            attemptCount: Int,
            nextEligibleAtMillis: Long,
            reference: String?,
        ): Boolean {
            val index = records.indexOfFirst { it.transactionId == transactionId && it.identity == expectedIdentity && it.state == PendingMutationState.SENDING }
            if (index == -1) return false
            records[index] = records[index].copy(state = state, attemptCount = attemptCount, nextEligibleAtMillis = nextEligibleAtMillis, reference = reference)
            return true
        }

        @Synchronized
        override fun markManualRecovery(transactionId: String, expectedIdentity: RecoveryIdentity): Boolean {
            val index = records.indexOfFirst { it.transactionId == transactionId && it.identity == expectedIdentity && !it.state.terminal && it.state != PendingMutationState.MANUAL_RECOVERY }
            if (index == -1) return false
            records[index] = records[index].copy(state = PendingMutationState.MANUAL_RECOVERY)
            return true
        }

        override fun persistTerminal(
            transactionId: String,
            expectedIdentity: RecoveryIdentity,
            state: PendingMutationState,
            terminalResponse: ByteArray,
            reference: String?,
        ): Boolean {
            val index = records.indexOfFirst { it.transactionId == transactionId && it.identity == expectedIdentity && it.state == PendingMutationState.SENDING }
            if (index == -1) return false
            if (!terminalResult) {
                records[index] = records[index].copy(state = PendingMutationState.MANUAL_RECOVERY)
                return false
            }
            records[index] = records[index].copy(state = state, terminalResponse = terminalResponse, reference = reference)
            return true
        }

        override fun find(transactionId: String, expectedIdentity: RecoveryIdentity): PendingMutation? =
            records.firstOrNull { it.transactionId == transactionId && it.identity == expectedIdentity }

        override fun unresolved(expectedIdentity: RecoveryIdentity): List<PendingMutation> =
            records.filter { !it.state.terminal && it.identity == expectedIdentity }

        override fun terminalRecovery(expectedIdentity: RecoveryIdentity): TerminalRecovery? = records
            .firstOrNull { it.identity == expectedIdentity && it.state.terminal }
            ?.let { TerminalRecovery(it.transactionId, it.state) }

        override fun readTerminalResult(
            transactionId: String,
            expectedIdentity: RecoveryIdentity,
        ): ValidatedTerminalResult? = records.firstOrNull {
            it.transactionId == transactionId && it.identity == expectedIdentity && it.state.terminal
        }?.let {
            ValidatedTerminalResult(
                it.transactionId,
                it.state,
                it.endpoint,
                it.reference,
                it.terminalResponse?.decodeToString() ?: "terminal",
                TerminalReadToken(it.transactionId, TERMINAL_RESULT_FORMAT_VERSION),
            )
        }

        override fun recoverStaleSending(transactionId: String, expectedIdentity: RecoveryIdentity): Boolean {
            val index = records.indexOfFirst {
                it.transactionId == transactionId && it.identity == expectedIdentity && it.state == PendingMutationState.SENDING
            }
            if (index == -1) return false
            records[index] = records[index].copy(state = PendingMutationState.WAITING_RETRY)
            return true
        }

        @Synchronized
        override fun acknowledge(token: TerminalReadToken, expectedIdentity: RecoveryIdentity): Boolean =
            token.formatVersion == TERMINAL_RESULT_FORMAT_VERSION && acknowledgeResult && records.removeIf {
                it.transactionId == token.transactionId && it.identity == expectedIdentity && it.state.terminal
            }

        override fun logoutIfNoRecords(cleanup: () -> Unit): RecoveryLogoutBlocker? = synchronized(this) {
            records.firstOrNull()?.let { RecoveryLogoutBlocker(it.identity.cashier, it.state) } ?: run {
                cleanup()
                null
            }
        }
    }

    private fun recordingScheduler(schedule: (String, Long) -> Unit) = object : RetryScheduler {
        override fun schedule(transactionId: String, nextEligibleAtMillis: Long, completion: (Throwable?) -> Unit) {
            schedule(transactionId, nextEligibleAtMillis)
            completion(null)
        }
    }

    private fun throwingScheduler() = object : RetryScheduler {
        override fun schedule(transactionId: String, nextEligibleAtMillis: Long, completion: (Throwable?) -> Unit) {
            throw IllegalStateException("scheduler unavailable")
        }
    }

    private class DeferredScheduler : RetryScheduler {
        private var completion: ((Throwable?) -> Unit)? = null

        override fun schedule(transactionId: String, nextEligibleAtMillis: Long, completion: (Throwable?) -> Unit) {
            this.completion = completion
        }

        fun fail() = requireNotNull(completion).invoke(IllegalStateException("scheduler unavailable"))
        fun succeed() = requireNotNull(completion).invoke(null)
    }

    private class RecordingTransport(
        private val result: ApiResult<String> = ApiResult.Success("ok", ApiMeta("v1", "request", "now"), "ok".encodeToByteArray())
    ) : RecoveryTransport {
        var calls = 0
        var beforeResult: (() -> Unit)? = null
        val bodies = mutableListOf<ByteArray>()
        val keys = mutableListOf<String>()
        override fun <T> execute(request: PendingMutation, deserializer: kotlinx.serialization.DeserializationStrategy<T>): ApiResult<T> {
            calls++
            bodies += request.body
            keys += request.transactionId
            beforeResult?.invoke()
            @Suppress("UNCHECKED_CAST")
            return result as ApiResult<T>
        }
    }
}
