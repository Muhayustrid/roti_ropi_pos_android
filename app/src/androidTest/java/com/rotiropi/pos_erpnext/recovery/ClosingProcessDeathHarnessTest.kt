package com.rotiropi.pos_erpnext.recovery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.data.ConnectivityStatus
import com.rotiropi.pos_erpnext.data.api.ApiResult
import com.rotiropi.pos_erpnext.data.api.ClosingStatus
import com.rotiropi.pos_erpnext.data.api.FrappeResponse
import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import com.rotiropi.pos_erpnext.data.api.SubmitClosingResponseDto
import com.rotiropi.pos_erpnext.data.toClosingReceipt
import com.rotiropi.pos_erpnext.test.SpecialHarnessOnly
import java.security.MessageDigest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClosingProcessDeathHarnessTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val identity = RecoveryIdentity(CASHIER, ORIGIN, CLIENT)

    @Test
    @SpecialHarnessOnly
    fun persistMutationBeforeProcessDeath() {
        context.deleteDatabase(DATABASE)
        val store = SqlitePendingMutationStore(context, DATABASE)
        val record = PendingMutation(
            transactionId = TRANSACTION_ID,
            identity = identity,
            endpoint = MobilePosEndpoint.CLOSING_SUBMIT,
            body = BODY.encodeToByteArray(),
            contentType = "application/json",
            serializerIdentity = MobilePosEndpoint.CLOSING_SUBMIT.serializerIdentity,
            bodyFormatVersion = PendingMutation.BODY_FORMAT_VERSION,
            createdAtMillis = 12L,
        )
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(record))
        assertNotNull(store.beginSending(record.transactionId, identity, PendingMutationState.PREPARED, 0, 0L))
        assertTrue(store.persistClosingQueued(TRANSACTION_ID, identity, QUEUED_ENVELOPE.encodeToByteArray(), CLOSING_REFERENCE))

        var cleanupCalled = false
        assertEquals(PendingMutationState.CLOSING_QUEUED, store.logoutIfNoRecords { cleanupCalled = true }?.state)
        assertFalse(cleanupCalled)
        store.close()
        prefs().edit()
            .putString(KEY_TRANSACTION, TRANSACTION_ID)
            .putString(KEY_BODY_SHA256, sha256(BODY.encodeToByteArray()))
            .commit()
    }

    @Test
    @SpecialHarnessOnly
    fun verifyMutationAfterProcessDeath() {
        val store = SqlitePendingMutationStore(context, DATABASE)
        val transactionId = requireNotNull(prefs().getString(KEY_TRANSACTION, null))
        val record = requireNotNull(store.find(transactionId, identity))
        assertEquals(TRANSACTION_ID, record.transactionId)
        assertEquals(MobilePosEndpoint.CLOSING_SUBMIT, record.endpoint)
        assertEquals(PendingMutationState.CLOSING_QUEUED, record.state)
        assertEquals(requireNotNull(prefs().getString(KEY_BODY_SHA256, null)), sha256(record.body))
        assertEquals(BODY, record.body.decodeToString())

        val transport = NoSubmitReplayTransport()
        val coordinator = RecoveryCoordinator(
            store = store,
            transport = transport,
            connectivity = { ConnectivityStatus.Online },
            identity = { identity },
            randomUuid = { error("Queued Closing recovery must not allocate another UUID") },
        )
        assertTrue(coordinator.recoverAtAuthenticatedStartup().isEmpty())
        assertEquals(TRANSACTION_ID, coordinator.uiState.value.closingQueuedTransactionId)
        assertEquals(0, transport.calls)

        val queued = requireNotNull(coordinator.readClosingResult(TRANSACTION_ID))
        assertEquals(CLOSING_REFERENCE, queued.reference)
        assertEquals(QUEUED_ENVELOPE, queued.responseText)
        assertEquals(ClosingStatus.QUEUED, decodeReceipt(queued.responseText).status)

        assertTrue(
            coordinator.persistClosingTerminal(
                TRANSACTION_ID,
                ClosingStatus.SUBMITTED,
                SUBMITTED_ENVELOPE.encodeToByteArray(),
                CLOSING_REFERENCE,
            ),
        )
        val terminal = requireNotNull(coordinator.readClosingResult(TRANSACTION_ID))
        assertEquals(SUBMITTED_ENVELOPE, terminal.responseText)
        val receipt = decodeReceipt(terminal.responseText)
        assertEquals(ClosingStatus.SUBMITTED, receipt.status)
        assertEquals(CLOSING_REFERENCE, receipt.name)
        assertEquals("POS-OPE-1", receipt.openingEntry)
        assertEquals("OUTLET-01", receipt.posProfile)
        assertEquals(12, receipt.invoiceCount)
        assertEquals("820000.00", receipt.grandTotal)
        assertEquals("738738.74", receipt.netTotal)
        assertEquals("81261.26", receipt.totalTaxesAndCharges)
        assertEquals("975000.00", receipt.reconciliation.countedTotal)
        assertEquals("-5000.00", receipt.reconciliation.differenceTotal)

        val completed = requireNotNull(store.find(TRANSACTION_ID, identity))
        assertEquals(PendingMutationState.COMPLETED, completed.state)
        assertEquals(BODY, completed.body.decodeToString())
        assertEquals(requireNotNull(prefs().getString(KEY_BODY_SHA256, null)), sha256(completed.body))
        assertEquals(0, transport.calls)

        var refreshes = 0
        val viewModel = com.rotiropi.pos_erpnext.ui.closing.ClosingViewModel(
            dispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            previewClosing = { _, _ -> error("Preview not expected during restored receipt") },
            submitClosing = { error("Submit replay not expected during restored receipt") },
            completedClosing = { id ->
                coordinator.readClosingResult(id)?.let { decodeReceipt(it.responseText) }
            },
            rejectedClosing = { null },
            closingStatus = { _, _ -> error("Status polling not expected after terminal persistence") },
            persistTerminal = { _, _, _ -> false },
            onTerminal = { refreshes++ },
            acknowledge = { id -> coordinator.acknowledge(id) is RecoveryAcknowledgement.Acknowledged },
        )
        viewModel.recover(TRANSACTION_ID)
        val restored = viewModel.state.value as com.rotiropi.pos_erpnext.ui.closing.ClosingUiState.Receipt
        assertEquals(CLOSING_REFERENCE, restored.receipt.name)
        assertEquals(1, refreshes)
        var cleanupCalled = false
        assertEquals(PendingMutationState.COMPLETED, store.logoutIfNoRecords { cleanupCalled = true }?.state)
        assertFalse(cleanupCalled)
        assertTrue(viewModel.closeReceipt())
        assertTrue(viewModel.state.value is com.rotiropi.pos_erpnext.ui.closing.ClosingUiState.Unavailable)
        assertEquals(null, store.logoutIfNoRecords { cleanupCalled = true })
        assertTrue(cleanupCalled)
        store.close()
    }

    private fun decodeReceipt(responseText: String) = Json.decodeFromString(
        FrappeResponse.serializer(),
        responseText,
    ).message.data
        ?.let { Json.decodeFromJsonElement(SubmitClosingResponseDto.serializer(), it).closing.toClosingReceipt() }
        ?: error("Closing receipt missing")

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private class NoSubmitReplayTransport : RecoveryTransport {
        var calls = 0

        override fun <T> execute(
            request: PendingMutation,
            deserializer: DeserializationStrategy<T>,
        ): ApiResult<T> {
            calls++
            error("Queued Closing recovery must poll closing.status, not replay closing.submit")
        }
    }

    private companion object {
        const val DATABASE = "pending-closing-process-death.db"
        const val PREFS = "pending-closing-process-death"
        const val KEY_TRANSACTION = "transaction"
        const val KEY_BODY_SHA256 = "body_sha256"
        const val TRANSACTION_ID = "123e4567-e89b-42d3-a456-426614174000"
        const val CASHIER = "cashier@example.com"
        const val ORIGIN = "https://oauth-staging.rotiropi.web.id"
        const val CLIENT = "rotiropi.mobilepos.task11.staging"
        const val CLOSING_REFERENCE = "POS-CLO-1"
        const val BODY = "{\"pos_profile\":\"OUTLET-01\",\"preview_id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"closing_balances\":[{\"mode_of_payment\":\"Cash\",\"closing_amount\":\"975000.00\"}]}"
        const val CLOSING_DATA = "{\"closing\":{\"name\":\"POS-CLO-1\",\"opening_entry\":\"POS-OPE-1\",\"pos_profile\":\"OUTLET-01\",\"status\":\"%s\",\"invoice_count\":12,\"grand_total\":\"820000.00\",\"net_total\":\"738738.74\",\"total_quantity\":\"12.00\",\"total_taxes_and_charges\":\"81261.26\",\"payments\":[{\"mode_of_payment\":\"Cash\",\"opening_amount\":\"500000.00\",\"expected_amount\":\"980000.00\",\"counted_amount\":\"975000.00\",\"difference\":\"-5000.00\"}],\"reconciliation\":{\"expected_total\":\"980000.00\",\"counted_total\":\"975000.00\",\"difference_total\":\"-5000.00\"},\"failure\":null}}"
        val QUEUED_ENVELOPE = "{\"message\":{\"ok\":true,\"data\":${CLOSING_DATA.format("queued")},\"meta\":{\"api_version\":\"v1\",\"request_id\":\"REQ-CLOSE-1\",\"server_time\":\"2026-08-08T10:00:00Z\",\"replayed\":false},\"error\":null}}"
        val SUBMITTED_ENVELOPE = "{\"message\":{\"ok\":true,\"data\":${CLOSING_DATA.format("submitted")},\"meta\":{\"api_version\":\"v1\",\"request_id\":\"REQ-STATUS-1\",\"server_time\":\"2026-08-08T10:01:00Z\",\"replayed\":false},\"error\":null}}"
    }
}
