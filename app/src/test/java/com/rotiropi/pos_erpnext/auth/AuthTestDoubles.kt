package com.rotiropi.pos_erpnext.auth

import android.app.PendingIntent
import android.content.Context

/** Shared monotonic sequence so tests can assert ordering across doubles. */
internal object CallSequence {
    private var counter = 0
    fun next(): Int = ++counter
}

internal class RecordingAttemptStore(context: Context) : OAuthAttemptStore(context) {
    var stored: OAuthAttempt? = null
    var written: OAuthAttempt? = null
    var consumedState: String? = null
    var writeOrder: Int = 0
    var consumeOrder: Int = 0
    var clearOrder: Int = 0
    var onClear: (() -> Unit)? = null

    override fun read(): OAuthAttempt? = stored

    override fun write(attempt: OAuthAttempt) {
        written = attempt
        stored = attempt
        writeOrder = CallSequence.next()
    }

    override fun consume(state: String): OAuthAttempt? {
        val current = stored ?: return null
        if (current.state != state || current.status != OAuthAttempt.Status.PENDING) return null
        consumedState = state
        consumeOrder = CallSequence.next()
        return current.copy(status = OAuthAttempt.Status.CONSUMED).also { stored = it }
    }

    override fun clearIfState(state: String): Boolean {
        if (stored?.state != state) return false
        clear()
        return true
    }

    override fun clear() {
        clearOrder = CallSequence.next()
        stored = null
        onClear?.invoke()
    }
}

internal open class RecordingTokenStore(context: Context) : TokenStore(context) {
    var stored: OAuthTokens? = null
    var written: OAuthTokens? = null
    var writeOrder: Int = 0
    var clearOrder: Int = 0
    var onClear: (() -> Unit)? = null

    override fun readUnboundForTest(): OAuthTokens? = stored

    override fun read(canonicalOrigin: String, clientId: String): OAuthTokens? {
        val current = stored ?: return null
        if (current.canonicalOrigin != canonicalOrigin || current.clientId != clientId) {
            clear()
            return null
        }
        return current
    }

    override fun write(tokens: OAuthTokens) {
        written = tokens
        stored = tokens
        writeOrder = CallSequence.next()
    }

    override fun clear() {
        clearOrder = CallSequence.next()
        stored = null
        onClear?.invoke()
    }
}

internal class RecordingLauncher : AuthorizationLauncher {
    var state: String? = null
    var codeVerifier: String? = null
    var codeChallenge: String? = null
    var launchOrder: Int = 0

    override fun launchAuthorization(
        config: OAuthConfiguration,
        state: String,
        codeVerifier: String,
        codeChallenge: String
    ) {
        this.state = state
        this.codeVerifier = codeVerifier
        this.codeChallenge = codeChallenge
        launchOrder = CallSequence.next()
    }

    override fun completionPendingIntent(state: String): PendingIntent? = null

    override fun cancelPendingIntent(state: String): PendingIntent? = null
}

internal class RecordingExchanger(
    private val tokens: OAuthTokens? = OAuthTokens("access", "refresh", Long.MAX_VALUE)
) : TokenExchanger {
    var exchangedCode: String? = null
    var exchangedVerifier: String? = null
    var exchangedTokenEndpoint: String? = null
    var exchangeCount: Int = 0
    var exchangeOrder: Int = 0

    var refreshedToken: String? = null
    var refreshedTokenEndpoint: String? = null
    var refreshCount: Int = 0

    override fun exchangeAuthorizationCode(
        config: OAuthConfiguration,
        code: String,
        codeVerifier: String
    ): OAuthTokens? {
        exchangedCode = code
        exchangedVerifier = codeVerifier
        exchangedTokenEndpoint = config.tokenEndpoint
        exchangeCount++
        exchangeOrder = CallSequence.next()
        return tokens
    }

    override fun refreshAccessToken(
        config: OAuthConfiguration,
        refreshToken: String
    ): OAuthTokens? {
        refreshedToken = refreshToken
        refreshedTokenEndpoint = config.tokenEndpoint
        refreshCount++
        return tokens
    }
}
