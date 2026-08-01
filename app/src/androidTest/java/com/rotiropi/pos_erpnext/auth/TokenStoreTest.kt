package com.rotiropi.pos_erpnext.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.SecretKey

/**
 * Tests TokenStore encrypted binary records: AES-GCM tamper detection,
 * version rejection, unique Cipher-created IVs, and logout cleanup.
 */
@RunWith(AndroidJUnit4::class)
class TokenStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val origin = "https://example.com"
    private val clientId = "test-client"

    private fun tokens(
        accessToken: String,
        refreshToken: String? = "refresh",
        expiresAt: Long = 601_000L,
        canonicalOrigin: String = origin,
        oauthClientId: String = clientId
    ) = OAuthTokens(
        accessToken,
        refreshToken,
        expiresAt,
        canonicalOrigin = canonicalOrigin,
        clientId = oauthClientId
    )

    @Test
    fun writeAndReadPreservesTokens() {
        val store = TokenStore(context)
        store.clear()
        val tokens = tokens("access-1", "refresh-1", 601_000L)

        store.write(tokens)
        val actual = store.readUnboundForTest()

        assertNotNull(actual)
        assertEquals(tokens, actual)
    }

    @Test
    fun nullRefreshTokenRoundTripsAsNull() {
        val store = TokenStore(context)
        store.clear()
        store.write(tokens("access-only", null, 601_000L))

        val actual = store.readUnboundForTest()

        assertNotNull(actual)
        assertNull(actual!!.refreshToken)
    }

    @Test
    fun matchingOriginAndClientRecordAccepted() {
        val store = TokenStore(context)
        store.clear()
        store.write(tokens("bound"))

        assertEquals("bound", store.read(origin, clientId)?.accessToken)
    }

    @Test
    fun originMismatchRejectedAndCleared() {
        val store = TokenStore(context)
        store.clear()
        store.write(tokens("origin-bound"))

        assertNull(store.read("https://other.example.com", clientId))
        assertNull(store.readUnboundForTest())
    }

    @Test
    fun clientIdMismatchRejectedAndCleared() {
        val store = TokenStore(context)
        store.clear()
        store.write(tokens("client-bound"))

        assertNull(store.read(origin, "other-client"))
        assertNull(store.readUnboundForTest())
    }

    @Test
    fun tamperedIvReturnsNullAndClearsRecord() {
        val store = TokenStore(context)
        store.clear()
        store.write(tokens("access-iv"))

        mutateRecord(4)

        assertNull(store.readUnboundForTest())
        assertTrue(!recordFile().exists())
    }

    @Test
    fun tamperedTagReturnsNullAndClearsRecord() {
        val store = TokenStore(context)
        store.clear()
        store.write(tokens("access-tag"))

        mutateRecord(recordFile().readBytes().lastIndex)

        assertNull(store.readUnboundForTest())
        assertTrue(!recordFile().exists())
    }

    @Test
    fun tamperedCiphertextReturnsNullAndClearsRecord() {
        val store = TokenStore(context)
        store.clear()
        store.write(tokens("access-tamper", "refresh", 601_000L))

        mutateRecord(16)

        assertNull(store.readUnboundForTest())
        assertTrue(!recordFile().exists())
    }

    @Test
    fun missingKeyClearsRecordAndNextWriteRegeneratesUsableKey() {
        val store = TokenStore(context)
        store.clear()
        store.write(tokens("before-key-loss"))
        keyStore().deleteEntry(TokenStore.KEY_ALIAS)

        assertNull(store.readUnboundForTest())
        assertTrue(!recordFile().exists())
        assertTrue(!keyStore().containsAlias(TokenStore.KEY_ALIAS))

        store.write(tokens("after-key-loss"))

        assertEquals("after-key-loss", store.read(origin, clientId)?.accessToken)
        assertTrue(keyStore().containsAlias(TokenStore.KEY_ALIAS))
    }

    @Test
    fun partialAtomicWritePreservesPreviousValidRecord() {
        val store = TokenStore(context)
        store.clear()
        store.write(tokens("stable"))
        val atomicFile = android.util.AtomicFile(recordFile())
        val output = atomicFile.startWrite()
        output.write(byteArrayOf(0, 0, 0, 2, 1, 2, 3))
        atomicFile.failWrite(output)

        assertEquals("stable", store.read(origin, clientId)?.accessToken)
    }

    @Test
    fun unknownVersionReturnsNull() {
        val store = TokenStore(context)
        store.clear()
        store.write(tokens("access-version", "refresh", 601_000L))

        val record = recordFile()
        val bytes = record.readBytes()
        ByteBuffer.wrap(bytes, 0, 4).putInt(99)
        record.writeBytes(bytes)

        assertNull(store.readUnboundForTest())
    }

    @Test
    fun invalidatedKeyRecoveryDeletesAliasAndRecordBeforeLaterWrite() {
        val store = RecoveringTokenStore(context)
        store.clear()
        store.write(tokens("before-invalidation"))
        store.failReadWithInvalidatedKey = true

        assertNull(store.readUnboundForTest())
        assertTrue(!recordFile().exists())
        assertTrue(!keyStore().containsAlias(TokenStore.KEY_ALIAS))

        store.failReadWithInvalidatedKey = false
        store.write(tokens("after-invalidation"))
        assertEquals("after-invalidation", store.read(origin, clientId)?.accessToken)
    }

    @Test
    fun truncatedRecordReturnsNull() {
        val store = TokenStore(context)
        store.clear()

        recordFile().parentFile?.mkdirs()
        recordFile().writeBytes(byteArrayOf(0, 0, 0, 1))

        assertNull(store.readUnboundForTest())
    }

    @Test
    fun eachWriteUsesUniqueIv() {
        val store = TokenStore(context)
        store.clear()

        store.write(tokens("access-a", null, 601_000L))
        val firstIv = recordFile().readBytes().copyOfRange(4, 16)

        store.write(tokens("access-b", null, 601_000L))
        val secondIv = recordFile().readBytes().copyOfRange(4, 16)

        assertNotEquals(firstIv.toList(), secondIv.toList())
    }

    @Test
    fun clearRemovesRecordOnLogout() {
        val store = TokenStore(context)
        store.write(tokens("access-clear", "refresh", 601_000L))
        assertNotNull(store.readUnboundForTest())

        store.clear()

        assertNull(store.readUnboundForTest())
        assertTrue(!recordFile().exists())
    }

    private class RecoveringTokenStore(context: Context) : TokenStore(context) {
        var failReadWithInvalidatedKey = false

        override fun getExistingKey(): SecretKey {
            if (failReadWithInvalidatedKey) {
                throw android.security.keystore.KeyPermanentlyInvalidatedException("injected")
            }
            return super.getExistingKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun mutateRecord(index: Int) {
        val bytes = recordFile().readBytes()
        bytes[index] = (bytes[index].toInt() xor 0x01).toByte()
        recordFile().writeBytes(bytes)
    }

    private fun recordFile(): File = File(
        File(context.filesDir, "oauth_tokens"),
        "token_record.enc"
    )
}
