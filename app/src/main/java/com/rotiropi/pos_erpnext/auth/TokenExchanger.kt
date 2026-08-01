package com.rotiropi.pos_erpnext.auth

import android.content.Context
import android.net.Uri
import java.net.HttpURLConnection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.NoClientAuthentication
import net.openid.appauth.TokenRequest
import net.openid.appauth.connectivity.ConnectionBuilder

interface TokenExchanger {
    fun exchangeAuthorizationCode(
        config: OAuthConfiguration,
        code: String,
        codeVerifier: String
    ): OAuthTokens?

    fun refreshAccessToken(
        config: OAuthConfiguration,
        refreshToken: String
    ): OAuthTokens?
}

/** AppAuth exchanger restricted to fixed endpoint and no-redirect HTTPS transport. */
class AppAuthTokenExchanger(
    context: Context,
    private val timeoutSeconds: Long = 30L
) : TokenExchanger {
    private val authorizationService = AuthorizationService(
        context.applicationContext,
        AppAuthConfiguration.Builder()
            .setConnectionBuilder(NoRedirectConnectionBuilder)
            .build()
    )

    override fun exchangeAuthorizationCode(
        config: OAuthConfiguration,
        code: String,
        codeVerifier: String
    ): OAuthTokens? {
        val request = TokenRequest.Builder(serviceConfig(config), config.clientId)
            .setGrantType(net.openid.appauth.GrantTypeValues.AUTHORIZATION_CODE)
            .setAuthorizationCode(code)
            .setCodeVerifier(codeVerifier)
            .setRedirectUri(Uri.parse(config.redirectUri))
            .build()
        return perform(request)
    }

    override fun refreshAccessToken(
        config: OAuthConfiguration,
        refreshToken: String
    ): OAuthTokens? {
        val request = TokenRequest.Builder(serviceConfig(config), config.clientId)
            .setGrantType(net.openid.appauth.GrantTypeValues.REFRESH_TOKEN)
            .setRefreshToken(refreshToken)
            .build()
        return perform(request)
    }

    private fun serviceConfig(config: OAuthConfiguration): AuthorizationServiceConfiguration {
        require(config.authorizationEndpoint == "${config.canonicalOrigin}${OAuthConfiguration.AUTHORIZE_PATH}")
        require(config.tokenEndpoint == "${config.canonicalOrigin}${OAuthConfiguration.TOKEN_PATH}")
        return AuthorizationServiceConfiguration(
            Uri.parse(config.authorizationEndpoint),
            Uri.parse(config.tokenEndpoint)
        )
    }

    private fun perform(request: TokenRequest): OAuthTokens? {
        val latch = CountDownLatch(1)
        var tokens: OAuthTokens? = null
        val auth: ClientAuthentication = NoClientAuthentication.INSTANCE
        authorizationService.performTokenRequest(request, auth) { response, _ ->
            val accessToken = response?.accessToken
            if (accessToken != null) {
                tokens = OAuthTokens(
                    accessToken = accessToken,
                    refreshToken = response.refreshToken,
                    expiresAt = response.accessTokenExpirationTime
                        ?: (System.currentTimeMillis() + DEFAULT_EXPIRY_SECONDS * 1000)
                )
            }
            latch.countDown()
        }
        return if (latch.await(timeoutSeconds, TimeUnit.SECONDS)) tokens else null
    }

    fun dispose() {
        authorizationService.dispose()
    }

    internal object NoRedirectConnectionBuilder : ConnectionBuilder {
        override fun openConnection(uri: Uri): HttpURLConnection {
            require(uri.scheme == "https") { "OAuth transport requires HTTPS" }
            return (java.net.URL(uri.toString()).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 10_000
                readTimeout = 15_000
            }
        }
    }

    private companion object {
        const val DEFAULT_EXPIRY_SECONDS = 3600L
    }
}
