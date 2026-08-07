package com.rotiropi.pos_erpnext.recovery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.data.ConnectivityStatus
import com.rotiropi.pos_erpnext.data.api.ApiMeta
import com.rotiropi.pos_erpnext.data.api.ApiResult
import com.rotiropi.pos_erpnext.data.api.CreateReturnResponseDto
import com.rotiropi.pos_erpnext.data.api.FrappeResponse
import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import com.rotiropi.pos_erpnext.test.SpecialHarnessOnly
import com.rotiropi.pos_erpnext.ui.payment.ReceiptMapper
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReturnProcessDeathHarnessTest {
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
            endpoint = MobilePosEndpoint.SALES_CREATE_RETURN,
            body = BODY.encodeToByteArray(),
            contentType = "application/json",
            serializerIdentity = MobilePosEndpoint.SALES_CREATE_RETURN.serializerIdentity,
            bodyFormatVersion = PendingMutation.BODY_FORMAT_VERSION,
            createdAtMillis = 12L,
        )
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(record))
        assertNotNull(store.beginSending(record.transactionId, identity, PendingMutationState.PREPARED, 0, 0L))
        var cleanupCalled = false
        assertEquals(PendingMutationState.SENDING, store.logoutIfNoRecords { cleanupCalled = true }?.state)
        assertFalse(cleanupCalled)
        assertNotNull(store.find(TRANSACTION_ID, identity))
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
        assertEquals(MobilePosEndpoint.SALES_CREATE_RETURN, record.endpoint)
        assertEquals(requireNotNull(prefs().getString(KEY_BODY_SHA256, null)), sha256(record.body))
        assertEquals(BODY, record.body.decodeToString())
        assertTrue(store.recoverStaleSending(transactionId, identity))

        val transport = CommittedReturnTransport()
        val coordinator = RecoveryCoordinator(
            store = store,
            transport = transport,
            connectivity = { ConnectivityStatus.Online },
            identity = { identity },
            randomUuid = { error("Recovery must replay the persisted return UUID") },
        )
        assertEquals(
            RecoveryExecution.Completed(TRANSACTION_ID),
            coordinator.retry(TRANSACTION_ID, CreateReturnResponseDto.serializer(), Long.MAX_VALUE),
        )
        assertEquals(1, transport.calls)
        assertEquals(TRANSACTION_ID, transport.idempotencyKey)
        assertEquals(BODY, transport.body.decodeToString())

        val terminal = requireNotNull(coordinator.readTerminalResult(TRANSACTION_ID))
        assertEquals(PendingMutationState.COMPLETED, terminal.state)
        assertEquals(RETURN_ENVELOPE, terminal.responseText)
        assertEquals(
            RecoveryTerminalResult.Completed("Return", "SINV-RET-1", "RETURN", "-20.00"),
            RecoveryTerminalMapper.parse(terminal),
        )
        val sale = Json.decodeFromString(FrappeResponse.serializer(), terminal.responseText).message.data
            ?.let { Json.decodeFromJsonElement(CreateReturnResponseDto.serializer(), it).return_sale }
        val receipt = ReceiptMapper.map(requireNotNull(sale))
        assertEquals("SINV-RET-1", receipt.saleId)
        assertEquals("SINV-1", receipt.sourceReference)
        assertEquals(listOf("Cash: 20.00"), receipt.payments)

        var cleanupCalled = false
        assertEquals(PendingMutationState.COMPLETED, store.logoutIfNoRecords { cleanupCalled = true }?.state)
        assertFalse(cleanupCalled)
        store.close()
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private class CommittedReturnTransport : RecoveryTransport {
        var calls = 0
        lateinit var idempotencyKey: String
        lateinit var body: ByteArray

        override fun <T> execute(
            request: PendingMutation,
            deserializer: kotlinx.serialization.DeserializationStrategy<T>,
        ): ApiResult<T> {
            calls++
            assertEquals(MobilePosEndpoint.SALES_CREATE_RETURN, request.endpoint)
            idempotencyKey = request.transactionId
            body = request.body
            return ApiResult.Success(
                Json.decodeFromString(deserializer, RETURN_DATA),
                ApiMeta("v1", "REQ-RET-1", "2026-08-02T10:00:00Z", replayed = true),
                RETURN_ENVELOPE.encodeToByteArray(),
            )
        }
    }

    private companion object {
        const val DATABASE = "pending-return-process-death.db"
        const val PREFS = "pending-return-process-death"
        const val KEY_TRANSACTION = "transaction"
        const val KEY_BODY_SHA256 = "body_sha256"
        const val TRANSACTION_ID = "123e4567-e89b-42d3-a456-426614174000"
        const val CASHIER = "cashier@example.com"
        const val ORIGIN = "https://oauth-staging.rotiropi.web.id"
        const val CLIENT = "rotiropi.mobilepos.task9.staging"
        const val BODY = "{\"source_name\":\"SINV-1\",\"items\":[{\"source_item_row\":\"row-1\",\"qty\":\"1\"}],\"reason\":\"Damaged\",\"refund_mode\":\"Cash\"}"
        const val RETURN_DATA = "{\"return_sale\":{\"summary\":{\"doctype\":\"POS Invoice\",\"name\":\"SINV-RET-1\",\"status\":\"return\",\"customer\":\"Walk In\",\"walk_in_customer_name\":null,\"currency\":\"USD\",\"grand_total\":\"-20.00\",\"paid_amount\":\"-20.00\",\"change_amount\":\"0.00\",\"posting_date\":\"2026-08-02\",\"posting_time\":\"10:00:00\"},\"items\":[],\"taxes\":[],\"payments\":[],\"return_against\":\"SINV-1\",\"return_reason\":\"Damaged\",\"refund_amount\":\"20.00\",\"refund_allocations\":[{\"mode_of_payment\":\"Cash\",\"amount\":\"20.00\",\"reference_no\":null}]}}"
        const val RETURN_ENVELOPE = "{\"message\":{\"ok\":true,\"data\":$RETURN_DATA,\"meta\":{\"api_version\":\"v1\",\"request_id\":\"REQ-RET-1\",\"server_time\":\"2026-08-02T10:00:00Z\",\"replayed\":true},\"error\":null}}"
    }
}
