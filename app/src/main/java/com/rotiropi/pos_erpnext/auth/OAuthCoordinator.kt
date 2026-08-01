package com.rotiropi.pos_erpnext.auth

import android.content.Context
import android.content.Intent
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

sealed class OAuthCompletionResult {
    object Success : OAuthCompletionResult()
    data class Failed(val reason: Reason) : OAuthCompletionResult()

    enum class Reason {
        INTENT_DATA_NULL,
        STATE_MISSING,
        CODE_MISSING,
        NO_PERSISTED_ATTEMPT,
        STATE_MISMATCH,
        ATTEMPT_EXPIRED,
        ATTEMPT_CONSUMED,
        DUPLICATE_PARAM,
        PATH_MISMATCH,
        AUTHORIZATION_CANCELLED,
        AUTHORIZATION_FAILED,
        MALFORMED_CALLBACK,
        TOKEN_EXCHANGE_FAILED,
        TOKEN_PERSISTENCE_FAILED,
        AUTHORIZATION_LAUNCH_FAILED
    }
}

/** Application-scoped OAuth PKCE coordinator. */
class OAuthCoordinator(
    private val context: Context,
    private val config: OAuthConfiguration,
    private val attemptStore: OAuthAttemptStore,
    private val tokenStore: TokenStore,
    private val authorizationLauncher: AuthorizationLauncher =
        AppAuthAuthorizationLauncher(context),
    private val tokenExchanger: TokenExchanger = AppAuthTokenExchanger(context)
) {
    private val nowMs: Long get() = System.currentTimeMillis()
    private val authenticationLock = Any()
    private val refreshLock = Any()
    private var generation = 0L
    private var nextRefreshOperation = 0L
    private var activeRefreshOperation: Long? = null

    fun beginAuthorization() = synchronized(authenticationLock) {
        generation++
        activeRefreshOperation = null
        val state = UUID.randomUUID().toString()
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val createdAt = nowMs
        attemptStore.write(
            OAuthAttempt(
                canonicalOrigin = config.canonicalOrigin,
                clientId = config.clientId,
                state = state,
                codeVerifier = codeVerifier,
                codeChallenge = codeChallenge,
                redirectUri = config.redirectUri,
                createdAt = createdAt,
                expiresAt = createdAt + config.attemptLifetimeSeconds * 1000,
                status = OAuthAttempt.Status.PENDING
            )
        )
        authorizationLauncher.launchAuthorization(
            config,
            state,
            codeVerifier,
            codeChallenge
        )
    }

    fun handleCompletion(intent: Intent): OAuthCompletionResult {
        val operationGeneration = synchronized(authenticationLock) { generation }
        val callbackState = intent.getStringExtra(AuthCompletionActivity.EXTRA_STATE)
        if (intent.action == AuthCompletionActivity.ACTION_AUTH_CANCEL) {
            return fail(
                if (clearTerminalAttempt(callbackState, operationGeneration)) {
                    OAuthCompletionResult.Reason.AUTHORIZATION_CANCELLED
                } else {
                    OAuthCompletionResult.Reason.ATTEMPT_CONSUMED
                }
            )
        }
        if (intent.action != AuthCompletionActivity.ACTION_AUTH_COMPLETION) {
            return fail(
                if (intent.data == null) OAuthCompletionResult.Reason.INTENT_DATA_NULL
                else OAuthCompletionResult.Reason.MALFORMED_CALLBACK
            )
        }

        val exception = try {
            AuthorizationException.fromIntent(intent)
        } catch (_: RuntimeException) {
            return fail(OAuthCompletionResult.Reason.MALFORMED_CALLBACK)
        }
        if (exception != null) {
            val terminalState = intent.getStringExtra(AuthCompletionActivity.EXTRA_STATE)
            if (!clearTerminalAttempt(terminalState, operationGeneration)) {
                return fail(OAuthCompletionResult.Reason.ATTEMPT_CONSUMED)
            }
            return fail(
                if (exception == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW) {
                    OAuthCompletionResult.Reason.AUTHORIZATION_CANCELLED
                } else {
                    OAuthCompletionResult.Reason.AUTHORIZATION_FAILED
                }
            )
        }

        val response = try {
            AuthorizationResponse.fromIntent(intent)
        } catch (_: RuntimeException) {
            return fail(OAuthCompletionResult.Reason.MALFORMED_CALLBACK)
        } ?: return fail(
            if (intent.data == null) OAuthCompletionResult.Reason.INTENT_DATA_NULL
            else OAuthCompletionResult.Reason.MALFORMED_CALLBACK
        )

        val data = intent.data ?: return fail(OAuthCompletionResult.Reason.INTENT_DATA_NULL)
        if (!config.matchesRedirect(data)) return fail(OAuthCompletionResult.Reason.PATH_MISMATCH)
        if (data.getQueryParameters("state").size > 1 || data.getQueryParameters("code").size > 1) {
            return fail(OAuthCompletionResult.Reason.DUPLICATE_PARAM)
        }

        val returnedState = response.state ?: return fail(OAuthCompletionResult.Reason.STATE_MISSING)
        val code = response.authorizationCode ?: return fail(OAuthCompletionResult.Reason.CODE_MISSING)
        if (callbackState != returnedState || data.getQueryParameter("state") != returnedState ||
            data.getQueryParameter("code") != code
        ) {
            return fail(OAuthCompletionResult.Reason.STATE_MISMATCH)
        }
        if (!response.matches(config, returnedState)) {
            return fail(OAuthCompletionResult.Reason.STATE_MISMATCH)
        }

        val consumed = when (val outcome = consumeCallbackAttempt(returnedState, operationGeneration)) {
            is CallbackAttempt.Pending -> outcome.attempt
            CallbackAttempt.AlreadyConsumed -> return completeConsumedAttempt(operationGeneration)
            is CallbackAttempt.Rejected -> return fail(outcome.reason)
        }
        val exchanged = tokenExchanger.exchangeAuthorizationCode(config, code, consumed.codeVerifier)
            ?: return fail(OAuthCompletionResult.Reason.TOKEN_EXCHANGE_FAILED)
        val boundTokens = exchanged.bind(config)

        return synchronized(authenticationLock) {
            if (generation != operationGeneration) {
                return@synchronized fail(OAuthCompletionResult.Reason.ATTEMPT_CONSUMED)
            }
            try {
                tokenStore.write(boundTokens)
                if (tokenStore.read(config.canonicalOrigin, config.clientId) == null) {
                    fail(OAuthCompletionResult.Reason.TOKEN_PERSISTENCE_FAILED)
                } else {
                    attemptStore.clearIfState(returnedState)
                    OAuthCompletionResult.Success
                }
            } catch (_: RuntimeException) {
                fail(OAuthCompletionResult.Reason.TOKEN_PERSISTENCE_FAILED)
            }
        }
    }

    fun hasValidStoredTokens(): Boolean {
        val tokens = currentTokens() ?: return false
        if (tokens.accessToken.isBlank() || nowMs >= tokens.expiresAt) return false
        return true
    }

    fun recoverAfterProcessRestart(): Boolean = synchronized(authenticationLock) {
        val attempt = attemptStore.read()
        val tokens = currentTokens()
        val validTokens = tokens != null && tokens.accessToken.isNotBlank() && nowMs < tokens.expiresAt
        if (attempt?.status == OAuthAttempt.Status.CONSUMED) {
            if (!validTokens) tokenStore.clear()
            attemptStore.clear()
        }
        validTokens
    }

    fun getAccessToken(): String? = synchronized(refreshLock) {
        val snapshot = refreshSnapshot() ?: return@synchronized null
        if (!isExpiring(snapshot.tokens)) {
            finishRefreshOperation(snapshot)
            return@synchronized snapshot.tokens.accessToken
        }
        refreshLocked(snapshot)?.accessToken
    }

    fun currentTokens(): OAuthTokens? = synchronized(authenticationLock) {
        tokenStore.read(config.canonicalOrigin, config.clientId)
    }

    fun refresh(): Boolean = synchronized(refreshLock) {
        val snapshot = refreshSnapshot() ?: return@synchronized false
        refreshLocked(snapshot) != null
    }

    fun refreshAccessTokenIfCurrent(observedAccessToken: String): String? = synchronized(refreshLock) {
        val snapshot = refreshSnapshot() ?: return@synchronized null
        if (snapshot.tokens.accessToken != observedAccessToken) {
            finishRefreshOperation(snapshot)
            return@synchronized snapshot.tokens.accessToken
        }
        refreshLocked(snapshot)?.accessToken
    }

    fun clear() = synchronized(authenticationLock) {
        generation++
        activeRefreshOperation = null
        attemptStore.clear()
        tokenStore.clear()
    }

    private fun clearTerminalAttempt(state: String?, operationGeneration: Long): Boolean =
        state != null && synchronized(authenticationLock) {
            val pending = attemptStore.read()
            generation == operationGeneration &&
                pending?.state == state &&
                pending.status == OAuthAttempt.Status.PENDING &&
                pending.matches(config) &&
                attemptStore.clearIfState(state)
        }

    private fun consumeCallbackAttempt(
        returnedState: String,
        operationGeneration: Long
    ): CallbackAttempt = synchronized(authenticationLock) {
        if (generation != operationGeneration) {
            return@synchronized CallbackAttempt.Rejected(OAuthCompletionResult.Reason.ATTEMPT_CONSUMED)
        }
        val visibleAttempt = attemptStore.read()
            ?: return@synchronized CallbackAttempt.Rejected(OAuthCompletionResult.Reason.NO_PERSISTED_ATTEMPT)
        if (visibleAttempt.state != returnedState || !visibleAttempt.matches(config)) {
            return@synchronized CallbackAttempt.Rejected(OAuthCompletionResult.Reason.STATE_MISMATCH)
        }
        if (visibleAttempt.status == OAuthAttempt.Status.CONSUMED) {
            return@synchronized CallbackAttempt.AlreadyConsumed
        }
        if (nowMs > visibleAttempt.expiresAt) {
            attemptStore.clearIfState(returnedState)
            return@synchronized CallbackAttempt.Rejected(OAuthCompletionResult.Reason.ATTEMPT_EXPIRED)
        }
        attemptStore.consume(returnedState)?.let(CallbackAttempt::Pending)
            ?: CallbackAttempt.Rejected(OAuthCompletionResult.Reason.ATTEMPT_CONSUMED)
    }

    private fun completeConsumedAttempt(operationGeneration: Long): OAuthCompletionResult =
        synchronized(authenticationLock) {
            if (generation != operationGeneration) {
                return@synchronized fail(OAuthCompletionResult.Reason.ATTEMPT_CONSUMED)
            }
            if (hasValidStoredTokens()) {
                attemptStore.clear()
                OAuthCompletionResult.Success
            } else {
                fail(OAuthCompletionResult.Reason.ATTEMPT_CONSUMED)
            }
        }

    private fun refreshSnapshot(): RefreshSnapshot? = synchronized(authenticationLock) {
        val snapshotGeneration = generation
        val operation = ++nextRefreshOperation
        activeRefreshOperation = operation
        val tokens = tokenStore.read(config.canonicalOrigin, config.clientId)
        if (tokens == null) {
            activeRefreshOperation = null
            return@synchronized null
        }
        val refreshToken = tokens.refreshToken?.takeIf { it.isNotBlank() }
        if (refreshToken == null) {
            activeRefreshOperation = null
            return@synchronized null
        }
        RefreshSnapshot(tokens, refreshToken, snapshotGeneration, operation)
    }

    private fun finishRefreshOperation(snapshot: RefreshSnapshot) = synchronized(authenticationLock) {
        if (activeRefreshOperation == snapshot.operation) activeRefreshOperation = null
    }

    private fun refreshLocked(snapshot: RefreshSnapshot): OAuthTokens? {
        val refreshed = tokenExchanger.refreshAccessToken(config, snapshot.refreshToken)
        if (refreshed == null) {
            finishRefreshOperation(snapshot)
            return null
        }
        val bound = refreshed.bind(config).copy(
            refreshToken = refreshed.refreshToken ?: snapshot.refreshToken
        )
        return synchronized(authenticationLock) {
            if (generation != snapshot.generation ||
                activeRefreshOperation != snapshot.operation ||
                tokenStore.read(config.canonicalOrigin, config.clientId) != snapshot.tokens
            ) {
                return@synchronized null
            }
            try {
                tokenStore.write(bound)
                tokenStore.read(config.canonicalOrigin, config.clientId)
            } finally {
                if (activeRefreshOperation == snapshot.operation) activeRefreshOperation = null
            }
        }
    }

    private data class RefreshSnapshot(
        val tokens: OAuthTokens,
        val refreshToken: String,
        val generation: Long,
        val operation: Long
    )

    private sealed interface CallbackAttempt {
        data class Pending(val attempt: OAuthAttempt) : CallbackAttempt
        object AlreadyConsumed : CallbackAttempt
        data class Rejected(val reason: OAuthCompletionResult.Reason) : CallbackAttempt
    }

    private fun OAuthTokens.bind(config: OAuthConfiguration) = copy(
        canonicalOrigin = config.canonicalOrigin,
        clientId = config.clientId,
        recordVersion = TokenStore.RECORD_VERSION
    )

    private fun OAuthAttempt.matches(config: OAuthConfiguration): Boolean =
        canonicalOrigin == config.canonicalOrigin &&
            clientId == config.clientId &&
            redirectUri == config.redirectUri

    private fun AuthorizationResponse.matches(config: OAuthConfiguration, expectedState: String): Boolean {
        val request = request
        return state == expectedState &&
            request.state == expectedState &&
            request.clientId == config.clientId &&
            request.redirectUri.toString() == config.redirectUri &&
            request.configuration.authorizationEndpoint.toString() == config.authorizationEndpoint &&
            request.configuration.tokenEndpoint.toString() == config.tokenEndpoint &&
            request.scope == config.scope &&
            request.codeVerifierChallengeMethod == "S256"
    }

    private fun OAuthConfiguration.matchesRedirect(uri: android.net.Uri): Boolean {
        val expected = android.net.Uri.parse(redirectUri)
        if (uri.userInfo != null || uri.fragment != null) return false
        return uri.scheme == expected.scheme &&
            uri.host == expected.host &&
            effectivePort(uri) == effectivePort(expected) &&
            uri.path == expected.path
    }

    private fun effectivePort(uri: android.net.Uri): Int =
        if (uri.port != -1) uri.port else if (uri.scheme == "https") 443 else -1

    private fun fail(reason: OAuthCompletionResult.Reason) = OAuthCompletionResult.Failed(reason)

    private fun isExpiring(tokens: OAuthTokens): Boolean = nowMs >= tokens.expiresAt - 60_000

    private fun generateCodeVerifier(): String = base64Url(
        ByteArray(32).also { SecureRandom().nextBytes(it) }
    )

    private fun generateCodeChallenge(verifier: String): String = base64Url(
        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    )

    private fun base64Url(bytes: ByteArray): String = android.util.Base64.encodeToString(
        bytes,
        android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
    )
}
