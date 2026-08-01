package com.rotiropi.pos_erpnext.auth

import android.content.Intent
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AuthenticationState {
    object Unauthenticated : AuthenticationState
    object Authorizing : AuthenticationState
    object Authenticated : AuthenticationState
    data class Error(val reason: OAuthCompletionResult.Reason) : AuthenticationState
}

/** Application-scoped owner for authentication state and terminal cleanup. */
class AuthenticationOwner(
    internal val coordinator: OAuthCoordinator,
    private val completionExecutor: Executor = DEFAULT_COMPLETION_EXECUTOR
) {
    private val ownerLock = Any()
    private var generation = 0L
    private val mutableState = MutableStateFlow<AuthenticationState>(AuthenticationState.Unauthenticated)
    val state: StateFlow<AuthenticationState> = mutableState.asStateFlow()

    val isAuthenticated: Boolean
        get() = mutableState.value == AuthenticationState.Authenticated

    init {
        restoreAuthenticationState()
    }

    fun restoreAuthenticationState() {
        mutableState.value = if (coordinator.recoverAfterProcessRestart()) {
            AuthenticationState.Authenticated
        } else {
            AuthenticationState.Unauthenticated
        }
    }

    fun beginAuthorization() = synchronized(ownerLock) {
        mutableState.value = AuthenticationState.Authorizing
        try {
            coordinator.beginAuthorization()
        } catch (error: RuntimeException) {
            mutableState.value = AuthenticationState.Error(OAuthCompletionResult.Reason.AUTHORIZATION_LAUNCH_FAILED)
            throw error
        }
    }

    fun handleCompletion(intent: Intent): OAuthCompletionResult =
        complete(intent, synchronized(ownerLock) { generation })

    fun handleCompletionAsync(intent: Intent) {
        val operationGeneration = synchronized(ownerLock) { generation }
        completionExecutor.execute { complete(intent, operationGeneration) }
    }

    private fun complete(intent: Intent, operationGeneration: Long): OAuthCompletionResult {
        val result = coordinator.handleCompletion(intent)
        return synchronized(ownerLock) {
            if (generation != operationGeneration) {
                return@synchronized OAuthCompletionResult.Failed(
                    OAuthCompletionResult.Reason.ATTEMPT_CONSUMED
                )
            }
            mutableState.value = when (result) {
                OAuthCompletionResult.Success -> if (coordinator.hasValidStoredTokens()) {
                    AuthenticationState.Authenticated
                } else {
                    AuthenticationState.Error(OAuthCompletionResult.Reason.TOKEN_PERSISTENCE_FAILED)
                }
                is OAuthCompletionResult.Failed -> AuthenticationState.Error(result.reason)
            }
            result
        }
    }

    fun logout() = synchronized(ownerLock) {
        generation++
        coordinator.clear()
        mutableState.value = AuthenticationState.Unauthenticated
    }

    private companion object {
        val DEFAULT_COMPLETION_EXECUTOR: Executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "oauth-completion").apply { isDaemon = true }
        }
    }
}
