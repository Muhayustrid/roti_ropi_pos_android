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

/** Encrypted, identity-bound, versioned OAuth token record. */
open class TokenStore(context: Context) {
    private val dataDir = File(context.filesDir, DIRECTORY).also { it.mkdirs() }
    private val atomicFile = AtomicFile(File(dataDir, RECORD_FILE))

    internal open fun readUnboundForTest(): OAuthTokens? = synchronized(PROCESS_LOCK) { readLocked() }

    open fun read(canonicalOrigin: String, clientId: String): OAuthTokens? = synchronized(PROCESS_LOCK) {
        val tokens = readLocked() ?: return@synchronized null
        if (tokens.canonicalOrigin != canonicalOrigin || tokens.clientId != clientId ||
            tokens.recordVersion != RECORD_VERSION
        ) {
            atomicFile.delete()
            return@synchronized null
        }
        tokens
    }

    open fun write(tokens: OAuthTokens) = synchronized(PROCESS_LOCK) {
        require(tokens.canonicalOrigin.isNotBlank()) { "Token origin binding is required" }
        require(tokens.clientId.isNotBlank()) { "Token client binding is required" }
        require(tokens.accessToken.isNotBlank()) { "Access token is required" }
        require(tokens.recordVersion == RECORD_VERSION) { "Unexpected token record version" }
        writeLocked(tokens)
    }

    open fun clear() = synchronized(PROCESS_LOCK) {
        atomicFile.delete()
    }

    private fun readLocked(): OAuthTokens? {
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

    private fun writeLocked(tokens: OAuthTokens) {
        val record = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            val iv = cipher.iv
            require(iv.size == IV_SIZE_BYTES)
            cipher.updateAAD(aad(RECORD_VERSION))
            val ciphertext = cipher.doFinal(encode(tokens).toByteArray(StandardCharsets.UTF_8))
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
            throw IllegalStateException("Unable to encrypt OAuth tokens", error)
        }

        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(record)
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            output?.let(atomicFile::failWrite)
            throw IllegalStateException("Unable to atomically persist OAuth tokens", error)
        }
    }

    private fun encode(tokens: OAuthTokens): String = org.json.JSONObject().apply {
        put("record_version", tokens.recordVersion)
        put("canonical_origin", tokens.canonicalOrigin)
        put("client_id", tokens.clientId)
        put("access_token", tokens.accessToken)
        put("refresh_token", tokens.refreshToken ?: org.json.JSONObject.NULL)
        put("expires_at_ms", tokens.expiresAt)
    }.toString()

    private fun decode(value: String): OAuthTokens {
        val json = org.json.JSONObject(value)
        return OAuthTokens(
            accessToken = json.getString("access_token"),
            refreshToken = if (json.isNull("refresh_token")) null else json.getString("refresh_token"),
            expiresAt = json.getLong("expires_at_ms"),
            canonicalOrigin = json.getString("canonical_origin"),
            clientId = json.getString("client_id"),
            recordVersion = json.getInt("record_version")
        ).also {
            require(it.recordVersion == RECORD_VERSION)
            require(it.canonicalOrigin.isNotBlank())
            require(it.clientId.isNotBlank())
            require(it.accessToken.isNotBlank())
            require(it.expiresAt > 0)
        }
    }

    private fun aad(version: Int): ByteArray =
        "$RECORD_TYPE:$version".toByteArray(StandardCharsets.US_ASCII)

    internal open fun getExistingKey(): SecretKey =
        keyStore().getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw UnrecoverableKeyException("OAuth token key missing")

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
        const val DIRECTORY = "oauth_tokens"
        const val RECORD_FILE = "token_record.enc"
        const val RECORD_VERSION = 2
        const val KEY_ALIAS = "oauth_token_key_v2"
        private val PROCESS_LOCK = Any()
        private const val RECORD_TYPE = "mobile-pos-oauth-tokens"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val IV_SIZE_BYTES = 12
        private const val HEADER_BYTES = 4 + IV_SIZE_BYTES
    }
}
