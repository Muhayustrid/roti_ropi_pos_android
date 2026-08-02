package com.rotiropi.pos_erpnext.recovery

import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PendingMutationSerializationTest {
    @Test
    fun encryptionUsesFreshIvAndRejectsAuthenticationTagTampering() {
        val crypto = PendingMutationCrypto.forTesting(ByteArray(32) { 7 })
        val first = crypto.encrypt("exact-body".encodeToByteArray())
        val second = crypto.encrypt("exact-body".encodeToByteArray())

        assertFalse(first.iv.contentEquals(second.iv))
        assertEquals("exact-body", crypto.decrypt(first).decodeToString())
        first.tag[0] = (first.tag[0].toInt() xor 1).toByte()
        org.junit.Assert.assertThrows(PendingMutationCryptoException::class.java) { crypto.decrypt(first) }
    }

    @Test
    fun encryptionRejectsMetadataAadSubstitution() {
        val crypto = PendingMutationCrypto.forTesting(ByteArray(32) { 7 })
        val encrypted = crypto.encrypt("exact-body".encodeToByteArray(), "request-aad".encodeToByteArray())

        assertEquals("exact-body", crypto.decrypt(encrypted, "request-aad".encodeToByteArray()).decodeToString())
        org.junit.Assert.assertThrows(PendingMutationCryptoException::class.java) {
            crypto.decrypt(encrypted, "changed-endpoint-aad".encodeToByteArray())
        }
    }

    @Test
    fun unknownBodyFormatNeedsManualRecovery() {
        val record = PendingMutation(
            transactionId = "123e4567-e89b-42d3-a456-426614174000",
            identity = RecoveryIdentity("cashier", "https://example.test", "client"),
            endpoint = MobilePosEndpoint.SALES_SUBMIT,
            body = byteArrayOf(1),
            contentType = "application/json",
            serializerIdentity = "SubmitSaleResponseDto",
            bodyFormatVersion = 99,
            createdAtMillis = 1,
        )
        assertEquals(PendingMutationState.MANUAL_RECOVERY, record.withValidatedFormat().state)
    }
}
