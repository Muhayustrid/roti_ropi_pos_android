package com.rotiropi.pos_erpnext.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

interface AuthorizationLauncher {
    fun launchAuthorization(
        config: OAuthConfiguration,
        state: String,
        codeVerifier: String,
        codeChallenge: String
    )

    fun completionPendingIntent(state: String): PendingIntent?
    fun cancelPendingIntent(state: String): PendingIntent?
}

class AppAuthAuthorizationLauncher(context: Context) : AuthorizationLauncher {
    private val appContext = context.applicationContext
    private val authorizationService = AuthorizationService(appContext)

    override fun launchAuthorization(
        config: OAuthConfiguration,
        state: String,
        codeVerifier: String,
        codeChallenge: String
    ) {
        val request = AuthorizationRequest.Builder(
            AuthorizationServiceConfiguration(
                Uri.parse(config.authorizationEndpoint),
                Uri.parse(config.tokenEndpoint)
            ),
            config.clientId,
            ResponseTypeValues.CODE,
            Uri.parse(config.redirectUri)
        )
            .setScope(config.scope)
            .setState(state)
            .setCodeVerifier(codeVerifier, codeChallenge, AuthorizationRequest.CODE_CHALLENGE_METHOD_S256)
            .build()

        authorizationService.performAuthorizationRequest(
            request,
            completionPendingIntent(state),
            cancelPendingIntent(state)
        )
    }

    override fun completionPendingIntent(state: String): PendingIntent = pendingIntent(
        state,
        AuthCompletionActivity.ACTION_AUTH_COMPLETION,
        REQUEST_CODE_COMPLETE_SALT
    )

    override fun cancelPendingIntent(state: String): PendingIntent = pendingIntent(
        state,
        AuthCompletionActivity.ACTION_AUTH_CANCEL,
        REQUEST_CODE_CANCEL_SALT
    )

    private fun pendingIntent(state: String, action: String, salt: Int): PendingIntent {
        val intent = Intent(appContext, AuthCompletionActivity::class.java).apply {
            this.action = "$action.$state"
            putExtra(AuthCompletionActivity.EXTRA_STATE, state)
        }
        return PendingIntent.getActivity(
            appContext,
            salt,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT
        )
    }

    fun dispose() {
        authorizationService.dispose()
    }

    private companion object {
        const val REQUEST_CODE_COMPLETE_SALT = 0x43A1
        const val REQUEST_CODE_CANCEL_SALT = 0x51C2
    }
}
