package com.rotiropi.pos_erpnext.ui

import com.rotiropi.pos_erpnext.auth.AuthenticationState
import com.rotiropi.pos_erpnext.data.BootstrapFailure
import com.rotiropi.pos_erpnext.data.BootstrapRefreshTrigger
import com.rotiropi.pos_erpnext.data.MobilePosRepository
import com.rotiropi.pos_erpnext.data.PosCapabilities
import com.rotiropi.pos_erpnext.data.RepositoryResult
import com.rotiropi.pos_erpnext.data.RepositoryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


enum class AppRoute {
    SIGN_IN,
    LOADING_BOOTSTRAP,
    SELECT_PROFILE,
    AUTHENTICATED_SHELL
}

data class AppUiState(
    val route: AppRoute,
    val repositoryState: RepositoryState,
    val retryRequired: Boolean = false,
    val error: String? = null
)

class AppViewModel internal constructor(
    private val repositoryState: () -> RepositoryState,
    private val refresh: (BootstrapRefreshTrigger) -> RepositoryResult,
    private val dispatch: ((() -> Unit) -> Unit),
    private val closeAction: () -> Unit = {}
) : AutoCloseable {
    constructor(repository: MobilePosRepository) : this(OwnedDependencies(repository))

    private constructor(dependencies: OwnedDependencies) : this(
        repositoryState = dependencies.repositoryState,
        refresh = dependencies.refresh,
        dispatch = dependencies.dispatch,
        closeAction = dependencies.closeAction
    )

    private val lock = Any()
    private var initialized = false
    private var authenticated = false
    private var authGeneration = 0L
    private var refreshPending = false
    private var pendingTrigger: BootstrapRefreshTrigger? = null
    private var closed = false

    private val _state = MutableStateFlow(AppUiState(AppRoute.SIGN_IN, repositoryState()))
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    val uiState: AppUiState
        get() = state.value

    private fun publish(next: AppUiState) {
        _state.value = next
    }

    fun onAuthenticationStateChanged(state: AuthenticationState) {
        synchronized(lock) {
            if (closed) return
            val nextAuthenticated = state === AuthenticationState.Authenticated
            if (!initialized) {
                initialized = true
                authenticated = nextAuthenticated
                if (authenticated) {
                    requestRefreshLocked(BootstrapRefreshTrigger.APP_OPEN)
                } else {
                    showSignInLocked()
                }
                return
            }
            if (authenticated != nextAuthenticated) {
                authGeneration++
                authenticated = nextAuthenticated
                if (authenticated) {
                    requestRefreshLocked(BootstrapRefreshTrigger.AUTH_SUCCESS)
                } else {
                    pendingTrigger = null
                    showSignInLocked()
                }
            } else if (!authenticated) {
                showSignInLocked()
            }
        }
    }

    fun retry() {
        synchronized(lock) {
            if (closed || !uiState.retryRequired) return
            requestRefreshLocked(BootstrapRefreshTrigger.RETRY)
        }
    }

    fun refreshAfterOpeningCompletion() {
        synchronized(lock) {
            if (closed || !authenticated) return
            requestRefreshLocked(BootstrapRefreshTrigger.OPENING_COMPLETED)
        }
    }

    fun synchronizeRouteFromRepository() {
        synchronized(lock) {
            if (closed || !authenticated) return
            val snapshot = repositoryState()
            val route = if (snapshot.profiles.size > 1 && snapshot.selectedProfile == null) {
                AppRoute.SELECT_PROFILE
            } else {
                AppRoute.AUTHENTICATED_SHELL
            }
            val current = uiState
            val next = AppUiState(
                route = route,
                repositoryState = snapshot,
                retryRequired = current.retryRequired,
                error = current.error,
            )
            if (next != current) publish(next)
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            closeAction()
        }
    }

    private fun requestRefreshLocked(trigger: BootstrapRefreshTrigger) {
        if (closed) return
        if (refreshPending) {
            pendingTrigger = when {
                trigger == BootstrapRefreshTrigger.AUTH_SUCCESS -> trigger
                pendingTrigger == null -> trigger
                else -> pendingTrigger
            }
            return
        }
        beginRefreshLocked(trigger, authGeneration)
    }

    private fun beginRefreshLocked(trigger: BootstrapRefreshTrigger, generation: Long) {
        if (refreshPending || closed) return
        refreshPending = true
        publish(
            AppUiState(
                route = AppRoute.LOADING_BOOTSTRAP,
                repositoryState = repositoryState()
            )
        )
        dispatch {
            val result = try {
                refresh(trigger)
            } catch (error: RuntimeException) {
                RepositoryResult.Failure(BootstrapFailure.Protocol(error.message ?: "Refresh failed"))
            }
            synchronized(lock) {
                if (closed) return@synchronized
                refreshPending = false
                val nextTrigger = pendingTrigger
                pendingTrigger = null
                if (generation == authGeneration && authenticated) {
                    publish(
                        when (result) {
                            is RepositoryResult.Success -> {
                                val state = repositoryState()
                                AppUiState(
                                    route = if (state.profiles.size > 1 && state.selectedProfile == null) {
                                        AppRoute.SELECT_PROFILE
                                    } else {
                                        AppRoute.AUTHENTICATED_SHELL
                                    },
                                    repositoryState = state
                                )
                            }
                            is RepositoryResult.Failure -> {
                                val state = repositoryState().copy(
                                    bootstrap = repositoryState().bootstrap?.copy(
                                        capabilities = PosCapabilities.DISABLED
                                    ),
                                    bootstrapFailure = result.reason
                                )
                                AppUiState(
                                    route = AppRoute.AUTHENTICATED_SHELL,
                                    repositoryState = state,
                                    retryRequired = true,
                                    error = result.reason.errorMessage()
                                )
                            }
                            RepositoryResult.Discarded -> uiState
                        }
                    )
                }
                if (nextTrigger != null && authenticated) {
                    beginRefreshLocked(nextTrigger, authGeneration)
                }
            }
        }
    }

    private fun showSignInLocked() {
        if (state.value.route == AppRoute.SIGN_IN) return
        publish(
            AppUiState(
                route = AppRoute.SIGN_IN,
                repositoryState = repositoryState()
            )
        )
    }

    private fun BootstrapFailure.errorMessage(): String = when (this) {
        BootstrapFailure.AuthRequired -> "Authentication required"
        BootstrapFailure.Unavailable -> "Bootstrap unavailable"
        is BootstrapFailure.Protocol -> reason
    }

    private class OwnedDependencies(repository: MobilePosRepository) {
        private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "app-bootstrap").apply { isDaemon = true }
        }

        val repositoryState: () -> RepositoryState = { repository.state }
        val refresh: (BootstrapRefreshTrigger) -> RepositoryResult = repository::refreshCapabilities
        val dispatch: ((() -> Unit) -> Unit) = { task -> executor.execute(task) }
        val closeAction: () -> Unit = { executor.shutdownNow() }
    }
}
