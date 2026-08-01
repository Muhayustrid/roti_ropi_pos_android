package com.rotiropi.pos_erpnext.auth

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class OAuthConfigurationTest {
    @Test
    fun `fixed canonical routes scope redirect and lifetime accepted`() {
        val config = create()

        assertEquals("$ORIGIN${OAuthConfiguration.AUTHORIZE_PATH}", config.authorizationEndpoint)
        assertEquals("$ORIGIN${OAuthConfiguration.TOKEN_PATH}", config.tokenEndpoint)
        assertEquals(OAuthConfiguration.SCOPE, config.scope)
        assertEquals(600L, config.attemptLifetimeSeconds)
    }

    @Test
    fun `arbitrary cross origin discovery and dynamic endpoints rejected`() {
        val invalid = listOf<() -> Unit>(
            { create(origin = "http://example.com") },
            { create(origin = "$ORIGIN/path") },
            { create(authorizePath = "/.well-known/openid-configuration") },
            { create(authorizePath = "https://evil.example.com/authorize") },
            { create(tokenPath = "/api/method/other") },
            { create(tokenPath = "https://evil.example.com/token") }
        )

        invalid.forEach { candidate ->
            assertThrows(IllegalArgumentException::class.java) { candidate() }
        }
    }

    @Test
    fun `token connection builder disables redirects`() {
        val connection = AppAuthTokenExchanger.NoRedirectConnectionBuilder.openConnection(
            Uri.parse("https://example.com${OAuthConfiguration.TOKEN_PATH}")
        )

        assertFalse(connection.instanceFollowRedirects)
        connection.disconnect()
    }

    @Test
    fun `wrong scope redirect and lifetime rejected`() {
        val invalid = listOf<() -> Unit>(
            { create(scope = "openid") },
            { create(redirect = "https://evil.example.com/android/oauth2redirect") },
            { create(redirect = "$ORIGIN/android/oauth2redirect/extra") },
            { create(lifetimeMinutes = 5L) }
        )

        invalid.forEach { candidate ->
            assertThrows(IllegalArgumentException::class.java) { candidate() }
        }
    }

    private fun create(
        origin: String = ORIGIN,
        authorizePath: String = OAuthConfiguration.AUTHORIZE_PATH,
        tokenPath: String = OAuthConfiguration.TOKEN_PATH,
        scope: String = OAuthConfiguration.SCOPE,
        redirect: String = "$origin${OAuthConfiguration.REDIRECT_PATH}",
        lifetimeMinutes: Long = OAuthConfiguration.ATTEMPT_LIFETIME_MINUTES
    ) = OAuthConfiguration.create(
        origin,
        "client-id",
        redirect,
        authorizePath,
        tokenPath,
        scope,
        lifetimeMinutes
    )

    private companion object {
        const val ORIGIN = "https://example.com"
    }
}
