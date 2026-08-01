package com.rotiropi.pos_erpnext.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val CANONICAL_ORIGIN = "https://oauth-staging.rotiropi.web.id"
private const val CLIENT_ID = "rotiropi.mobilepos.task9.staging"
private const val REDIRECT_URI =
    "https://oauth-staging.rotiropi.web.id/android/oauth2redirect"
private const val AUTHORIZE_PATH = "/api/method/frappe.integrations.oauth2.authorize"
private const val TOKEN_PATH = "/api/method/frappe.integrations.oauth2.get_token"
private const val SCOPE = "all"
private const val LIFETIME_MINUTES = 10L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class OAuthCoordinatorTest {

    private val context: Context get() = RuntimeEnvironment.application

    private fun config() = OAuthConfiguration.create(
        CANONICAL_ORIGIN, CLIENT_ID, REDIRECT_URI, AUTHORIZE_PATH, TOKEN_PATH, SCOPE, LIFETIME_MINUTES
    )

    private fun coordinator(
        attemptStore: OAuthAttemptStore,
        tokenStore: TokenStore,
        launcher: AuthorizationLauncher = RecordingLauncher(),
        exchanger: TokenExchanger = RecordingExchanger()
    ) = OAuthCoordinator(context, config(), attemptStore, tokenStore, launcher, exchanger)

    @Test
    fun `beginAuthorization persists pending attempt with S256 challenge before launch`() {
        val attemptStore = RecordingAttemptStore(context)
        val launcher = RecordingLauncher()

        coordinator(attemptStore, RecordingTokenStore(context), launcher).beginAuthorization()

        val attempt = attemptStore.written
        assertNotNull(attempt)
        assertEquals(OAuthAttempt.Status.PENDING, attempt!!.status)
        assertEquals(CANONICAL_ORIGIN, attempt.canonicalOrigin)
        assertEquals(CLIENT_ID, attempt.clientId)
        assertEquals(REDIRECT_URI, attempt.redirectUri)
        assertEquals(LIFETIME_MINUTES * 60 * 1000, attempt.expiresAt - attempt.createdAt)

        // Launcher receives exactly the persisted verifier/challenge (S256).
        assertEquals(attempt.state, launcher.state)
        assertEquals(attempt.codeVerifier, launcher.codeVerifier)
        assertEquals(attempt.codeChallenge, launcher.codeChallenge)
        assertEquals(expectedS256(attempt.codeVerifier), launcher.codeChallenge)
        assertTrue(launcher.codeChallenge!!.length >= 43)

        // Attempt written before the launch intent was requested.
        assertTrue(attemptStore.writeOrder < launcher.launchOrder)
    }

    @Test
    fun `successful completion exchanges code at fixed endpoint and persists tokens`() {
        val attemptStore = RecordingAttemptStore(context)
        val tokenStore = RecordingTokenStore(context)
        val exchanger = RecordingExchanger(
            tokens = OAuthTokens("access-1", "refresh-1", 9_999_999L)
        )
        val stored = pendingAttempt("state-ok")
        attemptStore.stored = stored

        val result = coordinator(attemptStore, tokenStore, exchanger = exchanger)
            .handleCompletion(callbackIntent(code = "auth-code", state = "state-ok"))

        assertTrue(result is OAuthCompletionResult.Success)
        assertEquals("auth-code", exchanger.exchangedCode)
        assertEquals(stored.codeVerifier, exchanger.exchangedVerifier)
        assertEquals("$CANONICAL_ORIGIN$TOKEN_PATH", exchanger.exchangedTokenEndpoint)
        assertEquals("access-1", tokenStore.written?.accessToken)
        // Consumed before exchange, cleaned up after tokens persisted.
        assertTrue(attemptStore.consumeOrder < exchanger.exchangeOrder)
        assertTrue(tokenStore.writeOrder < attemptStore.clearOrder)
    }

    @Test
    fun `failed token exchange reports typed failure and keeps attempt consumed`() {
        val attemptStore = RecordingAttemptStore(context)
        val tokenStore = RecordingTokenStore(context)
        attemptStore.stored = pendingAttempt("state-fail")

        val result = coordinator(
            attemptStore, tokenStore, exchanger = RecordingExchanger(tokens = null)
        ).handleCompletion(callbackIntent(code = "auth-code", state = "state-fail"))

        assertEquals(
            OAuthCompletionResult.Reason.TOKEN_EXCHANGE_FAILED,
            (result as OAuthCompletionResult.Failed).reason
        )
        assertNull(tokenStore.written)
        assertEquals("state-fail", attemptStore.consumedState)
    }

    @Test
    fun `consumed attempt with persisted tokens does not exchange again`() {
        val attemptStore = RecordingAttemptStore(context)
        val tokenStore = RecordingTokenStore(context)
        val exchanger = RecordingExchanger()
        attemptStore.stored = pendingAttempt("state-consumed")
            .copy(status = OAuthAttempt.Status.CONSUMED)
        tokenStore.stored = OAuthTokens(
            "already",
            "refresh",
            Long.MAX_VALUE,
            canonicalOrigin = CANONICAL_ORIGIN,
            clientId = CLIENT_ID
        )

        val result = coordinator(attemptStore, tokenStore, exchanger = exchanger)
            .handleCompletion(callbackIntent(code = "replay", state = "state-consumed"))

        assertTrue(result is OAuthCompletionResult.Success)
        assertEquals(0, exchanger.exchangeCount)
        assertNull(tokenStore.written)
        assertTrue(attemptStore.clearOrder > 0)
    }

    @Test
    fun `consumed attempt without tokens requires new authorization`() {
        val attemptStore = RecordingAttemptStore(context)
        val exchanger = RecordingExchanger()
        attemptStore.stored = pendingAttempt("state-consumed-2")
            .copy(status = OAuthAttempt.Status.CONSUMED)

        val result = coordinator(attemptStore, RecordingTokenStore(context), exchanger = exchanger)
            .handleCompletion(callbackIntent(code = "replay", state = "state-consumed-2"))

        assertEquals(
            OAuthCompletionResult.Reason.ATTEMPT_CONSUMED,
            (result as OAuthCompletionResult.Failed).reason
        )
        assertEquals(0, exchanger.exchangeCount)
    }

    @Test
    fun `two concurrent completions perform exactly one exchange`() {
        val attemptStore = RecordingAttemptStore(context)
        val tokenStore = RecordingTokenStore(context)
        val exchanger = BlockingRecordingExchanger()
        attemptStore.stored = pendingAttempt("race")
        val coordinator = coordinator(attemptStore, tokenStore, exchanger = exchanger)
        val results = java.util.Collections.synchronizedList(mutableListOf<OAuthCompletionResult>())

        val threads = List(2) {
            Thread { results += coordinator.handleCompletion(callbackIntent("code", "race")) }
        }
        threads.forEach(Thread::start)
        threads.forEach { it.join(5_000) }

        assertEquals(1, exchanger.exchangeCount.get())
        assertEquals(1, results.count { it is OAuthCompletionResult.Success })
        assertEquals(
            1,
            results.count {
                it is OAuthCompletionResult.Failed &&
                    it.reason == OAuthCompletionResult.Reason.ATTEMPT_CONSUMED
            }
        )
    }

    @Test
    fun `AppAuth terminal failure clears matching pending attempt without exchange`() {
        val attemptStore = RecordingAttemptStore(context).apply { stored = pendingAttempt("terminal-error") }
        val exchanger = RecordingExchanger()
        val intent = net.openid.appauth.AuthorizationException.AuthorizationRequestErrors.ACCESS_DENIED
            .toIntent()
            .apply {
                action = AuthCompletionActivity.ACTION_AUTH_COMPLETION
                putExtra(AuthCompletionActivity.EXTRA_STATE, "terminal-error")
            }

        val result = coordinator(attemptStore, RecordingTokenStore(context), exchanger = exchanger)
            .handleCompletion(intent)

        assertEquals(
            OAuthCompletionResult.Reason.AUTHORIZATION_FAILED,
            (result as OAuthCompletionResult.Failed).reason
        )
        assertNull(attemptStore.stored)
        assertEquals(0, exchanger.exchangeCount)
    }

    @Test
    fun `restart after matching terminal error stays unauthenticated`() {
        val attemptStore = RecordingAttemptStore(context).apply { stored = pendingAttempt("terminal-restart") }
        val tokenStore = RecordingTokenStore(context)
        val intent = net.openid.appauth.AuthorizationException.AuthorizationRequestErrors.ACCESS_DENIED
            .toIntent()
            .apply {
                action = AuthCompletionActivity.ACTION_AUTH_COMPLETION
                putExtra(AuthCompletionActivity.EXTRA_STATE, "terminal-restart")
            }
        val coordinator = coordinator(attemptStore, tokenStore)

        coordinator.handleCompletion(intent)

        assertNull(attemptStore.stored)
        assertFalse(AuthenticationOwner(coordinator).isAuthenticated)
        assertNull(tokenStore.stored)
    }

    @Test
    fun `AppAuth cancellation clears matching pending attempt without exchange`() {
        val attemptStore = RecordingAttemptStore(context).apply { stored = pendingAttempt("terminal-cancel") }
        val exchanger = RecordingExchanger()
        val intent = net.openid.appauth.AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW
            .toIntent()
            .apply {
                action = AuthCompletionActivity.ACTION_AUTH_COMPLETION
                putExtra(AuthCompletionActivity.EXTRA_STATE, "terminal-cancel")
            }

        val result = coordinator(attemptStore, RecordingTokenStore(context), exchanger = exchanger)
            .handleCompletion(intent)

        assertEquals(
            OAuthCompletionResult.Reason.AUTHORIZATION_CANCELLED,
            (result as OAuthCompletionResult.Failed).reason
        )
        assertNull(attemptStore.stored)
        assertEquals(0, exchanger.exchangeCount)
    }

    @Test
    fun `duplicate AppAuth cancellation is idempotent`() {
        val attemptStore = RecordingAttemptStore(context).apply { stored = pendingAttempt("cancel-twice") }
        val exchanger = RecordingExchanger()
        val intent = net.openid.appauth.AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW
            .toIntent()
            .apply {
                action = AuthCompletionActivity.ACTION_AUTH_COMPLETION
                putExtra(AuthCompletionActivity.EXTRA_STATE, "cancel-twice")
            }
        assertEquals(
            net.openid.appauth.AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW,
            net.openid.appauth.AuthorizationException.fromIntent(intent)
        )
        val coordinator = coordinator(attemptStore, RecordingTokenStore(context), exchanger = exchanger)

        val first = coordinator.handleCompletion(intent)
        val second = coordinator.handleCompletion(intent)

        assertEquals(
            OAuthCompletionResult.Reason.AUTHORIZATION_CANCELLED,
            (first as OAuthCompletionResult.Failed).reason
        )
        assertEquals(
            OAuthCompletionResult.Reason.ATTEMPT_CONSUMED,
            (second as OAuthCompletionResult.Failed).reason
        )
        assertNull(attemptStore.stored)
        assertEquals(0, exchanger.exchangeCount)
    }

    @Test
    fun `stale AppAuth cancellation preserves newer pending attempt`() {
        val attemptStore = RecordingAttemptStore(context).apply { stored = pendingAttempt("new-state") }
        val intent = net.openid.appauth.AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW
            .toIntent()
            .apply {
                action = AuthCompletionActivity.ACTION_AUTH_COMPLETION
                putExtra(AuthCompletionActivity.EXTRA_STATE, "old-state")
            }

        val result = coordinator(attemptStore, RecordingTokenStore(context)).handleCompletion(intent)

        assertEquals(
            OAuthCompletionResult.Reason.ATTEMPT_CONSUMED,
            (result as OAuthCompletionResult.Failed).reason
        )
        assertEquals("new-state", attemptStore.stored?.state)
    }

    @Test
    fun `stale cancellation does not clear newer attempt`() {
        val attemptStore = RecordingAttemptStore(context)
        attemptStore.stored = pendingAttempt("new-state")
        val result = coordinator(attemptStore, RecordingTokenStore(context)).handleCompletion(
            Intent(AuthCompletionActivity.ACTION_AUTH_CANCEL).apply {
                putExtra(AuthCompletionActivity.EXTRA_STATE, "old-state")
            }
        )

        assertEquals(OAuthCompletionResult.Reason.ATTEMPT_CONSUMED, (result as OAuthCompletionResult.Failed).reason)
        assertEquals("new-state", attemptStore.stored?.state)
    }

    @Test
    fun `duplicate matching cancellation is idempotent`() {
        val attemptStore = RecordingAttemptStore(context)
        attemptStore.stored = pendingAttempt("cancel-once")
        val intent = Intent(AuthCompletionActivity.ACTION_AUTH_CANCEL).apply {
            putExtra(AuthCompletionActivity.EXTRA_STATE, "cancel-once")
        }
        val coordinator = coordinator(attemptStore, RecordingTokenStore(context))

        val first = coordinator.handleCompletion(intent)
        val second = coordinator.handleCompletion(intent)

        assertEquals(
            OAuthCompletionResult.Reason.AUTHORIZATION_CANCELLED,
            (first as OAuthCompletionResult.Failed).reason
        )
        assertEquals(
            OAuthCompletionResult.Reason.ATTEMPT_CONSUMED,
            (second as OAuthCompletionResult.Failed).reason
        )
        assertNull(attemptStore.stored)
    }

    @Test
    fun `expired attempt is rejected and cleaned up without exchange`() {
        val attemptStore = RecordingAttemptStore(context)
        val exchanger = RecordingExchanger()
        attemptStore.stored = pendingAttempt("state-expired").copy(
            createdAt = System.currentTimeMillis() - 700_000,
            expiresAt = System.currentTimeMillis() - 100_000
        )

        val result = coordinator(attemptStore, RecordingTokenStore(context), exchanger = exchanger)
            .handleCompletion(callbackIntent(code = "code", state = "state-expired"))

        assertEquals(
            OAuthCompletionResult.Reason.ATTEMPT_EXPIRED,
            (result as OAuthCompletionResult.Failed).reason
        )
        assertEquals(0, exchanger.exchangeCount)
        assertTrue(attemptStore.clearOrder > 0)
    }

    @Test
    fun `mismatched state preserves pending attempt and expiry`() {
        val attemptStore = RecordingAttemptStore(context)
        val original = pendingAttempt("real-state")
        attemptStore.stored = original

        val result = coordinator(attemptStore, RecordingTokenStore(context))
            .handleCompletion(callbackIntent(code = "code", state = "forged"))

        assertEquals(
            OAuthCompletionResult.Reason.STATE_MISMATCH,
            (result as OAuthCompletionResult.Failed).reason
        )
        assertEquals(original, attemptStore.stored)
        assertEquals(0, attemptStore.clearOrder)
        assertNull(attemptStore.consumedState)
    }

    @Test
    fun `duplicate state parameter rejected`() {
        val attemptStore = RecordingAttemptStore(context)
        attemptStore.stored = pendingAttempt("dup")

        val intent = callbackIntent("abc", "dup").apply {
            data = Uri.parse("$REDIRECT_URI?code=abc&state=dup&state=other")
        }
        val result = coordinator(attemptStore, RecordingTokenStore(context)).handleCompletion(intent)

        assertEquals(
            OAuthCompletionResult.Reason.DUPLICATE_PARAM,
            (result as OAuthCompletionResult.Failed).reason
        )
    }

    @Test
    fun `duplicate code parameter rejected`() {
        val attemptStore = RecordingAttemptStore(context)
        attemptStore.stored = pendingAttempt("dupcode")

        val intent = callbackIntent("abc", "dupcode").apply {
            data = Uri.parse("$REDIRECT_URI?code=abc&code=def&state=dupcode")
        }
        val result = coordinator(attemptStore, RecordingTokenStore(context)).handleCompletion(intent)

        assertEquals(
            OAuthCompletionResult.Reason.DUPLICATE_PARAM,
            (result as OAuthCompletionResult.Failed).reason
        )
    }

    @Test
    fun `foreign host or userinfo callback rejected`() {
        val attemptStore = RecordingAttemptStore(context)
        attemptStore.stored = pendingAttempt("host")

        val foreignHost = callbackIntent("a", "host").apply {
            data = Uri.parse("https://evil.example.com/android/oauth2redirect?code=a&state=host")
        }
        assertEquals(
            OAuthCompletionResult.Reason.PATH_MISMATCH,
            (coordinator(attemptStore, RecordingTokenStore(context))
                .handleCompletion(foreignHost) as OAuthCompletionResult.Failed).reason
        )

        val userInfo = callbackIntent("a", "host").apply {
            data = Uri.parse(
                "https://user@oauth-staging.rotiropi.web.id" +
                    "/android/oauth2redirect?code=a&state=host"
            )
        }
        assertEquals(
            OAuthCompletionResult.Reason.PATH_MISMATCH,
            (coordinator(attemptStore, RecordingTokenStore(context))
                .handleCompletion(userInfo) as OAuthCompletionResult.Failed).reason
        )
    }

    @Test
    fun `cancelled authorization clears pending attempt`() {
        val attemptStore = RecordingAttemptStore(context)
        attemptStore.stored = pendingAttempt("cancelled")

        val cancelIntent = Intent().apply {
            action = AuthCompletionActivity.ACTION_AUTH_CANCEL
            putExtra(AuthCompletionActivity.EXTRA_STATE, "cancelled")
        }
        val result = coordinator(attemptStore, RecordingTokenStore(context))
            .handleCompletion(cancelIntent)

        assertTrue(result is OAuthCompletionResult.Failed)
        assertTrue(attemptStore.clearOrder > 0)
    }

    @Test
    fun `refresh uses refresh grant at fixed endpoint and persists new tokens`() {
        val tokenStore = RecordingTokenStore(context)
        tokenStore.stored = OAuthTokens(
            "old-access",
            "refresh-1",
            1L,
            canonicalOrigin = CANONICAL_ORIGIN,
            clientId = CLIENT_ID
        )
        val exchanger = RecordingExchanger(tokens = OAuthTokens("new-access", "refresh-2", Long.MAX_VALUE))

        val refreshed = coordinator(RecordingAttemptStore(context), tokenStore, exchanger = exchanger)
            .refresh()

        assertTrue(refreshed)
        assertEquals("refresh-1", exchanger.refreshedToken)
        assertEquals("$CANONICAL_ORIGIN$TOKEN_PATH", exchanger.refreshedTokenEndpoint)
        assertEquals("new-access", tokenStore.written?.accessToken)
    }

    @Test
    fun `refresh without replacement refresh token preserves existing token`() {
        val tokenStore = RecordingTokenStore(context)
        tokenStore.stored = OAuthTokens(
            "old-access",
            "refresh-keep",
            1L,
            canonicalOrigin = CANONICAL_ORIGIN,
            clientId = CLIENT_ID
        )
        val exchanger = RecordingExchanger(tokens = OAuthTokens("new-access", null, Long.MAX_VALUE))

        val refreshed = coordinator(RecordingAttemptStore(context), tokenStore, exchanger = exchanger)
            .refresh()

        assertTrue(refreshed)
        assertEquals("new-access", tokenStore.stored?.accessToken)
        assertEquals("refresh-keep", tokenStore.stored?.refreshToken)
    }

    @Test
    fun `concurrent expired token reads perform one refresh`() {
        val tokenStore = RecordingTokenStore(context)
        tokenStore.stored = OAuthTokens(
            "expired",
            "refresh-once",
            1L,
            canonicalOrigin = CANONICAL_ORIGIN,
            clientId = CLIENT_ID
        )
        val exchanger = BlockingRefreshExchanger()
        val coordinator = coordinator(RecordingAttemptStore(context), tokenStore, exchanger = exchanger)
        val tokens = java.util.Collections.synchronizedList(mutableListOf<String?>())

        val threads = List(2) { Thread { tokens += coordinator.getAccessToken() } }
        threads.forEach(Thread::start)
        threads.forEach { it.join(5_000) }

        assertEquals(1, exchanger.refreshCount.get())
        assertEquals(listOf("fresh", "fresh"), tokens.sortedBy { it })
    }

    @Test
    fun `failed refresh preserves existing valid record`() {
        val tokenStore = RecordingTokenStore(context)
        val existing = OAuthTokens(
            "still-valid",
            "refresh",
            System.currentTimeMillis() + 30_000,
            canonicalOrigin = CANONICAL_ORIGIN,
            clientId = CLIENT_ID
        )
        tokenStore.stored = existing

        val refreshed = coordinator(
            RecordingAttemptStore(context),
            tokenStore,
            exchanger = RecordingExchanger(tokens = null)
        ).refresh()

        assertFalse(refreshed)
        assertEquals(existing, tokenStore.stored)
    }

    @Test
    fun `refresh without refresh token fails and does not call exchanger`() {
        val tokenStore = RecordingTokenStore(context)
        tokenStore.stored = OAuthTokens(
            "old-access",
            null,
            1L,
            canonicalOrigin = CANONICAL_ORIGIN,
            clientId = CLIENT_ID
        )
        val exchanger = RecordingExchanger()

        val refreshed = coordinator(RecordingAttemptStore(context), tokenStore, exchanger = exchanger)
            .refresh()

        assertFalse(refreshed)
        assertEquals(0, exchanger.refreshCount)
    }

    @Test
    fun `logout between legacy token read and generation snapshot cannot restore token`() {
        val tokenStore = LogoutAfterReadTokenStore(context).apply {
            stored = OAuthTokens(
                "expired",
                "refresh",
                1L,
                canonicalOrigin = CANONICAL_ORIGIN,
                clientId = CLIENT_ID
            )
        }
        val exchanger = RecordingExchanger()
        lateinit var coordinator: OAuthCoordinator
        coordinator = coordinator(RecordingAttemptStore(context), tokenStore, exchanger = exchanger)
        tokenStore.afterRead = { coordinator.clear() }

        val refreshed = coordinator.refresh()

        assertFalse(refreshed)
        assertEquals(1, exchanger.refreshCount)
        assertNull(tokenStore.stored)
        assertFalse(AuthenticationOwner(coordinator).isAuthenticated)
    }

    @Test
    fun `logout during in-flight refresh prevents token persistence and restart authentication`() {
        val attemptStore = RecordingAttemptStore(context)
        val tokenStore = RecordingTokenStore(context).apply {
            stored = OAuthTokens(
                "expired",
                "refresh",
                1L,
                canonicalOrigin = CANONICAL_ORIGIN,
                clientId = CLIENT_ID
            )
        }
        val exchanger = LatchingRefreshExchanger()
        val coordinator = coordinator(attemptStore, tokenStore, exchanger = exchanger)
        val owner = AuthenticationOwner(coordinator)
        var result: String? = "not-finished"
        val refresh = Thread { result = coordinator.getAccessToken() }

        refresh.start()
        assertTrue(exchanger.started.await(5, java.util.concurrent.TimeUnit.SECONDS))
        owner.logout()
        exchanger.release.countDown()
        refresh.join(5_000)

        assertNull(result)
        assertNull(tokenStore.stored)
        assertFalse(owner.isAuthenticated)
        assertFalse(AuthenticationOwner(coordinator).isAuthenticated)
    }

    @Test
    fun `logout during in-flight callback rejects stale token result`() {
        val attemptStore = RecordingAttemptStore(context).apply {
            stored = pendingAttempt("logout-callback")
        }
        val tokenStore = RecordingTokenStore(context)
        val exchanger = LatchingExchangeExchanger()
        val owner = AuthenticationOwner(
            coordinator(attemptStore, tokenStore, exchanger = exchanger)
        )
        var result: OAuthCompletionResult? = null
        val completion = Thread {
            result = owner.handleCompletion(callbackIntent("code", "logout-callback"))
        }

        completion.start()
        assertTrue(exchanger.started.await(5, java.util.concurrent.TimeUnit.SECONDS))
        owner.logout()
        exchanger.release.countDown()
        completion.join(5_000)

        assertEquals(
            OAuthCompletionResult.Reason.ATTEMPT_CONSUMED,
            (result as OAuthCompletionResult.Failed).reason
        )
        assertNull(tokenStore.stored)
        assertNull(attemptStore.stored)
        assertEquals(AuthenticationState.Unauthenticated, owner.state.value)
    }

    @Test
    fun `logout during several observed 401 refresh decisions cannot restore credentials`() {
        val tokenStore = RecordingTokenStore(context).apply {
            stored = OAuthTokens(
                "expired",
                "refresh",
                Long.MAX_VALUE,
                canonicalOrigin = CANONICAL_ORIGIN,
                clientId = CLIENT_ID
            )
        }
        val exchanger = LatchingRefreshExchanger()
        val coordinator = coordinator(RecordingAttemptStore(context), tokenStore, exchanger = exchanger)
        val owner = AuthenticationOwner(coordinator)
        val results = java.util.Collections.synchronizedList(mutableListOf<String?>())
        val reads = List(4) {
            Thread { results += coordinator.refreshAccessTokenIfCurrent("expired") }
        }

        reads.forEach(Thread::start)
        assertTrue(exchanger.started.await(5, java.util.concurrent.TimeUnit.SECONDS))
        owner.logout()
        exchanger.release.countDown()
        reads.forEach { it.join(5_000) }

        assertEquals(listOf(null, null, null, null), results)
        assertNull(tokenStore.stored)
        assertFalse(owner.isAuthenticated)
        assertFalse(AuthenticationOwner(coordinator).isAuthenticated)
    }

    private class LogoutAfterReadTokenStore(context: Context) : RecordingTokenStore(context) {
        var afterRead: (() -> Unit)? = null
        private var triggered = false

        override fun read(canonicalOrigin: String, clientId: String): OAuthTokens? {
            val tokens = super.read(canonicalOrigin, clientId)
            if (!triggered) {
                triggered = true
                afterRead?.invoke()
            }
            return tokens
        }
    }

    private class LatchingRefreshExchanger : TokenExchanger {
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)

        override fun exchangeAuthorizationCode(
            config: OAuthConfiguration,
            code: String,
            codeVerifier: String
        ): OAuthTokens? = null

        override fun refreshAccessToken(
            config: OAuthConfiguration,
            refreshToken: String
        ): OAuthTokens {
            started.countDown()
            release.await(5, java.util.concurrent.TimeUnit.SECONDS)
            return OAuthTokens("stale-refresh", "refresh", Long.MAX_VALUE)
        }
    }

    private class LatchingExchangeExchanger : TokenExchanger {
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)

        override fun exchangeAuthorizationCode(
            config: OAuthConfiguration,
            code: String,
            codeVerifier: String
        ): OAuthTokens {
            started.countDown()
            release.await(5, java.util.concurrent.TimeUnit.SECONDS)
            return OAuthTokens("stale-exchange", "refresh", Long.MAX_VALUE)
        }

        override fun refreshAccessToken(
            config: OAuthConfiguration,
            refreshToken: String
        ): OAuthTokens? = null
    }

    private class BlockingRefreshExchanger : TokenExchanger {
        val refreshCount = java.util.concurrent.atomic.AtomicInteger()

        override fun exchangeAuthorizationCode(
            config: OAuthConfiguration,
            code: String,
            codeVerifier: String
        ): OAuthTokens? = null

        override fun refreshAccessToken(
            config: OAuthConfiguration,
            refreshToken: String
        ): OAuthTokens {
            refreshCount.incrementAndGet()
            Thread.sleep(50)
            return OAuthTokens("fresh", null, Long.MAX_VALUE)
        }
    }

    private class BlockingRecordingExchanger : TokenExchanger {
        val exchangeCount = java.util.concurrent.atomic.AtomicInteger()

        override fun exchangeAuthorizationCode(
            config: OAuthConfiguration,
            code: String,
            codeVerifier: String
        ): OAuthTokens {
            exchangeCount.incrementAndGet()
            Thread.sleep(50)
            return OAuthTokens("access", "refresh", Long.MAX_VALUE)
        }

        override fun refreshAccessToken(
            config: OAuthConfiguration,
            refreshToken: String
        ): OAuthTokens? = null
    }

    private fun callbackIntent(code: String, state: String): Intent {
        val verifier = callbackVerifier(state)
        val request = AuthorizationRequest.Builder(
            AuthorizationServiceConfiguration(
                Uri.parse("$CANONICAL_ORIGIN$AUTHORIZE_PATH"),
                Uri.parse("$CANONICAL_ORIGIN$TOKEN_PATH")
            ),
            CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI)
        )
            .setState(state)
            .setScope(SCOPE)
            .setCodeVerifier(verifier, expectedS256(verifier), "S256")
            .build()
        return AuthorizationResponse.Builder(request)
            .setState(state)
            .setAuthorizationCode(code)
            .build()
            .toIntent()
            .apply {
                action = AuthCompletionActivity.ACTION_AUTH_COMPLETION
                data = Uri.parse("$REDIRECT_URI?code=$code&state=$state")
                putExtra(AuthCompletionActivity.EXTRA_STATE, state)
            }
    }

    private fun pendingAttempt(state: String) = OAuthAttempt(
        canonicalOrigin = CANONICAL_ORIGIN,
        clientId = CLIENT_ID,
        state = state,
        codeVerifier = callbackVerifier(state),
        codeChallenge = expectedS256(callbackVerifier(state)),
        redirectUri = REDIRECT_URI,
        createdAt = System.currentTimeMillis(),
        expiresAt = System.currentTimeMillis() + 600_000,
        status = OAuthAttempt.Status.PENDING
    )

    private fun callbackVerifier(state: String): String =
        ("verifier-$state-abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ").take(64)

    private fun expectedS256(verifier: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return android.util.Base64.encodeToString(
            digest,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    }
}
