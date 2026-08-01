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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class AuthenticationOwnerTest {

    private val context: Context get() = RuntimeEnvironment.application
    private val config = OAuthConfiguration.create(
        canonicalOrigin = ORIGIN,
        clientId = CLIENT_ID,
        redirectUri = REDIRECT_URI,
        authorizePath = OAuthConfiguration.AUTHORIZE_PATH,
        tokenPath = OAuthConfiguration.TOKEN_PATH,
        scope = OAuthConfiguration.SCOPE,
        lifetimeMinutes = OAuthConfiguration.ATTEMPT_LIFETIME_MINUTES
    )

    @Test
    fun `browser launch does not authenticate`() {
        val fixture = fixture()

        fixture.owner.beginAuthorization()

        assertEquals(AuthenticationState.Authorizing, fixture.owner.state.value)
        assertFalse(fixture.owner.isAuthenticated)
        assertNull(fixture.tokenStore.stored)
    }

    @Test
    fun `callback failure does not authenticate`() {
        val fixture = fixture()
        fixture.owner.beginAuthorization()

        fixture.owner.handleCompletion(Intent(AuthCompletionActivity.ACTION_AUTH_COMPLETION))

        assertTrue(fixture.owner.state.value is AuthenticationState.Error)
        assertFalse(fixture.owner.isAuthenticated)
    }

    @Test
    fun `token exchange failure does not authenticate`() {
        val fixture = fixture(exchangeTokens = null)
        fixture.owner.beginAuthorization()
        val state = requireNotNull(fixture.attemptStore.stored?.state)

        fixture.owner.handleCompletion(callback(state, "code-1"))

        assertTrue(fixture.owner.state.value is AuthenticationState.Error)
        assertFalse(fixture.owner.isAuthenticated)
        assertNull(fixture.tokenStore.stored)
    }

    @Test
    fun `only successful callback exchange and persisted valid token authenticates`() {
        val fixture = fixture()
        fixture.owner.beginAuthorization()
        val state = requireNotNull(fixture.attemptStore.stored?.state)

        fixture.owner.handleCompletion(callback(state, "code-1"))

        assertEquals(AuthenticationState.Authenticated, fixture.owner.state.value)
        assertTrue(fixture.owner.isAuthenticated)
        assertEquals("access", fixture.tokenStore.stored?.accessToken)
    }

    @Test
    fun `logout clears credentials and attempt before routing to sign in`() {
        val fixture = fixture()
        fixture.attemptStore.stored = pending("pending")
        fixture.tokenStore.stored = OAuthTokens(
            "access",
            "refresh",
            Long.MAX_VALUE,
            canonicalOrigin = ORIGIN,
            clientId = CLIENT_ID
        )
        fixture.owner.restoreAuthenticationState()
        assertTrue(fixture.owner.isAuthenticated)

        var stateDuringAttemptClear: AuthenticationState? = null
        var stateDuringTokenClear: AuthenticationState? = null
        fixture.attemptStore.onClear = { stateDuringAttemptClear = fixture.owner.state.value }
        fixture.tokenStore.onClear = { stateDuringTokenClear = fixture.owner.state.value }

        fixture.owner.logout()

        assertEquals(AuthenticationState.Authenticated, stateDuringAttemptClear)
        assertEquals(AuthenticationState.Authenticated, stateDuringTokenClear)
        assertTrue(fixture.tokenStore.clearOrder > 0)
        assertTrue(fixture.attemptStore.clearOrder > 0)
        assertEquals(AuthenticationState.Unauthenticated, fixture.owner.state.value)
        assertNull(fixture.tokenStore.stored)
        assertNull(fixture.attemptStore.stored)
    }

    @Test
    fun `logout during pending attempt blocks stale callback authentication`() {
        val fixture = fixture()
        fixture.owner.beginAuthorization()
        val state = requireNotNull(fixture.attemptStore.stored?.state)
        val callback = callback(state, "late-code")

        fixture.owner.logout()
        fixture.owner.handleCompletion(callback)

        assertFalse(fixture.owner.isAuthenticated)
        assertEquals(0, fixture.exchanger.exchangeCount)
    }

    @Test
    fun `restart with valid token clears consumed attempt without exchange`() {
        val fixture = fixture()
        fixture.attemptStore.stored = pending("consumed").copy(status = OAuthAttempt.Status.CONSUMED)
        fixture.tokenStore.stored = OAuthTokens(
            "persisted",
            "refresh",
            Long.MAX_VALUE,
            canonicalOrigin = ORIGIN,
            clientId = CLIENT_ID
        )

        fixture.owner.restoreAuthenticationState()

        assertTrue(fixture.owner.isAuthenticated)
        assertNull(fixture.attemptStore.stored)
        assertEquals(0, fixture.exchanger.exchangeCount)
    }

    @Test
    fun `restart with missing expired or mismatched token clears consumed attempt without exchange`() {
        listOf<OAuthTokens?>(
            null,
            OAuthTokens("expired", "refresh", 1L, ORIGIN, CLIENT_ID),
            OAuthTokens("mismatch", "refresh", Long.MAX_VALUE, "https://evil.example.com", CLIENT_ID)
        ).forEach { tokens ->
            val fixture = fixture()
            fixture.attemptStore.stored = pending("consumed").copy(status = OAuthAttempt.Status.CONSUMED)
            fixture.tokenStore.stored = tokens

            fixture.owner.restoreAuthenticationState()

            assertFalse(fixture.owner.isAuthenticated)
            assertNull(fixture.attemptStore.stored)
            assertEquals(0, fixture.exchanger.exchangeCount)
        }
    }

    private fun fixture(exchangeTokens: OAuthTokens? = OAuthTokens("access", "refresh", Long.MAX_VALUE)): Fixture {
        val attemptStore = RecordingAttemptStore(context)
        val tokenStore = RecordingTokenStore(context)
        val exchanger = RecordingExchanger(exchangeTokens)
        val coordinator = OAuthCoordinator(
            context,
            config,
            attemptStore,
            tokenStore,
            RecordingLauncher(),
            exchanger
        )
        return Fixture(
            AuthenticationOwner(coordinator),
            attemptStore,
            tokenStore,
            exchanger
        )
    }

    private fun callback(state: String, code: String): Intent {
        val verifier = "verifier-$state"
        val request = AuthorizationRequest.Builder(
            AuthorizationServiceConfiguration(
                Uri.parse(config.authorizationEndpoint),
                Uri.parse(config.tokenEndpoint)
            ),
            CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI)
        )
            .setState(state)
            .setScope(OAuthConfiguration.SCOPE)
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

    private fun pending(state: String) = OAuthAttempt(
        canonicalOrigin = ORIGIN,
        clientId = CLIENT_ID,
        state = state,
        codeVerifier = "verifier-$state",
        codeChallenge = expectedS256("verifier-$state"),
        redirectUri = REDIRECT_URI,
        createdAt = System.currentTimeMillis(),
        expiresAt = System.currentTimeMillis() + 600_000,
        status = OAuthAttempt.Status.PENDING
    )

    private fun expectedS256(verifier: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return android.util.Base64.encodeToString(
            digest,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    }

    private data class Fixture(
        val owner: AuthenticationOwner,
        val attemptStore: RecordingAttemptStore,
        val tokenStore: RecordingTokenStore,
        val exchanger: RecordingExchanger
    )

    private companion object {
        const val ORIGIN = "https://example.com"
        const val CLIENT_ID = "test-client"
        const val REDIRECT_URI = "$ORIGIN/android/oauth2redirect"
    }
}
