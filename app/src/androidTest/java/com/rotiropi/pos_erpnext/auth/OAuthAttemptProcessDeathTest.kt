package com.rotiropi.pos_erpnext.auth

import android.content.Context
import android.net.Uri
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import androidx.test.core.app.ApplicationProvider
import com.rotiropi.pos_erpnext.MobilePosApplication
import com.rotiropi.pos_erpnext.data.api.ApiResult
import com.rotiropi.pos_erpnext.data.api.BootstrapResponseDto
import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import com.rotiropi.pos_erpnext.data.api.MobilePosRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.test.SpecialHarnessOnly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests OAuth attempt persistence across process death boundaries.
 * Host-script driven: tools/oauth-process-death.sh invokes these methods by exact name
 * across separate process instances.
 */
@RunWith(AndroidJUnit4::class)
class OAuthAttemptProcessDeathTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Consumed-before-token boundary 1: persist PENDING before host force-stops.
     * Later boundary dispatches exchange but deliberately omits token persistence.
     */
    @Test
    @SpecialHarnessOnly
    fun writePendingAttemptForConsumedBeforeTokenPersistence() {
        val store = OAuthAttemptStore(context)
        store.clear()
        TokenStore(context).clear()
        context.getSharedPreferences(PROCESS_DEATH_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store.write(createPendingAttempt(CONSUMED_BEFORE_TOKEN_STATE))

        assertEquals(OAuthAttempt.Status.PENDING, store.read()?.status)
    }

    /**
     * Consumed-before-token boundary 2: validate callback, persist CONSUMED, complete
     * exactly one exchange, and leave TokenStore empty for immediate host force-stop.
     */
    @Test
    @SpecialHarnessOnly
    fun consumeAttemptBeforeTokenPersistence() {
        val store = OAuthAttemptStore(context)
        val tokens = ThrowBeforeWriteTokenStore(context)
        val pending = requireNotNull(store.read())
        assertEquals(CONSUMED_BEFORE_TOKEN_STATE, pending.state)
        assertEquals(OAuthAttempt.Status.PENDING, pending.status)
        val callback = callback(pending, "consumed-before-token-code")
        assertNotNull(AuthorizationResponse.fromIntent(callback))
        val exchanger = CountingExchanger()
        val result = productionCoordinator(store, tokens, exchanger).handleCompletion(callback)

        assertEquals(
            OAuthCompletionResult.Reason.TOKEN_PERSISTENCE_FAILED,
            (result as OAuthCompletionResult.Failed).reason
        )
        assertEquals(OAuthAttempt.Status.CONSUMED, store.read()?.status)
        assertEquals(1, exchanger.exchangeCount)
        assertTrue(tokens.writeAttempted)
        assertNull(tokens.read(MobilePosApplication.CANONICAL_ORIGIN, MobilePosApplication.CLIENT_ID))
        context.getSharedPreferences(PROCESS_DEATH_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(EXCHANGE_COUNT, exchanger.exchangeCount)
            .commit()
    }

    @Test
    @SpecialHarnessOnly
    fun malformedConsumedAttemptNeverExchangesAfterRestart() {
        val store = OAuthAttemptStore(context)
        val tokens = TokenStore(context)
        store.clear()
        tokens.clear()
        val consumed = createPendingAttempt("malformed-consumed").copy(
            status = OAuthAttempt.Status.CONSUMED,
            canonicalOrigin = "https://evil.example.com"
        )
        store.write(consumed)
        val exchanger = CountingExchanger()
        val owner = AuthenticationOwner(productionCoordinator(store, tokens, exchanger))

        assertFalse(owner.isAuthenticated)
        assertEquals(0, exchanger.exchangeCount)
        assertNull(store.read())
        assertNull(tokens.read(MobilePosApplication.CANONICAL_ORIGIN, MobilePosApplication.CLIENT_ID))
    }

    /** Real-browser gate: execute one approved authenticated bootstrap read. */
    @Test
    @SpecialHarnessOnly
    fun verifyLiveAuthenticatedBootstrapRead() {
        val app = context as MobilePosApplication

        val result = app.mobilePosApiClient.execute(
            MobilePosRequest.get(MobilePosEndpoint.BOOTSTRAP_GET, emptyMap()),
            BootstrapResponseDto.serializer()
        )

        assertTrue("bootstrap result=${result.describe()}", result is ApiResult.Success)
        val bootstrap = (result as ApiResult.Success).data
        assertTrue(bootstrap.user.name.isNotBlank())
        assertTrue(bootstrap.profiles.isNotEmpty())
    }

    /** Real-browser gate: stale callback after live logout cannot restore authentication. */
    @Test
    @SpecialHarnessOnly
    fun verifyStaleCallbackAfterLiveLogoutCannotAuthenticate() {
        val app = context as MobilePosApplication
        val attempts = OAuthAttemptStore(context)
        val tokens = TokenStore(context)
        val stale = createPendingAttempt("stale-after-live-logout")

        app.authenticationOwner.logout()

        assertFalse(app.authenticationOwner.isAuthenticated)
        assertNull(attempts.read())
        assertNull(tokens.read(MobilePosApplication.CANONICAL_ORIGIN, MobilePosApplication.CLIENT_ID))

        val result = app.authenticationOwner.handleCompletion(callback(stale, "stale-code"))

        assertEquals(
            OAuthCompletionResult.Reason.NO_PERSISTED_ATTEMPT,
            (result as OAuthCompletionResult.Failed).reason
        )
        assertFalse(app.authenticationOwner.isAuthenticated)
        assertNull(attempts.read())
        assertNull(tokens.read(MobilePosApplication.CANONICAL_ORIGIN, MobilePosApplication.CLIENT_ID))
    }

    /** Boundary 3: restart clears CONSUMED without token and never exchanges again. */
    @Test
    @SpecialHarnessOnly
    fun recoverConsumedBeforeTokenPersistenceAfterDeath() {
        val store = OAuthAttemptStore(context)
        val tokens = TokenStore(context)
        val exchanger = CountingExchanger()
        val owner = AuthenticationOwner(productionCoordinator(store, tokens, exchanger))

        assertFalse(owner.isAuthenticated)
        assertEquals(0, exchanger.exchangeCount)
        assertEquals(1, persistedExchangeCount())
        assertNull(store.read())
        assertNull(tokens.read(MobilePosApplication.CANONICAL_ORIGIN, MobilePosApplication.CLIENT_ID))
    }

    /** Boundary 4: second relaunch remains unauthenticated and remains non-replaying. */
    @Test
    @SpecialHarnessOnly
    fun verifySecondConsumedBeforeTokenRelaunchDoesNotExchange() {
        val store = OAuthAttemptStore(context)
        val tokens = TokenStore(context)
        val exchanger = CountingExchanger()
        val owner = AuthenticationOwner(productionCoordinator(store, tokens, exchanger))

        assertFalse(owner.isAuthenticated)
        assertEquals(0, exchanger.exchangeCount)
        assertEquals(1, persistedExchangeCount())
        assertNull(store.read())
        assertNull(tokens.read(MobilePosApplication.CANONICAL_ORIGIN, MobilePosApplication.CLIENT_ID))
    }

    /**
     * Boundary 1: Write pending attempt before host force-stops the process.
     */
    @Test
    @SpecialHarnessOnly
    fun writePendingAttempt() {
        val store = OAuthAttemptStore(context)
        store.clear()
        TokenStore(context).clear()
        context.getSharedPreferences(PROCESS_DEATH_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store.write(createPendingAttempt("pending-state"))

        val result = store.read()
        assertNotNull(result)
        assertEquals("pending-state", result!!.state)
        assertEquals(OAuthAttempt.Status.PENDING, result.status)
    }

    @Test
    @SpecialHarnessOnly
    fun completionExchangeRunsOffMainThread() {
        val store = OAuthAttemptStore(context)
        val tokens = TokenStore(context)
        store.clear()
        tokens.clear()
        val attempt = createPendingAttempt("off-main-exchange")
        store.write(attempt)
        val called = CountDownLatch(1)
        val exchangeThread = arrayOfNulls<Thread>(1)
        val exchanger = object : TokenExchanger {
            override fun exchangeAuthorizationCode(
                config: OAuthConfiguration,
                code: String,
                codeVerifier: String
            ): OAuthTokens {
                exchangeThread[0] = Thread.currentThread()
                called.countDown()
                return OAuthTokens("off-main", "refresh", Long.MAX_VALUE)
            }

            override fun refreshAccessToken(config: OAuthConfiguration, refreshToken: String): OAuthTokens? = null
        }
        val owner = AuthenticationOwner(productionCoordinator(store, tokens, exchanger))
        owner.handleCompletionAsync(callback(attempt, "off-main-code"))

        assertTrue(called.await(5, TimeUnit.SECONDS))
        assertTrue(exchangeThread[0] !== android.os.Looper.getMainLooper().thread)
        waitFor { owner.isAuthenticated }
        tokens.clear()
    }

    /**
     * Boundary 2: Restore PENDING, exchange once, persist token, then retain CONSUMED
     * so host process death lands between token persistence and terminal cleanup.
     */
    @Test
    @SpecialHarnessOnly
    fun persistTokenBeforeTerminalCleanup() {
        val store = CleanupBlockingAttemptStore(context)
        val tokens = TokenStore(context)
        val persisted = store.read()
        assertNotNull(persisted)
        assertEquals(OAuthAttempt.Status.PENDING, persisted!!.status)
        val exchanger = CountingExchanger()
        val coordinator = productionCoordinator(store, tokens, exchanger)

        store.blockTerminalCleanup = true
        val result = coordinator.handleCompletion(callback(persisted, "authorization-code"))

        assertTrue(result is OAuthCompletionResult.Success)
        assertEquals(1, exchanger.exchangeCount)
        assertEquals(OAuthAttempt.Status.CONSUMED, store.read()?.status)
        assertNotNull(tokens.read(MobilePosApplication.CANONICAL_ORIGIN, MobilePosApplication.CLIENT_ID))
        context.getSharedPreferences(PROCESS_DEATH_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(EXCHANGE_COUNT, exchanger.exchangeCount)
            .commit()
    }

    /** Boundary 3: Restart restores matching token and removes CONSUMED without exchange. */
    @Test
    @SpecialHarnessOnly
    fun recoverPersistedTokenAfterDeath() {
        val store = OAuthAttemptStore(context)
        val tokens = TokenStore(context)
        val app = context as MobilePosApplication

        assertTrue(app.authenticationOwner.isAuthenticated)
        assertEquals(1, persistedExchangeCount())
        assertNotNull(tokens.read(MobilePosApplication.CANONICAL_ORIGIN, MobilePosApplication.CLIENT_ID))
        assertNull(store.read())
    }

    /** Boundary 4: A second restart remains terminal and performs no exchange. */
    @Test
    @SpecialHarnessOnly
    fun verifySecondRelaunchStillDoesNotExchange() {
        val store = OAuthAttemptStore(context)
        val tokens = TokenStore(context)
        val exchanger = CountingExchanger()
        val owner = AuthenticationOwner(productionCoordinator(store, tokens, exchanger))

        assertTrue(owner.isAuthenticated)
        assertEquals(0, exchanger.exchangeCount)
        assertEquals(1, persistedExchangeCount())
        assertNull(store.read())
        tokens.clear()
    }

    private fun productionCoordinator(
        store: OAuthAttemptStore,
        tokens: TokenStore,
        exchanger: TokenExchanger = CountingExchanger()
    ): OAuthCoordinator = OAuthCoordinator(
        context,
        OAuthConfiguration.create(
            MobilePosApplication.CANONICAL_ORIGIN,
            MobilePosApplication.CLIENT_ID,
            MobilePosApplication.REDIRECT_URI,
            OAuthConfiguration.AUTHORIZE_PATH,
            OAuthConfiguration.TOKEN_PATH,
            OAuthConfiguration.SCOPE,
            OAuthConfiguration.ATTEMPT_LIFETIME_MINUTES
        ),
        store,
        tokens,
        ProcessDeathLauncher,
        exchanger
    )

    private fun callback(attempt: OAuthAttempt, code: String): android.content.Intent {
        val request = AuthorizationRequest.Builder(
            AuthorizationServiceConfiguration(
                Uri.parse("${attempt.canonicalOrigin}${OAuthConfiguration.AUTHORIZE_PATH}"),
                Uri.parse("${attempt.canonicalOrigin}${OAuthConfiguration.TOKEN_PATH}")
            ),
            attempt.clientId,
            ResponseTypeValues.CODE,
            Uri.parse(attempt.redirectUri)
        )
            .setState(attempt.state)
            .setScope(OAuthConfiguration.SCOPE)
            .setCodeVerifier(attempt.codeVerifier, attempt.codeChallenge, "S256")
            .build()
        return AuthorizationResponse.Builder(request)
            .setState(attempt.state)
            .setAuthorizationCode(code)
            .build()
            .toIntent()
            .apply {
                action = AuthCompletionActivity.ACTION_AUTH_COMPLETION
                data = Uri.parse("${attempt.redirectUri}?code=$code&state=${attempt.state}")
                putExtra(AuthCompletionActivity.EXTRA_STATE, attempt.state)
            }
    }

    private fun ApiResult<*>.describe(): String = when (this) {
        is ApiResult.Success -> "success"
        is ApiResult.ExpectedFailure -> "expected_failure:${error.code}"
        is ApiResult.TransportFailure -> "transport_failure:${kind.name}:${statusCode ?: "none"}"
        is ApiResult.ProtocolFailure -> "protocol_failure:$reason"
    }

    private fun waitFor(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!predicate() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(predicate())
    }

    private fun persistedExchangeCount(): Int =
        context.getSharedPreferences(PROCESS_DEATH_PREFS, Context.MODE_PRIVATE)
            .getInt(EXCHANGE_COUNT, -1)

    private class ThrowBeforeWriteTokenStore(context: Context) : TokenStore(context) {
        var writeAttempted = false

        override fun write(tokens: OAuthTokens) {
            writeAttempted = true
            throw IllegalStateException("Injected process death before token persistence")
        }
    }

    private class CleanupBlockingAttemptStore(context: Context) : OAuthAttemptStore(context) {
        var blockTerminalCleanup = false

        override fun clearIfState(state: String): Boolean =
            if (blockTerminalCleanup && read()?.status == OAuthAttempt.Status.CONSUMED) {
                false
            } else {
                super.clearIfState(state)
            }
    }

    private class CountingExchanger : TokenExchanger {
        var exchangeCount = 0

        override fun exchangeAuthorizationCode(
            config: OAuthConfiguration,
            code: String,
            codeVerifier: String
        ): OAuthTokens {
            exchangeCount++
            return OAuthTokens("persisted-access", "persisted-refresh", Long.MAX_VALUE)
        }

        override fun refreshAccessToken(
            config: OAuthConfiguration,
            refreshToken: String
        ): OAuthTokens? = null
    }

    private object ProcessDeathLauncher : AuthorizationLauncher {
        override fun launchAuthorization(
            config: OAuthConfiguration,
            state: String,
            codeVerifier: String,
            codeChallenge: String
        ) = Unit

        override fun completionPendingIntent(state: String): android.app.PendingIntent? = null
        override fun cancelPendingIntent(state: String): android.app.PendingIntent? = null
    }

    private fun createPendingAttempt(state: String): OAuthAttempt {
        val verifier = generateCodeVerifier()
        return OAuthAttempt(
            canonicalOrigin = MobilePosApplication.CANONICAL_ORIGIN,
            clientId = MobilePosApplication.CLIENT_ID,
            state = state,
            codeVerifier = verifier,
            codeChallenge = generateCodeChallenge(verifier),
            redirectUri = MobilePosApplication.REDIRECT_URI,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 600_000,
            status = OAuthAttempt.Status.PENDING
        )
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    }

    private fun generateCodeChallenge(verifier: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(verifier.toByteArray(charset("US-ASCII")))
        return android.util.Base64.encodeToString(
            digest,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    }

    private companion object {
        const val PROCESS_DEATH_PREFS = "oauth_process_death_test"
        const val EXCHANGE_COUNT = "exchange_count"
        const val CONSUMED_BEFORE_TOKEN_STATE = "consumed-before-token-state"
    }
}
