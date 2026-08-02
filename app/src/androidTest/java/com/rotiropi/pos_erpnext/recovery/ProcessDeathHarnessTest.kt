package com.rotiropi.pos_erpnext.recovery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import com.rotiropi.pos_erpnext.test.SpecialHarnessOnly
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessDeathHarnessTest {
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
            endpoint = MobilePosEndpoint.SALES_SUBMIT,
            body = BODY.encodeToByteArray(),
            contentType = "application/json",
            serializerIdentity = MobilePosEndpoint.SALES_SUBMIT.serializerIdentity,
            bodyFormatVersion = PendingMutation.BODY_FORMAT_VERSION,
            createdAtMillis = 12L,
        )
        assertEquals(PendingMutationAdmission.ACCEPTED, store.prepare(record))
        assertTrue(store.beginSending(record.transactionId, identity, PendingMutationState.PREPARED, 0, 0L) != null)
        store.close()
        prefs().edit()
            .putString(KEY_TRANSACTION, TRANSACTION_ID)
            .putString(KEY_CASHIER, CASHIER)
            .putString(KEY_ORIGIN, ORIGIN)
            .putString(KEY_CLIENT, CLIENT)
            .putString(KEY_BODY_SHA256, sha256(BODY.encodeToByteArray()))
            .commit()
    }

    @Test
    @SpecialHarnessOnly
    fun verifyMutationAfterProcessDeath() {
        val store = SqlitePendingMutationStore(context, DATABASE)
        val id = requireNotNull(prefs().getString(KEY_TRANSACTION, null))
        val expectedIdentity = RecoveryIdentity(
            requireNotNull(prefs().getString(KEY_CASHIER, null)),
            requireNotNull(prefs().getString(KEY_ORIGIN, null)),
            requireNotNull(prefs().getString(KEY_CLIENT, null)),
        )
        val record = requireNotNull(store.find(id, expectedIdentity))

        assertEquals(TRANSACTION_ID, record.transactionId)
        assertEquals(identity, record.identity)
        assertEquals(PendingMutationState.SENDING, record.state)
        assertEquals(requireNotNull(prefs().getString(KEY_BODY_SHA256, null)), sha256(record.body))
        assertEquals(BODY, record.body.decodeToString())
        assertTrue(store.recoverStaleSending(record.transactionId, expectedIdentity))
        assertEquals(PendingMutationState.WAITING_RETRY, store.find(id, expectedIdentity)?.state)
        store.close()
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DATABASE = "pending-mutations-process-death.db"
        const val PREFS = "pending-mutations-process-death"
        const val KEY_TRANSACTION = "transaction"
        const val KEY_CASHIER = "cashier"
        const val KEY_ORIGIN = "origin"
        const val KEY_CLIENT = "client"
        const val KEY_BODY_SHA256 = "body_sha256"
        const val TRANSACTION_ID = "123e4567-e89b-42d3-a456-426614174000"
        const val CASHIER = "cashier@example.com"
        const val ORIGIN = "https://oauth-staging.rotiropi.web.id"
        const val CLIENT = "rotiropi.mobilepos.task9.staging"
        const val BODY = "{\"pos_profile\":\"OUTLET-01\",\"client_accepted_grand_total\":\"1.00\",\"items\":[],\"payments\":[]}"
    }
}
