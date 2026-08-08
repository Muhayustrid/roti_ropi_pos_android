package com.rotiropi.pos_erpnext.recovery

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingMutationStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var store: SqlitePendingMutationStore
    private val identity = RecoveryIdentity("cashier", "https://example.test", "client")

    @Before
    fun setUp() {
        store = SqlitePendingMutationStore(context, "pending-mutations-test-${UUID.randomUUID()}.db")
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(store.databaseNameForTest)
    }

    @Test
    fun metadataUpdateRetainsByteIdenticalBodyEvidence() {
        val record = prepared("metadata")
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(record))
        val before = store.encryptedEvidenceForTest(record.transactionId)!!

        assertTrue(store.beginSending(
            record.transactionId,
            identity,
            PendingMutationState.PREPARED,
            0,
            0L,
        ) != null)
        assertTrue(store.finishSending(
            transactionId = record.transactionId,
            expectedIdentity = identity,
            state = PendingMutationState.WAITING_RETRY,
            attemptCount = 1,
            nextEligibleAtMillis = 500L,
            reference = "request-1",
        ))

        val after = store.encryptedEvidenceForTest(record.transactionId)!!
        assertEquals(before.bodyIv.toList(), after.bodyIv.toList())
        assertEquals(before.bodyCiphertext.toList(), after.bodyCiphertext.toList())
        assertEquals(before.bodyTag.toList(), after.bodyTag.toList())
        assertEquals(PendingMutationState.WAITING_RETRY, store.find(record.transactionId, identity)!!.state)
    }

    @Test
    fun terminalWriteUsesDistinctIvAndReadsStateWithCompleteResponse() {
        val record = prepared("terminal")
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(record))

        assertTrue(store.beginSending(record.transactionId, identity, PendingMutationState.PREPARED, 0, 0L) != null)
        assertTrue(store.persistTerminal(
            transactionId = record.transactionId,
            expectedIdentity = identity,
            state = PendingMutationState.COMPLETED,
            terminalResponse = "receipt".encodeToByteArray(),
            reference = "invoice-1",
        ))

        val evidence = store.encryptedEvidenceForTest(record.transactionId)!!
        assertNotEquals(evidence.bodyIv.toList(), evidence.terminalIv!!.toList())
        val terminal = store.find(record.transactionId, identity)!!
        assertEquals(PendingMutationState.COMPLETED, terminal.state)
        assertEquals("receipt", terminal.terminalResponse!!.decodeToString())
    }

    @Test
    fun queuedClosingPersistsResponseThenAtomicallyPromotesStatusReceipt() {
        val record = prepared("closing").copy(
            endpoint = MobilePosEndpoint.CLOSING_SUBMIT,
            serializerIdentity = MobilePosEndpoint.CLOSING_SUBMIT.serializerIdentity,
        )
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(record))
        val bodyBefore = store.encryptedEvidenceForTest(record.transactionId)!!
        assertTrue(store.beginSending(record.transactionId, identity, PendingMutationState.PREPARED, 0, 0L) != null)

        assertTrue(store.persistClosingQueued(
            record.transactionId,
            identity,
            "queued".encodeToByteArray(),
            "CLOSING-1",
        ))
        assertEquals(PendingMutationState.CLOSING_QUEUED, store.find(record.transactionId, identity)!!.state)
        assertEquals("queued", store.find(record.transactionId, identity)!!.terminalResponse!!.decodeToString())
        assertEquals(record.transactionId, store.unresolved(identity).single().transactionId)

        assertTrue(store.persistClosingStatusTerminal(
            record.transactionId,
            identity,
            "submitted".encodeToByteArray(),
            "CLOSING-1",
        ))
        val terminal = store.find(record.transactionId, identity)!!
        val bodyAfter = store.encryptedEvidenceForTest(record.transactionId)!!
        assertEquals(PendingMutationState.COMPLETED, terminal.state)
        assertEquals("submitted", terminal.terminalResponse!!.decodeToString())
        assertEquals(bodyBefore.bodyCiphertext.toList(), bodyAfter.bodyCiphertext.toList())
        assertTrue(store.unresolved(identity).isEmpty())
    }

    @Test
    fun queuedClosingPromotionRejectsWrongIdentityAndNonClosingEndpoint() {
        val record = prepared("closing").copy(
            endpoint = MobilePosEndpoint.CLOSING_SUBMIT,
            serializerIdentity = MobilePosEndpoint.CLOSING_SUBMIT.serializerIdentity,
        )
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(record))
        assertTrue(store.beginSending(record.transactionId, identity, PendingMutationState.PREPARED, 0, 0L) != null)
        assertTrue(store.persistClosingQueued(record.transactionId, identity, "queued".encodeToByteArray(), "CLOSING-1"))

        assertFalse(store.persistClosingStatusTerminal(
            record.transactionId,
            identity.copy(cashier = "other"),
            "submitted".encodeToByteArray(),
            "CLOSING-1",
        ))
        store.tamperMetadataForTest(record.transactionId, "endpoint", MobilePosEndpoint.SALES_SUBMIT.name)
        assertFalse(store.persistClosingStatusTerminal(
            record.transactionId,
            identity,
            "submitted".encodeToByteArray(),
            "CLOSING-1",
        ))
        assertEquals(PendingMutationState.MANUAL_RECOVERY, store.find(record.transactionId, identity)!!.state)
    }

    @Test
    fun concurrentAdmissionAllowsOnlyOneUnresolvedMutationPerIdentity() {
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val outcomes = java.util.Collections.synchronizedList(mutableListOf<PendingMutationAdmission>())
        val executor = Executors.newFixedThreadPool(2)
        repeat(2) { index ->
            executor.execute {
                start.await(5, TimeUnit.SECONDS)
                outcomes += store.prepare(prepared("concurrent-$index"))
                done.countDown()
            }
        }

        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        executor.shutdownNow()

        assertEquals(1, outcomes.count { it == PendingMutationAdmission.ACCEPTED })
        assertEquals(1, outcomes.count { it == PendingMutationAdmission.UNRESOLVED_EXISTS })
    }

    @Test
    fun identityMismatchDoesNotDecryptOrExposeBody() {
        val record = prepared("secret")
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(record))
        store.tamperBodyTagForTest(record.transactionId)

        assertNull(store.find(record.transactionId, identity.copy(cashier = "other")))
        assertFalse(store.encryptedEvidenceForTest(record.transactionId)!!.manualRecovery)
    }

    @Test
    fun tagFailureMarksManualRecoveryWithoutErasingEvidence() {
        val record = prepared("body")
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(record))
        store.tamperBodyTagForTest(record.transactionId)
        val tampered = store.encryptedEvidenceForTest(record.transactionId)!!

        val recovered = store.find(record.transactionId, identity)!!
        val after = store.encryptedEvidenceForTest(record.transactionId)!!
        assertEquals(PendingMutationState.MANUAL_RECOVERY, recovered.state)
        assertTrue(recovered.body.isEmpty())
        assertEquals(tampered.bodyCiphertext.toList(), after.bodyCiphertext.toList())
        assertEquals(tampered.bodyTag.toList(), after.bodyTag.toList())
        assertTrue(after.manualRecovery)
    }

    @Test
    fun retainedCompletedAndRejectedEvidenceAllowsNextAdmissionWithUsableKey() {
        val completed = prepared("completed")
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(completed))
        assertTrue(store.beginSending(completed.transactionId, identity, PendingMutationState.PREPARED, 0, 0L) != null)
        assertTrue(store.persistTerminal(completed.transactionId, identity, PendingMutationState.COMPLETED, "done".encodeToByteArray(), null))

        val rejected = prepared("rejected")
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(rejected))
        assertTrue(store.beginSending(rejected.transactionId, identity, PendingMutationState.PREPARED, 0, 0L) != null)
        assertTrue(store.persistTerminal(rejected.transactionId, identity, PendingMutationState.REJECTED, "no".encodeToByteArray(), null))

        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(prepared("next")))
    }

    @Test
    fun unresolvedOtherCashierDoesNotBlockAdmission() {
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(prepared("other", identity.copy(cashier = "other"))))

        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(prepared("current")))
    }

    @Test
    fun invalidatedKeyDuringPrepareQuarantinesExistingEvidenceWithoutReplacement() {
        val provider = RecordingCryptoProvider().also { cryptoProvider ->
            store.close()
            context.deleteDatabase(store.databaseNameForTest)
            store = SqlitePendingMutationStore(context, "pending-mutations-test-${UUID.randomUUID()}.db", cryptoProvider)
        }
        val record = prepared("evidence")
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(record))
        provider.failExisting = true

        assertEquals(
            PendingMutationAdmission.MANUAL_RECOVERY_REQUIRED(record.transactionId),
            store.prepare(prepared("new")),
        )
        assertEquals(PendingMutationState.MANUAL_RECOVERY, store.find(record.transactionId, identity)!!.state)
        assertEquals(1, provider.created)
    }

    @Test
    fun absentKeyWithTerminalEvidenceReturnsExistingEvidenceIdWithoutReplacement() {
        val provider = RecordingCryptoProvider().also { cryptoProvider ->
            store.close()
            context.deleteDatabase(store.databaseNameForTest)
            store = SqlitePendingMutationStore(context, "pending-mutations-test-${UUID.randomUUID()}.db", cryptoProvider)
        }
        val terminal = prepared("terminal")
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(terminal))
        assertTrue(store.beginSending(terminal.transactionId, identity, PendingMutationState.PREPARED, 0, 0L) != null)
        assertTrue(store.persistTerminal(terminal.transactionId, identity, PendingMutationState.COMPLETED, "done".encodeToByteArray(), null))
        provider.replaceExistingKey()

        assertEquals(
            PendingMutationAdmission.MANUAL_RECOVERY_REQUIRED(terminal.transactionId),
            store.prepare(prepared("replacement")),
        )
        assertTrue(store.encryptedEvidenceForTest(terminal.transactionId)!!.manualRecovery)
        assertEquals(1, provider.created)
    }

    @Test
    fun corruptedTerminalEvidenceQuarantinesExistingIdWithoutReplacement() {
        val provider = RecordingCryptoProvider().also { cryptoProvider ->
            store.close()
            context.deleteDatabase(store.databaseNameForTest)
            store = SqlitePendingMutationStore(context, "pending-mutations-test-${UUID.randomUUID()}.db", cryptoProvider)
        }
        val terminal = prepared("terminal-tag")
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(terminal))
        assertTrue(store.beginSending(terminal.transactionId, identity, PendingMutationState.PREPARED, 0, 0L) != null)
        assertTrue(store.persistTerminal(terminal.transactionId, identity, PendingMutationState.REJECTED, "no".encodeToByteArray(), null))
        store.tamperTerminalTagForTest(terminal.transactionId)
        val retained = store.encryptedEvidenceForTest(terminal.transactionId)!!
        val replacement = prepared("replacement")

        assertEquals(
            PendingMutationAdmission.MANUAL_RECOVERY_REQUIRED(terminal.transactionId),
            store.prepare(replacement),
        )
        assertRetainedEvidenceManualAndUnchanged(terminal.transactionId, retained)
        assertNull(store.find(replacement.transactionId, identity))
        assertEquals(1, provider.created)
    }

    @Test
    fun corruptedTerminalCiphertextQuarantinesExistingIdWithoutReplacement() {
        val provider = RecordingCryptoProvider().also { cryptoProvider ->
            store.close()
            context.deleteDatabase(store.databaseNameForTest)
            store = SqlitePendingMutationStore(context, "pending-mutations-test-${UUID.randomUUID()}.db", cryptoProvider)
        }
        val terminal = prepared("terminal-ciphertext")
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(terminal))
        assertTrue(store.beginSending(terminal.transactionId, identity, PendingMutationState.PREPARED, 0, 0L) != null)
        assertTrue(store.persistTerminal(terminal.transactionId, identity, PendingMutationState.COMPLETED, "done".encodeToByteArray(), null))
        store.tamperTerminalCiphertextForTest(terminal.transactionId)
        val retained = store.encryptedEvidenceForTest(terminal.transactionId)!!
        val replacement = prepared("replacement")

        assertEquals(
            PendingMutationAdmission.MANUAL_RECOVERY_REQUIRED(terminal.transactionId),
            store.prepare(replacement),
        )
        assertRetainedEvidenceManualAndUnchanged(terminal.transactionId, retained)
        assertNull(store.find(replacement.transactionId, identity))
        assertEquals(1, provider.created)
    }

    @Test
    fun partialTerminalEvidenceQuarantinesExistingIdWithoutReplacement() {
        val provider = RecordingCryptoProvider().also { cryptoProvider ->
            store.close()
            context.deleteDatabase(store.databaseNameForTest)
            store = SqlitePendingMutationStore(context, "pending-mutations-test-${UUID.randomUUID()}.db", cryptoProvider)
        }
        val terminal = prepared("terminal-partial")
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(terminal))
        assertTrue(store.beginSending(terminal.transactionId, identity, PendingMutationState.PREPARED, 0, 0L) != null)
        assertTrue(store.persistTerminal(terminal.transactionId, identity, PendingMutationState.COMPLETED, "done".encodeToByteArray(), null))
        store.clearTerminalTagForTest(terminal.transactionId)
        val retained = store.encryptedEvidenceForTest(terminal.transactionId)!!
        val replacement = prepared("replacement")

        assertEquals(
            PendingMutationAdmission.MANUAL_RECOVERY_REQUIRED(terminal.transactionId),
            store.prepare(replacement),
        )
        assertRetainedEvidenceManualAndUnchanged(terminal.transactionId, retained)
        assertNull(store.find(replacement.transactionId, identity))
        assertEquals(1, provider.created)
    }

    @Test
    fun twoStoreInstancesSerializeFirstInstallKeyAndAdmission() {
        val provider = RecordingCryptoProvider()
        store.close()
        context.deleteDatabase(store.databaseNameForTest)
        val name = "pending-mutations-test-${UUID.randomUUID()}.db"
        store = SqlitePendingMutationStore(context, name, provider)
        val peer = SqlitePendingMutationStore(context, name, provider)
        val gate = CountDownLatch(1)
        val done = CountDownLatch(2)
        val outcomes = java.util.Collections.synchronizedList(mutableListOf<PendingMutationAdmission>())
        val executor = Executors.newFixedThreadPool(2)
        listOf(store, peer).forEachIndexed { index, candidate ->
            executor.execute {
                gate.await(5, TimeUnit.SECONDS)
                outcomes += candidate.prepare(prepared("body-$index"))
                done.countDown()
            }
        }

        gate.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        peer.close()
        executor.shutdownNow()

        assertEquals(1, outcomes.count { it == PendingMutationAdmission.ACCEPTED })
        assertEquals(1, outcomes.count { it == PendingMutationAdmission.UNRESOLVED_EXISTS })
        assertEquals(1, provider.created)
        assertEquals(1, store.unresolved(identity).size)
    }

    @Test
    fun deletedKeyQuarantinesEvidenceWithoutReplacement() {
        val record = prepared("key-loss")
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(record))
        val evidence = store.encryptedEvidenceForTest(record.transactionId)!!
        store.deleteKeyForTest()

        val support = store.find(record.transactionId, identity)!!

        assertEquals(PendingMutationState.MANUAL_RECOVERY, support.state)
        assertFalse(store.hasKeyForTest())
        assertEquals(evidence.bodyCiphertext.toList(), store.encryptedEvidenceForTest(record.transactionId)!!.bodyCiphertext.toList())
    }

    @Test
    fun terminalEncryptionFailureMarksSendingEvidenceManualWithoutReplacementKey() {
        val record = prepared("terminal-key-loss")
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(record))
        assertTrue(store.beginSending(record.transactionId, identity, PendingMutationState.PREPARED, 0, 0L) != null)
        val evidence = store.encryptedEvidenceForTest(record.transactionId)!!
        store.deleteKeyForTest()

        assertFalse(store.persistTerminal(record.transactionId, identity, PendingMutationState.COMPLETED, "receipt".encodeToByteArray(), null))
        assertEquals(PendingMutationState.MANUAL_RECOVERY, store.find(record.transactionId, identity)!!.state)
        assertFalse(store.hasKeyForTest())
        assertEquals(evidence.bodyCiphertext.toList(), store.encryptedEvidenceForTest(record.transactionId)!!.bodyCiphertext.toList())
    }

    @Test
    fun unknownFormatAndStaleSendingUseMetadataOnlyTransitions() {
        val record = prepared("format")
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(record))
        val before = store.encryptedEvidenceForTest(record.transactionId)!!
        store.forceBodyFormatForTest(record.transactionId, 99)

        val recovered = store.find(record.transactionId, identity)!!
        assertEquals(PendingMutationState.MANUAL_RECOVERY, recovered.state)
        val afterFormat = store.encryptedEvidenceForTest(record.transactionId)!!
        assertEquals(before.bodyCiphertext.toList(), afterFormat.bodyCiphertext.toList())

        assertTrue(store.acknowledgeManualRecoveryForTest(record.transactionId))
        val sending = prepared("sending", identity.copy(cashier = "cashier-2"))
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(sending))
        assertTrue(store.beginSending(sending.transactionId, sending.identity, PendingMutationState.PREPARED, 0, 0L) != null)
        assertTrue(store.finishSending(sending.transactionId, sending.identity, PendingMutationState.WAITING_RETRY, 1, 77L, null))
        assertTrue(store.beginSending(sending.transactionId, sending.identity, PendingMutationState.WAITING_RETRY, 1, 77L) != null)
        val sendingBefore = store.encryptedEvidenceForTest(sending.transactionId)!!
        assertTrue(store.recoverStaleSending(sending.transactionId, sending.identity))
        val stale = store.find(sending.transactionId, sending.identity)!!
        val sendingAfter = store.encryptedEvidenceForTest(sending.transactionId)!!
        assertEquals(PendingMutationState.WAITING_RETRY, stale.state)
        assertEquals(2, stale.attemptCount)
        assertEquals(77L, stale.nextEligibleAtMillis)
        assertEquals(sendingBefore.bodyCiphertext.toList(), sendingAfter.bodyCiphertext.toList())
    }

    @Test
    fun concurrentLogoutsThroughRealStoreCannotRunCleanupWhileDurableEvidenceExists() {
        val record = prepared("logout-race")
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(record))
        val evidence = store.encryptedEvidenceForTest(record.transactionId)!!
        val peer = SqlitePendingMutationStore(context, store.databaseNameForTest)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val cleanups = java.util.concurrent.atomic.AtomicInteger()
        val blockers = java.util.Collections.synchronizedList(mutableListOf<RecoveryLogoutBlocker?>())
        val executor = Executors.newFixedThreadPool(2)
        repeat(2) { index ->
            executor.execute {
                start.await(5, TimeUnit.SECONDS)
                val candidate = if (index == 0) store else peer
                blockers += candidate.logoutIfNoRecords { cleanups.incrementAndGet() }
                done.countDown()
            }
        }

        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        peer.close()
        executor.shutdownNow()

        assertEquals(0, cleanups.get())
        assertEquals(2, blockers.count { it == RecoveryLogoutBlocker(identity.cashier, PendingMutationState.PREPARED) })
        val retained = store.encryptedEvidenceForTest(record.transactionId)!!
        assertEquals(evidence.bodyIv.toList(), retained.bodyIv.toList())
        assertEquals(evidence.bodyCiphertext.toList(), retained.bodyCiphertext.toList())
        assertEquals(evidence.bodyTag.toList(), retained.bodyTag.toList())
    }

    @Test
    fun validatedTerminalReadAllowsSameIdentityAcknowledgementOnly() {
        val record = terminal("ack", PendingMutationState.REJECTED, "stable-error")

        val result = store.readTerminalResult(record.transactionId, identity)!!

        assertEquals(PendingMutationState.REJECTED, result.state)
        assertEquals("stable-error", result.responseText)
        assertTrue(store.acknowledge(result.token, identity))
        assertNull(store.find(record.transactionId, identity))
    }

    @Test
    fun wrongIdentityCannotReadOrAcknowledgeTerminalEvidence() {
        val record = terminal("wrong-identity", PendingMutationState.COMPLETED, "receipt")
        val other = identity.copy(cashier = "other")

        assertNull(store.readTerminalResult(record.transactionId, other))
        assertFalse(store.acknowledge(TerminalReadToken(record.transactionId, 1), other))
        assertTrue(store.encryptedEvidenceForTest(record.transactionId) != null)
    }

    @Test
    fun terminalMetadataTamperQuarantinesEvidenceAndRejectsReadAndAcknowledgement() {
        val record = terminal("terminal-tamper", PendingMutationState.COMPLETED, "receipt")
        val before = store.encryptedEvidenceForTest(record.transactionId)!!
        store.tamperMetadataForTest(record.transactionId, "state", PendingMutationState.REJECTED.name)

        assertNull(store.readTerminalResult(record.transactionId, identity))
        assertTrue(store.encryptedEvidenceForTest(record.transactionId)!!.manualRecovery)
        assertFalse(store.acknowledge(TerminalReadToken(record.transactionId, 1), identity))
        assertEquals(before.terminalCiphertext!!.toList(), store.encryptedEvidenceForTest(record.transactionId)!!.terminalCiphertext!!.toList())
    }

    @Test
    fun requestMetadataTamperQuarantinesBeforeDispatch() {
        val mutations = listOf<(PendingMutation) -> PendingMutation>(
            { it.copy(endpoint = MobilePosEndpoint.SESSIONS_OPEN) },
            { it.copy(identity = it.identity.copy(cashier = "other")) },
            { it.copy(identity = it.identity.copy(canonicalOrigin = "https://other.test")) },
            { it.copy(identity = it.identity.copy(clientId = "other-client")) },
            { it.copy(transactionId = UUID.randomUUID().toString()) },
            { it.copy(contentType = "text/plain") },
            { it.copy(serializerIdentity = "other-v1") },
            { it.copy(bodyFormatVersion = 99) },
        )
        mutations.forEachIndexed { index, mutate ->
            val record = prepared("metadata-tamper-$index")
            val altered = mutate(record)
            assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(record))
            store.replaceMetadataForTest(record.transactionId, altered)

            val recovered = store.find(altered.transactionId, altered.identity)
            assertEquals(PendingMutationState.MANUAL_RECOVERY, recovered!!.state)
            assertTrue(store.encryptedEvidenceForTest(altered.transactionId)!!.manualRecovery)
            assertTrue(store.acknowledgeManualRecoveryForTest(altered.transactionId))
        }
    }

    @Test
    fun corruptedTerminalResultRetainsEvidenceAndRejectsAcknowledgement() {
        val record = terminal("corrupt-result", PendingMutationState.COMPLETED, "receipt")
        val before = store.encryptedEvidenceForTest(record.transactionId)!!
        store.tamperTerminalTagForTest(record.transactionId)

        assertNull(store.readTerminalResult(record.transactionId, identity))
        assertTrue(store.encryptedEvidenceForTest(record.transactionId)!!.manualRecovery)
        assertEquals(before.terminalCiphertext!!.toList(), store.encryptedEvidenceForTest(record.transactionId)!!.terminalCiphertext!!.toList())
    }

    @Test
    fun legacyUnboundBodyFormatBecomesManualWithoutDecrypting() {
        val record = prepared("legacy")
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(record))
        store.forceBodyFormatForTest(record.transactionId, 1)

        val recovered = store.find(record.transactionId, identity)!!

        assertEquals(PendingMutationState.MANUAL_RECOVERY, recovered.state)
        assertTrue(recovered.body.isEmpty())
    }

    private fun terminal(body: String, state: PendingMutationState, response: String): PendingMutation {
        val record = prepared(body)
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(record))
        assertTrue(store.beginSending(record.transactionId, identity, PendingMutationState.PREPARED, 0, 0L) != null)
        assertTrue(store.persistTerminal(record.transactionId, identity, state, response.encodeToByteArray(), null))
        return record
    }

    private fun assertRetainedEvidenceManualAndUnchanged(
        transactionId: String,
        retained: SqlitePendingMutationStore.EncryptedEvidence,
    ) {
        val after = store.encryptedEvidenceForTest(transactionId)!!
        assertTrue(after.manualRecovery)
        assertEquals(retained.bodyIv.toList(), after.bodyIv.toList())
        assertEquals(retained.bodyCiphertext.toList(), after.bodyCiphertext.toList())
        assertEquals(retained.bodyTag.toList(), after.bodyTag.toList())
        assertEquals(retained.terminalIv!!.toList(), after.terminalIv!!.toList())
        assertEquals(retained.terminalCiphertext!!.toList(), after.terminalCiphertext!!.toList())
        assertEquals(retained.terminalTag?.toList(), after.terminalTag?.toList())
    }

    private class RecordingCryptoProvider : PendingMutationCryptoProvider {
        private var crypto = PendingMutationCrypto.forTesting(ByteArray(32) { 7 })
        var failExisting = false
        var created = 0
        private var installed = false

        fun replaceExistingKey() {
            crypto = PendingMutationCrypto.forTesting(ByteArray(32) { 8 })
        }

        override fun existing(): PendingMutationCrypto? {
            if (failExisting) throw java.security.InvalidKeyException("injected")
            return if (installed) crypto else null
        }

        override fun createOnFirstInstall(): PendingMutationCrypto {
            created++
            installed = true
            return crypto
        }
    }

    private fun prepared(body: String, owner: RecoveryIdentity = identity) = PendingMutation(
        transactionId = UUID.randomUUID().toString(),
        identity = owner,
        endpoint = MobilePosEndpoint.SALES_SUBMIT,
        body = body.encodeToByteArray(),
        contentType = "application/json",
        serializerIdentity = "sale-v1",
        bodyFormatVersion = PendingMutation.BODY_FORMAT_VERSION,
        createdAtMillis = 12L,
    )
}
