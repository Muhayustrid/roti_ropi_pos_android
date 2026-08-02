package com.rotiropi.pos_erpnext.recovery

import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

enum class PendingMutationState(val terminal: Boolean = false) {
    PREPARED,
    SENDING,
    WAITING_RETRY,
    AUTH_REQUIRED,
    REQUEST_IN_PROGRESS,
    CLOSING_QUEUED,
    COMPLETED(true),
    REJECTED(true),
    MANUAL_RECOVERY,
}

data class RecoveryIdentity(
    val cashier: String,
    val canonicalOrigin: String,
    val clientId: String,
)

data class PendingMutation(
    val transactionId: String,
    val identity: RecoveryIdentity,
    val endpoint: MobilePosEndpoint,
    val body: ByteArray,
    val contentType: String,
    val serializerIdentity: String,
    val bodyFormatVersion: Int,
    val createdAtMillis: Long,
    val state: PendingMutationState = PendingMutationState.PREPARED,
    val attemptCount: Int = 0,
    val nextEligibleAtMillis: Long = 0,
    val terminalResponse: ByteArray? = null,
    val reference: String? = null,
) {
    fun withValidatedFormat(): PendingMutation =
        if (bodyFormatVersion == BODY_FORMAT_VERSION) this else copy(state = PendingMutationState.MANUAL_RECOVERY)

    companion object {
        const val BODY_FORMAT_VERSION = 2
    }
}

data class EncryptedMutationBody(val iv: ByteArray, val ciphertext: ByteArray, val tag: ByteArray)
class PendingMutationCryptoException(cause: Throwable) : Exception(cause)

/** Canonical length-prefixed UTF-8 AAD. Request and terminal metadata never share a domain. */
object PendingMutationAad {
    private const val VERSION = 1

    fun request(record: PendingMutation): ByteArray = encode(
        "pending-mutation-request",
        record.transactionId,
        record.identity.cashier,
        record.identity.canonicalOrigin,
        record.identity.clientId,
        record.endpoint.path,
        record.endpoint.requiresIdempotency.toString(),
        record.endpoint.retryClass.name,
        record.contentType,
        record.serializerIdentity,
        record.bodyFormatVersion.toString(),
    )

    fun terminal(record: PendingMutation, state: PendingMutationState): ByteArray = encode(
        "pending-mutation-terminal",
        record.transactionId,
        record.identity.cashier,
        record.identity.canonicalOrigin,
        record.identity.clientId,
        record.endpoint.path,
        record.endpoint.requiresIdempotency.toString(),
        record.endpoint.retryClass.name,
        record.contentType,
        record.serializerIdentity,
        record.bodyFormatVersion.toString(),
        state.name,
        TERMINAL_RESULT_FORMAT,
    )

    private fun encode(domain: String, vararg fields: String): ByteArray = buildString {
        append(VERSION).append(':')
        append(fields.size + 1).append(':')
        appendField(domain)
        fields.forEach { appendField(it) }
    }.encodeToByteArray()

    private fun StringBuilder.appendField(value: String) {
        val bytes = value.encodeToByteArray()
        append(bytes.size).append(':').append(value)
    }

    const val TERMINAL_RESULT_FORMAT = "utf8-json-v1"
}

class PendingMutationCrypto private constructor(private val key: SecretKey) {
    fun encrypt(plain: ByteArray, aad: ByteArray = ByteArray(0)): EncryptedMutationBody = try {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            if (key.format == null) {
                init(Cipher.ENCRYPT_MODE, key)
            } else {
                val iv = ByteArray(IV_SIZE_BYTES).also(random::nextBytes)
                initGcmCipher(this, Cipher.ENCRYPT_MODE, key, iv, GCM_TAG_BITS)
            }
            if (aad.isNotEmpty()) updateAAD(aad)
        }
        val encrypted = cipher.doFinal(plain)
        EncryptedMutationBody(
            iv = cipher.iv,
            ciphertext = encrypted.copyOfRange(0, encrypted.size - GCM_TAG_BYTES),
            tag = encrypted.copyOfRange(encrypted.size - GCM_TAG_BYTES, encrypted.size),
        )
    } catch (error: GeneralSecurityException) {
        throw PendingMutationCryptoException(error)
    }

    fun decrypt(encrypted: EncryptedMutationBody, aad: ByteArray = ByteArray(0)): ByteArray = try {
        require(encrypted.iv.size == IV_SIZE_BYTES)
        require(encrypted.tag.size == GCM_TAG_BYTES)
        Cipher.getInstance(TRANSFORMATION).apply {
            initGcmCipher(this, Cipher.DECRYPT_MODE, key, encrypted.iv, GCM_TAG_BITS)
            if (aad.isNotEmpty()) updateAAD(aad)
        }.doFinal(encrypted.ciphertext + encrypted.tag)
    } catch (error: Exception) {
        throw PendingMutationCryptoException(error)
    }

    companion object {
        private val random = SecureRandom()
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8

        fun forTesting(key: ByteArray): PendingMutationCrypto =
            PendingMutationCrypto(SecretKeySpec(key, "AES"))

        fun fromKey(key: SecretKey): PendingMutationCrypto = PendingMutationCrypto(key)
    }
}
