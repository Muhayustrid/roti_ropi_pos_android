package com.rotiropi.pos_erpnext.ui

import com.rotiropi.pos_erpnext.auth.AuthenticationState
import com.rotiropi.pos_erpnext.data.BootstrapData
import com.rotiropi.pos_erpnext.data.BootstrapFailure
import com.rotiropi.pos_erpnext.data.BootstrapRefreshTrigger
import com.rotiropi.pos_erpnext.data.BootstrapUser
import com.rotiropi.pos_erpnext.data.PosCapabilities
import com.rotiropi.pos_erpnext.data.PosProfile
import com.rotiropi.pos_erpnext.data.RepositoryResult
import com.rotiropi.pos_erpnext.data.RepositoryState
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AppViewModelTest {
    @Test
    fun recoveredAuthenticationRefreshesOnce() {
        val harness = Harness()
        val viewModel = harness.viewModel

        viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)
        viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)

        assertEquals(listOf(BootstrapRefreshTrigger.APP_OPEN), harness.triggers)
        assertEquals(AppRoute.AUTHENTICATED_SHELL, viewModel.uiState.route)
    }

    @Test
    fun authenticationSuccessRefreshesOnce() {
        val harness = Harness()
        val viewModel = harness.viewModel

        viewModel.onAuthenticationStateChanged(AuthenticationState.Unauthenticated)
        assertEquals(AppRoute.SIGN_IN, viewModel.uiState.route)
        viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)

        assertEquals(listOf(BootstrapRefreshTrigger.AUTH_SUCCESS), harness.triggers)
        assertEquals(AppRoute.AUTHENTICATED_SHELL, viewModel.uiState.route)
    }

    @Test
    fun pendingRefreshCoalescesRepeatedAuthenticationObservation() {
        val harness = Harness(queued = true)
        val viewModel = harness.viewModel

        viewModel.onAuthenticationStateChanged(AuthenticationState.Unauthenticated)
        viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)
        viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)

        assertTrue(harness.triggers.isEmpty())
        assertEquals(AppRoute.LOADING_BOOTSTRAP, viewModel.uiState.route)
        harness.drain()
        assertEquals(listOf(BootstrapRefreshTrigger.AUTH_SUCCESS), harness.triggers)
        assertEquals(AppRoute.AUTHENTICATED_SHELL, viewModel.uiState.route)
    }

    @Test
    fun staleAppOpenAfterAuthenticationLossCannotPublishShell() {
        val harness = Harness(queued = true)
        val viewModel = harness.viewModel

        viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)
        viewModel.onAuthenticationStateChanged(AuthenticationState.Unauthenticated)

        assertEquals(AppRoute.SIGN_IN, viewModel.uiState.route)
        harness.drain()

        assertEquals(listOf(BootstrapRefreshTrigger.APP_OPEN), harness.triggers)
        assertEquals(AppRoute.SIGN_IN, viewModel.uiState.route)
    }

    @Test
    fun authSuccessWaitsBehindStaleAppOpenAndWinsPublication() {
        val harness = Harness(queued = true)
        harness.results[BootstrapRefreshTrigger.APP_OPEN] =
            RepositoryResult.Success(repositoryState(profileCount = 2))
        harness.results[BootstrapRefreshTrigger.AUTH_SUCCESS] =
            RepositoryResult.Success(repositoryState(profileCount = 0))
        val viewModel = harness.viewModel

        viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)
        viewModel.onAuthenticationStateChanged(AuthenticationState.Unauthenticated)
        viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)
        viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)

        harness.drain()

        assertEquals(
            listOf(BootstrapRefreshTrigger.APP_OPEN, BootstrapRefreshTrigger.AUTH_SUCCESS),
            harness.triggers
        )
        assertEquals(AppRoute.AUTHENTICATED_SHELL, viewModel.uiState.route)
    }

    @Test
    fun multipleProfilesWithoutSelectionShowsProfileSelection() {
        val harness = Harness()
        harness.result = RepositoryResult.Success(repositoryState(profileCount = 2))

        harness.viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)

        assertEquals(AppRoute.SELECT_PROFILE, harness.viewModel.uiState.route)
    }

    @Test
    fun noProfilesShowsAuthenticatedShell() {
        val harness = Harness()
        harness.result = RepositoryResult.Success(repositoryState(profileCount = 0))

        harness.viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)

        assertEquals(AppRoute.AUTHENTICATED_SHELL, harness.viewModel.uiState.route)
    }

    @Test
    fun failedRefreshRequiresExplicitRetry() {
        val harness = Harness()
        harness.result = RepositoryResult.Failure(BootstrapFailure.Unavailable)
        val viewModel = harness.viewModel

        viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)

        assertEquals(listOf(BootstrapRefreshTrigger.APP_OPEN), harness.triggers)
        assertTrue(viewModel.uiState.retryRequired)
        assertEquals(AppRoute.AUTHENTICATED_SHELL, viewModel.uiState.route)
        assertFalse(viewModel.uiState.repositoryState.capabilities.any)

        harness.result = RepositoryResult.Success(repositoryState(profileCount = 1, selected = true))
        viewModel.retry()

        assertEquals(
            listOf(BootstrapRefreshTrigger.APP_OPEN, BootstrapRefreshTrigger.RETRY),
            harness.triggers
        )
        assertFalse(viewModel.uiState.retryRequired)
        assertEquals(AppRoute.AUTHENTICATED_SHELL, viewModel.uiState.route)
    }

    @Test
    fun stateReadsDoNotRefresh() {
        val harness = Harness()
        val viewModel = harness.viewModel

        repeat(3) { viewModel.uiState }
        viewModel.onAuthenticationStateChanged(AuthenticationState.Unauthenticated)
        repeat(3) { viewModel.uiState.repositoryState.capabilities }

        assertTrue(harness.triggers.isEmpty())
    }

    @Test
    fun retryWithoutFailureIsNoOp() {
        val harness = Harness()
        val viewModel = harness.viewModel

        viewModel.retry()
        viewModel.onAuthenticationStateChanged(AuthenticationState.Authorizing)
        viewModel.retry()

        assertTrue(harness.triggers.isEmpty())
        assertEquals(AppRoute.SIGN_IN, viewModel.uiState.route)
    }

    @Test
    fun closePreventsLateRefreshPublication() {
        val harness = Harness(queued = true)
        val viewModel = harness.viewModel

        viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)
        assertEquals(AppRoute.LOADING_BOOTSTRAP, viewModel.uiState.route)
        viewModel.close()
        viewModel.close()
        harness.drain()

        assertEquals(AppRoute.LOADING_BOOTSTRAP, viewModel.uiState.route)
        assertEquals(1, harness.closeCalls)
    }

    @Test
    fun firstAuthenticationTransitionPublishesLoadingOnce() {
        val harness = Harness(queued = true)
        val observer = StateObserver(harness.viewModel)
        try {
            observer.emissions.clear()
            harness.viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)

            assertEquals(1, observer.emissions.size)
            assertEquals(AppRoute.LOADING_BOOTSTRAP, harness.viewModel.state.value.route)
        } finally {
            observer.close()
        }
    }

    @Test
    fun repeatedAuthenticatedObservationPublishesNoExtraState() {
        val harness = Harness(queued = true)
        val observer = StateObserver(harness.viewModel)
        try {
            observer.emissions.clear()
            harness.viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)
            harness.viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)

            assertEquals(1, observer.emissions.size)
        } finally {
            observer.close()
        }
    }

    @Test
    fun refreshCompletionPublishesFinalState() {
        val harness = Harness(queued = true)
        val observer = StateObserver(harness.viewModel)
        try {
            observer.emissions.clear()
            harness.viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)
            harness.drain()

            assertEquals(AppRoute.AUTHENTICATED_SHELL, harness.viewModel.state.value.route)
            assertEquals(2, observer.emissions.size)
        } finally {
            observer.close()
        }
    }

    @Test
    fun logoutPublishesSignInRoute() {
        val harness = Harness(queued = true)
        val observer = StateObserver(harness.viewModel)
        try {
            observer.emissions.clear()
            harness.viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)
            harness.viewModel.onAuthenticationStateChanged(AuthenticationState.Unauthenticated)

            assertEquals(AppRoute.SIGN_IN, harness.viewModel.state.value.route)
            assertEquals(AppRoute.SIGN_IN, observer.emissions.last().route)
        } finally {
            observer.close()
        }
    }

    @Test
    fun synchronizeRouteUsesCurrentRepositorySnapshotWithoutRefreshing() {
        val harness = Harness(queued = true)
        harness.viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)
        harness.drain()
        harness.current = repositoryState(profileCount = 2)
        val observer = StateObserver(harness.viewModel)
        try {
            observer.emissions.clear()
            val refreshCount = harness.triggers.size

            harness.viewModel.synchronizeRouteFromRepository()

            assertEquals(AppRoute.SELECT_PROFILE, harness.viewModel.uiState.route)
            assertEquals(refreshCount, harness.triggers.size)
            assertEquals(1, observer.emissions.size)
            assertEquals(harness.current, observer.emissions.single().repositoryState)
        } finally {
            observer.close()
        }
    }

    @Test
    fun synchronizeRouteUsesAuthenticatedShellWhenSelectionExists() {
        val harness = Harness(queued = true)
        harness.viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)
        harness.drain()
        harness.current = repositoryState(profileCount = 2, selected = true)

        harness.viewModel.synchronizeRouteFromRepository()

        assertEquals(AppRoute.AUTHENTICATED_SHELL, harness.viewModel.uiState.route)
    }

    @Test
    fun synchronizeRouteDoesNothingWhileUnauthenticatedOrClosed() {
        val unauthenticated = Harness()
        val unauthenticatedObserver = StateObserver(unauthenticated.viewModel)
        try {
            unauthenticatedObserver.emissions.clear()
            unauthenticated.viewModel.synchronizeRouteFromRepository()
            assertTrue(unauthenticatedObserver.emissions.isEmpty())
            assertTrue(unauthenticated.triggers.isEmpty())
        } finally {
            unauthenticatedObserver.close()
        }

        val closed = Harness(queued = true)
        closed.viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)
        closed.viewModel.close()
        val closedObserver = StateObserver(closed.viewModel)
        try {
            closedObserver.emissions.clear()
            closed.viewModel.synchronizeRouteFromRepository()
            assertTrue(closedObserver.emissions.isEmpty())
            assertTrue(closed.triggers.isEmpty())
        } finally {
            closedObserver.close()
        }
    }

    @Test
    fun synchronizeRouteRepeatedSnapshotPublishesNoDuplicateState() {
        val harness = Harness(queued = true)
        harness.viewModel.onAuthenticationStateChanged(AuthenticationState.Authenticated)
        harness.drain()
        harness.current = repositoryState(profileCount = 2)
        val observer = StateObserver(harness.viewModel)
        try {
            observer.emissions.clear()
            harness.viewModel.synchronizeRouteFromRepository()
            harness.viewModel.synchronizeRouteFromRepository()

            assertEquals(1, observer.emissions.size)
        } finally {
            observer.close()
        }
    }

    @Test
    fun retryWithoutFailurePublishesNoState() {
        val harness = Harness()
        val observer = StateObserver(harness.viewModel)
        try {
            observer.emissions.clear()
            harness.viewModel.retry()

            assertTrue(observer.emissions.isEmpty())
        } finally {
            observer.close()
        }
    }

    private class StateObserver(viewModel: AppViewModel) : AutoCloseable {
        val emissions = mutableListOf<AppUiState>()
        private val scope = CoroutineScope(Dispatchers.Unconfined)

        init {
            scope.launch {
                viewModel.state.collect { emissions += it }
            }
        }

        override fun close() {
            scope.cancel()
        }
    }

    private class Harness(queued: Boolean = false) {
        var current = repositoryState(profileCount = 1, selected = true)
        var result: RepositoryResult = RepositoryResult.Success(current)
        val results = mutableMapOf<BootstrapRefreshTrigger, RepositoryResult>()
        val triggers = mutableListOf<BootstrapRefreshTrigger>()
        var closeCalls = 0
        private val jobs = ArrayDeque<() -> Unit>()
        val viewModel = AppViewModel(
            repositoryState = { current },
            refresh = { trigger ->
                triggers += trigger
                (results[trigger] ?: result).also { if (it is RepositoryResult.Success) current = it.state }
            },
            dispatch = { job -> if (queued) jobs.addLast(job) else job() },
            closeAction = { closeCalls++ }
        )

        fun drain() {
            while (jobs.isNotEmpty()) jobs.removeFirst().invoke()
        }
    }

    private companion object {
        fun repositoryState(profileCount: Int, selected: Boolean = false): RepositoryState {
            val profiles = (1..profileCount).map { profile(it.toString()) }
            return RepositoryState(
                bootstrap = BootstrapData(
                    user = BootstrapUser("user", "User"),
                    profiles = profiles,
                    selectedProfile = if (selected) profiles.firstOrNull() else null,
                    opening = null,
                    capabilities = PosCapabilities.DISABLED,
                    posMode = "standard"
                )
            )
        }

        fun profile(name: String) = PosProfile(
            name = name,
            company = "Company",
            warehouse = "Warehouse",
            currency = "USD",
            sellingPriceList = "Standard",
            customer = "Customer",
            allowPartialPayment = true,
            invoiceMode = "POS"
        )
    }
}
