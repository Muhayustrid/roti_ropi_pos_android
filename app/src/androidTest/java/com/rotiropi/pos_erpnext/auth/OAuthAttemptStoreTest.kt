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
import java.util.Collections
import java.util.concurrent.CountDownLatch
import javax.crypto.SecretKey

/**
 * Tests OAuthAttemptStore encrypted binary records: AES-GCM tamper detection,
 * version rejection, unique Cipher-created IVs, clear, and consumed persistence.
 */
@RunWith(AndroidJUnit4::class)
class OAuthAttemptStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun writeAndReadPreservesMetadata() {
        val store = OAuthAttemptStore(context)
        store.clear()
        val original = createAttempt("state-1")

        store.write(original)
        val actual = store.read()

        assertNotNull(actual)
        assertEquals(original, actual)
    }

    @Test
    fun consumePreservesMetadataAndPersistsConsumedStatus() {
        val store = OAuthAttemptStore(context)
        store.clear()
        val original = createAttempt("state-consume")
        store.write(original)

        assertNotNull(store.consume(original.state))
        val actual = store.read()

        assertNotNull(actual)
        assertEquals(OAuthAttempt.Status.CONSUMED, actual!!.status)
        assertEquals(original.state, actual.state)
        assertEquals(original.canonicalOrigin, actual.canonicalOrigin)
        assertEquals(original.clientId, actual.clientId)
        assertEquals(original.codeVerifier, actual.codeVerifier)
        assertEquals(original.expiresAt, actual.expiresAt)
    }

    @Test
    fun concurrentStoreInstancesAllowOnlyOneConsume() {
        val first = OAuthAttemptStore(context)
        val second = OAuthAttemptStore(context)
        first.clear()
        first.write(createAttempt("state-race"))
        val start = CountDownLatch(1)
        val consumed = Collections.synchronizedList(mutableListOf<OAuthAttempt?>())
        val threads = listOf(first, second).map { store ->
            Thread {
                start.await()
                consumed += store.consume("state-race")
            }
        }

        threads.forEach(Thread::start)
        start.countDown()
        threads.forEach { it.join(5_000) }

        assertEquals(1, consumed.count { it != null })
        assertEquals(OAuthAttempt.Status.CONSUMED, OAuthAttemptStore(context).read()?.status)
        assertNull(OAuthAttemptStore(context).consume("state-race"))
    }

    @Test
    fun tamperedCiphertextReturnsNullAndClearsRecord() {
        val store = OAuthAttemptStore(context)
        store.clear()
        store.write(createAttempt("state-tamper"))

        val record = recordFile()
        val bytes = record.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        record.writeBytes(bytes)

        assertNull(store.read())
        assertTrue(!record.exists())
    }

    @Test
    fun missingKeyClearsRecordAndNextWriteRegeneratesUsableKey() {
        val store = OAuthAttemptStore(context)
        store.clear()
        store.write(createAttempt("before-key-loss"))
        keyStore().deleteEntry(OAuthAttemptStore.KEY_ALIAS)

        assertNull(store.read())
        assertTrue(!recordFile().exists())
        assertTrue(!keyStore().containsAlias(OAuthAttemptStore.KEY_ALIAS))

        store.write(createAttempt("after-key-loss"))

        assertEquals("after-key-loss", store.read()?.state)
        assertTrue(keyStore().containsAlias(OAuthAttemptStore.KEY_ALIAS))
    }

    @Test
    fun partialAtomicWritePreservesPreviousValidRecord() {
        val store = OAuthAttemptStore(context)
        store.clear()
        store.write(createAttempt("stable"))
        val atomicFile = android.util.AtomicFile(recordFile())
        val output = atomicFile.startWrite()
        output.write(byteArrayOf(0, 0, 0, 1, 1, 2, 3))
        atomicFile.failWrite(output)

        assertEquals("stable", store.read()?.state)
    }

    @Test
    fun unknownVersionReturnsNull() {
        val store = OAuthAttemptStore(context)
        store.clear()
        store.write(createAttempt("state-version"))

        val record = recordFile()
        val bytes = record.readBytes()
        ByteBuffer.wrap(bytes, 0, 4).putInt(99)
        record.writeBytes(bytes)

        assertNull(store.read())
    }

    @Test
    fun invalidatedKeyRecoveryDeletesAliasAndRecordBeforeLaterWrite() {
        val store = RecoveringAttemptStore(context)
        store.clear()
        store.write(createAttempt("before-invalidation"))
        store.failReadWithInvalidatedKey = true

        assertNull(store.read())
        assertTrue(!recordFile().exists())
        assertTrue(!keyStore().containsAlias(OAuthAttemptStore.KEY_ALIAS))

        store.failReadWithInvalidatedKey = false
        store.write(createAttempt("after-invalidation"))
        assertEquals("after-invalidation", store.read()?.state)
    }

    @Test
    fun malformedAndTruncatedRecordsReturnNull() {
        val store = OAuthAttemptStore(context)
        store.clear()

        recordFile().parentFile?.mkdirs()
        recordFile().writeBytes(byteArrayOf(0, 1, 2))

        assertNull(store.read())
    }

    @Test
    fun eachWriteUsesUniqueIv() {
        val store = OAuthAttemptStore(context)
        store.clear()

        store.write(createAttempt("state-a"))
        val firstIv = recordFile().readBytes().copyOfRange(4, 16)

        store.write(createAttempt("state-b"))
        val secondIv = recordFile().readBytes().copyOfRange(4, 16)

        assertNotEquals(firstIv.toList(), secondIv.toList())
    }

    @Test
    fun clearRemovesRecord() {
        val store = OAuthAttemptStore(context)
        store.write(createAttempt("state-clear"))
        assertNotNull(store.read())

        store.clear()

        assertNull(store.read())
        assertTrue(!recordFile().exists())
    }

    private class RecoveringAttemptStore(context: Context) : OAuthAttemptStore(context) {
        var failReadWithInvalidatedKey = false

        override fun getExistingKey(): SecretKey {
            if (failReadWithInvalidatedKey) {
                throw android.security.keystore.KeyPermanentlyInvalidatedException("injected")
            }
            return super.getExistingKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun createAttempt(state: String): OAuthAttempt {
        return OAuthAttempt(
            canonicalOrigin = "https://example.com",
            clientId = "test-client",
            state = state,
            codeVerifier = "verifier-$state",
            codeChallenge = "challenge-$state",
            redirectUri = "https://example.com/android/oauth2redirect",
            createdAt = 1_000L,
            expiresAt = 601_000L,
            status = OAuthAttempt.Status.PENDING
        )
    }

    private fun recordFile(): File = File(
        File(context.filesDir, "oauth_attempts"),
        "attempt_record.enc"
    )
}
