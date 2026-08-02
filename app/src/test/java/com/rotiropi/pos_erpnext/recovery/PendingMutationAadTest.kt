package com.rotiropi.pos_erpnext.recovery

import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PendingMutationAadTest {
    private val identity = RecoveryIdentity("cashier", "https://example.test", "client")
    private val mutation = PendingMutation(
        transactionId = "123e4567-e89b-42d3-a456-426614174000",
        identity = identity,
        endpoint = MobilePosEndpoint.SALES_SUBMIT,
        body = "{}".encodeToByteArray(),
        contentType = "application/json",
        serializerIdentity = "sale-v1",
        bodyFormatVersion = PendingMutation.BODY_FORMAT_VERSION,
        createdAtMillis = 1L,
    )

    @Test
    fun requestAadIsCanonicalAndDeterministic() {
        assertEquals(
            PendingMutationAad.request(mutation).toList(),
            PendingMutationAad.request(mutation.copy(createdAtMillis = 99L)).toList(),
        )
    }

    @Test
    fun requestAadChangesForEveryBoundRequestField() {
        val original = PendingMutationAad.request(mutation)
        val variants = listOf(
            mutation.copy(transactionId = "123e4567-e89b-42d3-a456-426614174001"),
            mutation.copy(identity = identity.copy(cashier = "other")),
            mutation.copy(identity = identity.copy(canonicalOrigin = "https://other.test")),
            mutation.copy(identity = identity.copy(clientId = "other-client")),
            mutation.copy(endpoint = MobilePosEndpoint.SESSIONS_OPEN),
            mutation.copy(contentType = "text/plain"),
            mutation.copy(serializerIdentity = "other-v1"),
            mutation.copy(bodyFormatVersion = 99),
        )

        variants.forEach { assertFalse(original.contentEquals(PendingMutationAad.request(it))) }
    }

    @Test
    fun terminalAadBindsTerminalStateAndResultFormat() {
        val original = PendingMutationAad.terminal(mutation, PendingMutationState.COMPLETED)

        assertFalse(original.contentEquals(PendingMutationAad.terminal(mutation, PendingMutationState.REJECTED)))
    }
}
