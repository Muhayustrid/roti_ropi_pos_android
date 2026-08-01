package com.rotiropi.pos_erpnext.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests OAuth redirect security: exact path validation, forgery prevention,
 * duplicate parameter rejection, and unsolicited-callback handling.
 */
@RunWith(AndroidJUnit4::class)
class AuthRedirectSecurityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val canonicalOrigin = "https://example.com"
    private val clientId = "test-client"
    private val redirectUri = "https://example.com/android/oauth2redirect"

    private fun coordinator(attemptStore: OAuthAttemptStore): OAuthCoordinator {
        val config = OAuthConfiguration.create(
            canonicalOrigin, clientId, redirectUri,
            "/api/method/frappe.integrations.oauth2.authorize",
            "/api/method/frappe.integrations.oauth2.get_token",
            "all", 10L
        )
        return OAuthCoordinator(context, config, attemptStore, TokenStore(context))
    }

    @Test
    fun duplicateStateParameterRejected() {
        val store = OAuthAttemptStore(context)
        store.clear()
        val uri = Uri.parse("$redirectUri?code=abc&state=x&state=y")
        val result = coordinator(store).handleCompletion(callbackIntent(uri))

        assertTrue(result is OAuthCompletionResult.Failed)
        assertEquals(
            OAuthCompletionResult.Reason.DUPLICATE_PARAM,
            (result as OAuthCompletionResult.Failed).reason
        )
    }

    @Test
    fun wrongPathRejected() {
        val store = OAuthAttemptStore(context)
        store.clear()
        val uri = Uri.parse("$canonicalOrigin/wrong/path?code=abc&state=x")
        val result = coordinator(store).handleCompletion(callbackIntent(uri))

        assertTrue(result is OAuthCompletionResult.Failed)
        assertEquals(
            OAuthCompletionResult.Reason.PATH_MISMATCH,
            (result as OAuthCompletionResult.Failed).reason
        )
    }

    @Test
    fun missingStateRejected() {
        val store = OAuthAttemptStore(context)
        store.clear()
        val uri = Uri.parse("$redirectUri?code=abc")
        val result = coordinator(store).handleCompletion(callbackIntent(uri))

        assertTrue(result is OAuthCompletionResult.Failed)
        assertEquals(
            OAuthCompletionResult.Reason.STATE_MISSING,
            (result as OAuthCompletionResult.Failed).reason
        )
    }

    @Test
    fun missingCodeRejected() {
        val store = OAuthAttemptStore(context)
        store.clear()
        val uri = Uri.parse("$redirectUri?state=abc")
        val result = coordinator(store).handleCompletion(callbackIntent(uri))

        assertTrue(result is OAuthCompletionResult.Failed)
        assertEquals(
            OAuthCompletionResult.Reason.CODE_MISSING,
            (result as OAuthCompletionResult.Failed).reason
        )
    }

    @Test
    fun nullIntentDataRejected() {
        val store = OAuthAttemptStore(context)
        store.clear()
        val result = coordinator(store).handleCompletion(Intent())

        assertTrue(result is OAuthCompletionResult.Failed)
        assertEquals(
            OAuthCompletionResult.Reason.INTENT_DATA_NULL,
            (result as OAuthCompletionResult.Failed).reason
        )
    }

    @Test
    fun mismatchedCallbackPreservesPendingAttempt() {
        val store = OAuthAttemptStore(context)
        store.clear()
        val original = OAuthAttempt(
            canonicalOrigin = canonicalOrigin,
            clientId = clientId,
            state = "real-state",
            codeVerifier = "verifier",
            codeChallenge = "challenge",
            redirectUri = redirectUri,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 600_000,
            status = OAuthAttempt.Status.PENDING
        )
        store.write(original)

        val forged = Uri.parse("$redirectUri?code=abc&state=forged-state")
        val result = coordinator(store).handleCompletion(callbackIntent(forged))

        assertTrue(result is OAuthCompletionResult.Failed)
        assertEquals(
            OAuthCompletionResult.Reason.STATE_MISMATCH,
            (result as OAuthCompletionResult.Failed).reason
        )

        val preserved = store.read()
        assertEquals("real-state", preserved?.state)
        assertEquals(OAuthAttempt.Status.PENDING, preserved?.status)
        assertEquals(original.expiresAt, preserved?.expiresAt)
    }

    private fun callbackIntent(uri: Uri): Intent {
        val state = uri.getQueryParameter("state")
        val code = uri.getQueryParameter("code")
        val verifier = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
        val request = AuthorizationRequest.Builder(
            AuthorizationServiceConfiguration(
                Uri.parse("$canonicalOrigin${OAuthConfiguration.AUTHORIZE_PATH}"),
                Uri.parse("$canonicalOrigin${OAuthConfiguration.TOKEN_PATH}")
            ),
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(redirectUri)
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
                data = uri
                putExtra(AuthCompletionActivity.EXTRA_STATE, state)
            }
    }

    private fun expectedS256(verifier: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return android.util.Base64.encodeToString(
            digest,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    }
}
