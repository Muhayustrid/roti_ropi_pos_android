package com.rotiropi.pos_erpnext.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.rotiropi.pos_erpnext.MobilePosApplication
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MobilePosApplication::class)
class AuthCallbackDeliveryTest {
    private lateinit var context: Context
    private lateinit var app: MobilePosApplication
    private lateinit var attemptStore: RecordingAttemptStore
    private lateinit var exchanger: RecordingExchanger
    private lateinit var owner: AuthenticationOwner
    private val config = OAuthConfiguration.create(
        ORIGIN,
        CLIENT_ID,
        REDIRECT_URI,
        OAuthConfiguration.AUTHORIZE_PATH,
        OAuthConfiguration.TOKEN_PATH,
        OAuthConfiguration.SCOPE,
        OAuthConfiguration.ATTEMPT_LIFETIME_MINUTES
    )

    @Before
    fun setUp() {
        context = RuntimeEnvironment.application
        app = context as MobilePosApplication
        attemptStore = RecordingAttemptStore(context)
        exchanger = RecordingExchanger(tokens = null)
        owner = AuthenticationOwner(
            OAuthCoordinator(
                context,
                config,
                attemptStore,
                RecordingTokenStore(context),
                RecordingLauncher(),
                exchanger
            )
        )
        MobilePosApplication::class.java.getDeclaredField("authenticationOwner")
            .apply { isAccessible = true }
            .set(app, owner)
    }

    @Test
    fun `completion activity finishes while exchange runs off main`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exchangeThread = arrayOfNulls<Thread>(1)
        val asyncExchanger = object : TokenExchanger {
            override fun exchangeAuthorizationCode(
                config: OAuthConfiguration,
                code: String,
                codeVerifier: String
            ): OAuthTokens? {
                exchangeThread[0] = Thread.currentThread()
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
                return OAuthTokens("access", "refresh", Long.MAX_VALUE)
            }

            override fun refreshAccessToken(config: OAuthConfiguration, refreshToken: String): OAuthTokens? = null
        }
        val tokens = RecordingTokenStore(context)
        owner = AuthenticationOwner(
            OAuthCoordinator(context, config, attemptStore, tokens, RecordingLauncher(), asyncExchanger)
        )
        MobilePosApplication::class.java.getDeclaredField("authenticationOwner")
            .apply { isAccessible = true }
            .set(app, owner)
        owner.beginAuthorization()
        val state = requireNotNull(attemptStore.stored?.state)

        val activity = Robolectric.buildActivity(AuthCompletionActivity::class.java, successIntent(state, "cold-code"))
            .setup()
            .get()

        assertTrue(activity.isFinishing)
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertTrue(exchangeThread[0] !== Looper.getMainLooper().thread)
        assertTrue(tokens.stored == null)
        release.countDown()
        waitFor { owner.state.value == AuthenticationState.Authenticated }
        assertEquals("access", tokens.stored?.accessToken)
    }

    @Test
    fun `AppAuth AuthorizationException reaches completion parser`() {
        owner.beginAuthorization()
        val state = requireNotNull(attemptStore.stored?.state)
        val intent = AuthorizationException.AuthorizationRequestErrors.ACCESS_DENIED.toIntent().apply {
            action = AuthCompletionActivity.ACTION_AUTH_COMPLETION
            putExtra(AuthCompletionActivity.EXTRA_STATE, state)
        }

        Robolectric.buildActivity(AuthCompletionActivity::class.java, intent).setup().get()

        waitFor { owner.state.value is AuthenticationState.Error }
        val error = owner.state.value as AuthenticationState.Error
        assertEquals(OAuthCompletionResult.Reason.AUTHORIZATION_FAILED, error.reason)
        assertNull(attemptStore.stored)
        assertEquals(0, exchanger.exchangeCount)
    }

    @Test
    fun `warm callback delivery uses same validation path`() {
        owner.beginAuthorization()
        val state = requireNotNull(attemptStore.stored?.state)
        val controller = Robolectric.buildActivity(
            AuthCompletionActivity::class.java,
            Intent(AuthCompletionActivity.ACTION_AUTH_COMPLETION)
        ).create().start().resume()

        AuthCompletionActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
            .apply { isAccessible = true }
            .invoke(controller.get(), successIntent(state, "warm-code"))

        waitFor { exchanger.exchangeCount == 1 }
        waitFor { owner.state.value is AuthenticationState.Error }
    }

    @Test
    fun `forged explicit Intent without AppAuth extras is rejected`() {
        owner.beginAuthorization()
        val state = requireNotNull(attemptStore.stored?.state)
        val forged = Intent(context, AuthCompletionActivity::class.java).apply {
            action = AuthCompletionActivity.ACTION_AUTH_COMPLETION
            data = Uri.parse("$REDIRECT_URI?code=forged&state=$state")
            putExtra(AuthCompletionActivity.EXTRA_STATE, state)
        }

        Robolectric.buildActivity(AuthCompletionActivity::class.java, forged).setup().get()

        waitFor { owner.state.value is AuthenticationState.Error }
        val error = owner.state.value as AuthenticationState.Error
        assertEquals(OAuthCompletionResult.Reason.MALFORMED_CALLBACK, error.reason)
        assertEquals(state, attemptStore.stored?.state)
        assertEquals(0, exchanger.exchangeCount)
    }

    @Test
    fun `PendingIntent identities differ between attempts and remain explicit mutable one shot`() {
        val launcher = AppAuthAuthorizationLauncher(context)
        val first = launcher.completionPendingIntent("state-1")
        val second = launcher.completionPendingIntent("state-2")

        assertNotEquals(first, second)
        assertTrue(first.isActivity)
        assertTrue(second.isActivity)
        assertFalse(first.isImmutable)
        assertFalse(second.isImmutable)
        assertTrue(Shadows.shadowOf(first).flags and android.app.PendingIntent.FLAG_ONE_SHOT != 0)
        assertTrue(Shadows.shadowOf(second).flags and android.app.PendingIntent.FLAG_ONE_SHOT != 0)
        assertEquals(
            AuthCompletionActivity::class.java.name,
            Shadows.shadowOf(first).savedIntent.component?.className
        )
        launcher.dispose()
    }

    private fun waitFor(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!predicate() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(predicate())
    }

    private fun successIntent(state: String, code: String): Intent {
        val verifier = callbackVerifier(state)
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
            .setScope(config.scope)
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

    private companion object {
        const val ORIGIN = "https://example.com"
        const val CLIENT_ID = "test-client"
        const val REDIRECT_URI = "$ORIGIN/android/oauth2redirect"
    }
}
