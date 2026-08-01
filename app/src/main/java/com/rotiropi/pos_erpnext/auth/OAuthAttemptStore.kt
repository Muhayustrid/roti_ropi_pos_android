package com.rotiropi.pos_erpnext.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted, versioned, atomically replaced active OAuth attempt. */
open class OAuthAttemptStore(context: Context) {
    private val dataDir = File(context.filesDir, DIRECTORY).also { it.mkdirs() }
    private val atomicFile = AtomicFile(File(dataDir, RECORD_FILE))

    open fun read(): OAuthAttempt? = synchronized(PROCESS_LOCK) { readLocked() }

    open fun write(attempt: OAuthAttempt) = synchronized(PROCESS_LOCK) {
        writeLocked(attempt)
    }

    /** Atomically persists CONSUMED and returns attempt to exactly one caller. */
    open fun consume(state: String): OAuthAttempt? = synchronized(PROCESS_LOCK) {
        val current = readLocked() ?: return@synchronized null
        if (current.state != state || current.status != OAuthAttempt.Status.PENDING) {
            return@synchronized null
        }
        val consumed = current.copy(status = OAuthAttempt.Status.CONSUMED)
        writeLocked(consumed)
        consumed
    }

    open fun clearIfState(state: String): Boolean = synchronized(PROCESS_LOCK) {
        val current = readLocked() ?: return@synchronized false
        if (current.state != state) return@synchronized false
        atomicFile.delete()
        true
    }

    open fun clear() = synchronized(PROCESS_LOCK) {
        atomicFile.delete()
    }

    private fun readLocked(): OAuthAttempt? {
        val record = try {
            atomicFile.openRead().use { it.readBytes() }
        } catch (_: java.io.FileNotFoundException) {
            return null
        } catch (_: Exception) {
            atomicFile.delete()
            return null
        }

        return try {
            require(record.size >= HEADER_BYTES + GCM_TAG_BYTES)
            val header = ByteBuffer.wrap(record)
            val version = header.int
            require(version == RECORD_VERSION)
            val iv = ByteArray(IV_SIZE_BYTES).also(header::get)
            val ciphertext = ByteArray(header.remaining()).also(header::get)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad(version))
            decode(String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8))
        } catch (error: Exception) {
            atomicFile.delete()
            if (error.isUnusableKey()) deleteKeyAlias()
            null
        }
    }

    private fun writeLocked(attempt: OAuthAttempt) {
        val record = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            val iv = cipher.iv
            require(iv.size == IV_SIZE_BYTES)
            cipher.updateAAD(aad(RECORD_VERSION))
            val ciphertext = cipher.doFinal(encode(attempt).toByteArray(StandardCharsets.UTF_8))
            ByteBuffer.allocate(HEADER_BYTES + ciphertext.size)
                .putInt(RECORD_VERSION)
                .put(iv)
                .put(ciphertext)
                .array()
        } catch (error: Exception) {
            if (error.isUnusableKey()) {
                deleteKeyAlias()
                atomicFile.delete()
            }
            throw IllegalStateException("Unable to encrypt OAuth attempt", error)
        }

        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(record)
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            output?.let(atomicFile::failWrite)
            throw IllegalStateException("Unable to atomically persist OAuth attempt", error)
        }
    }

    private fun encode(attempt: OAuthAttempt): String = org.json.JSONObject().apply {
        put("canonical_origin", attempt.canonicalOrigin)
        put("client_id", attempt.clientId)
        put("state", attempt.state)
        put("code_verifier", attempt.codeVerifier)
        put("code_challenge", attempt.codeChallenge)
        put("redirect_uri", attempt.redirectUri)
        put("created_at_ms", attempt.createdAt)
        put("expires_at_ms", attempt.expiresAt)
        put("status", attempt.status.name)
    }.toString()

    private fun decode(value: String): OAuthAttempt {
        val json = org.json.JSONObject(value)
        return OAuthAttempt(
            canonicalOrigin = json.getString("canonical_origin"),
            clientId = json.getString("client_id"),
            state = json.getString("state"),
            codeVerifier = json.getString("code_verifier"),
            codeChallenge = json.getString("code_challenge"),
            redirectUri = json.getString("redirect_uri"),
            createdAt = json.getLong("created_at_ms"),
            expiresAt = json.getLong("expires_at_ms"),
            status = OAuthAttempt.Status.valueOf(json.getString("status"))
        ).also {
            require(it.canonicalOrigin.isNotBlank())
            require(it.clientId.isNotBlank())
            require(it.state.isNotBlank())
            require(it.codeVerifier.isNotBlank())
            require(it.codeChallenge.isNotBlank())
            require(it.redirectUri.isNotBlank())
            require(it.expiresAt > it.createdAt)
        }
    }

    private fun aad(version: Int): ByteArray =
        "$RECORD_TYPE:$version".toByteArray(StandardCharsets.US_ASCII)

    internal open fun getExistingKey(): SecretKey =
        keyStore().getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw UnrecoverableKeyException("OAuth attempt key missing")

    private fun getKey(): SecretKey {
        val keyStore = keyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setKeySize(KEY_SIZE_BITS)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun deleteKeyAlias() {
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun Throwable.isUnusableKey(): Boolean =
        this is KeyPermanentlyInvalidatedException ||
            this is UnrecoverableKeyException ||
            this is InvalidKeyException ||
            cause?.isUnusableKey() == true

    companion object {
        const val DIRECTORY = "oauth_attempts"
        const val RECORD_FILE = "attempt_record.enc"
        const val RECORD_VERSION = 1
        const val KEY_ALIAS = "oauth_attempt_key_v1"
        private val PROCESS_LOCK = Any()
        private const val RECORD_TYPE = "mobile-pos-oauth-attempt"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val IV_SIZE_BYTES = 12
        private const val HEADER_BYTES = 4 + IV_SIZE_BYTES
    }
}
