package com.rotiropi.pos_erpnext.ui

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.compose.ui.platform.ComposeView
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.auth.AuthenticationState
import com.rotiropi.pos_erpnext.data.BootstrapData
import com.rotiropi.pos_erpnext.data.BootstrapUser
import com.rotiropi.pos_erpnext.data.OpeningSession
import com.rotiropi.pos_erpnext.data.OpeningStatus
import com.rotiropi.pos_erpnext.data.OpeningWarning
import com.rotiropi.pos_erpnext.data.PosCapabilities
import com.rotiropi.pos_erpnext.data.PosProfile
import com.rotiropi.pos_erpnext.data.RepositoryState
import com.rotiropi.pos_erpnext.databinding.Task4RootBinding
import com.rotiropi.pos_erpnext.ui.profile.ProfileSelectionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class Task4RootControllerTest {
    private val context = ContextThemeWrapper(
        RuntimeEnvironment.getApplication(),
        R.style.Theme_POSERPNext,
    )

    @Test
    fun native_root_owns_loading_selection_and_authenticated_status_routes() {
        val binding = Task4RootBinding.inflate(LayoutInflater.from(context))
        val controller = Task4RootController(binding)

        controller.render(
            AuthenticationState.Authenticated,
            appState(AppRoute.LOADING_BOOTSTRAP),
            profileState(refreshing = true),
        )
        assertEquals(View.VISIBLE, binding.task4Profile.visibility)
        assertEquals(View.GONE, binding.task4LegacyShell.visibility)

        controller.render(
            AuthenticationState.Authenticated,
            appState(AppRoute.SELECT_PROFILE, repositoryState(profileCount = 2)),
            profileState(profiles = profiles(2)),
        )
        assertEquals(View.VISIBLE, binding.task4Profile.visibility)

        controller.render(
            AuthenticationState.Authenticated,
            appState(AppRoute.AUTHENTICATED_SHELL, repositoryState(profileCount = 2, selected = true)),
            profileState(profiles = profiles(2), selected = "PROFILE-1"),
        )
        assertEquals(View.GONE, binding.task4Profile.visibility)
        assertEquals(View.VISIBLE, binding.task4LegacyShell.visibility)
        assertEquals(View.VISIBLE, binding.task4Status.visibility)
        assertEquals("PROFILE-1", binding.task4ProfileName.text.toString())
        assertEquals(View.VISIBLE, binding.task4ChangeProfile.visibility)
    }

    @Test
    fun unauthenticated_route_uses_unchanged_legacy_compose_surface() {
        val binding = Task4RootBinding.inflate(LayoutInflater.from(context))
        val controller = Task4RootController(binding)

        controller.render(
            AuthenticationState.Unauthenticated,
            appState(AppRoute.AUTHENTICATED_SHELL),
            profileState(),
        )

        assertEquals(View.VISIBLE, binding.task4LegacyShell.visibility)
        assertEquals(View.GONE, binding.task4Profile.visibility)
        assertEquals(View.GONE, binding.task4Status.visibility)
        assertTrue(binding.task4LegacyShell is ComposeView)
    }

    @Test
    fun native_logout_preserves_blocked_result_for_host_rendering() {
        var rendered: com.rotiropi.pos_erpnext.session.LogoutResult? = null
        val blocked = com.rotiropi.pos_erpnext.session.LogoutResult.Blocked(
            "cashier-1",
            com.rotiropi.pos_erpnext.recovery.PendingMutationState.AUTH_REQUIRED,
        )
        val binding = Task4RootBinding.inflate(LayoutInflater.from(context))
        val controller = Task4RootController(
            binding = binding,
            onLogout = { blocked },
            onLogoutResult = { rendered = it },
        )

        binding.task4Logout.performClick()

        assertEquals(blocked, rendered)
    }

    @Test
    fun stale_warning_retry_change_profile_and_logout_are_native_actions() {
        var retries = 0
        var logouts = 0
        val binding = Task4RootBinding.inflate(LayoutInflater.from(context))
        val controller = Task4RootController(
            binding = binding,
            onRetry = { retries++ },
            onLogout = {
                logouts++
                com.rotiropi.pos_erpnext.session.LogoutResult.LoggedOut
            },
        )
        val state = repositoryState(profileCount = 2, selected = true, stale = true)
        val selectedProfile = profileState(profiles = profiles(2), selected = "PROFILE-1")

        controller.render(
            AuthenticationState.Authenticated,
            AppUiState(
                route = AppRoute.AUTHENTICATED_SHELL,
                repositoryState = state,
                retryRequired = true,
                error = "Bootstrap unavailable",
            ),
            selectedProfile,
        )

        assertTrue(binding.task4Warning.text.toString().contains("STALE_OPENING"))
        assertEquals(View.VISIBLE, binding.task4Retry.visibility)
        binding.task4Retry.performClick()
        binding.task4Logout.performClick()
        assertEquals(1, retries)
        assertEquals(1, logouts)

        binding.task4ChangeProfile.performClick()
        assertEquals(View.VISIBLE, binding.task4Profile.visibility)
        assertEquals(View.GONE, binding.task4LegacyShell.visibility)
        assertEquals(
            2,
            binding.task4Profile.findViewById<LinearLayout>(R.id.profile_selection_rows).childCount,
        )

        controller.completeProfileAction()
        controller.render(
            AuthenticationState.Authenticated,
            appState(AppRoute.AUTHENTICATED_SHELL, state),
            selectedProfile,
        )
        assertEquals(View.VISIBLE, binding.task4LegacyShell.visibility)
        assertEquals(View.GONE, binding.task4Profile.visibility)

        binding.task4ChangeProfile.performClick()
        controller.render(
            AuthenticationState.Unauthenticated,
            appState(AppRoute.SIGN_IN),
            profileState(),
        )
        controller.render(
            AuthenticationState.Authenticated,
            appState(AppRoute.AUTHENTICATED_SHELL, state),
            selectedProfile,
        )
        assertEquals(View.VISIBLE, binding.task4LegacyShell.visibility)
        assertEquals(View.GONE, binding.task4Profile.visibility)
    }

    @Test
    fun durable_profile_success_requires_route_synchronization_after_retry_or_recreation() {
        val selectedRepository = repositoryState(profileCount = 2, selected = true)
        val selectedProfile = profileState(profiles = profiles(2), selected = "PROFILE-1")

        assertTrue(
            shouldSynchronizeProfileRoute(
                AuthenticationState.Authenticated,
                appState(AppRoute.SELECT_PROFILE, selectedRepository),
                selectedProfile,
            )
        )
        assertFalse(
            shouldSynchronizeProfileRoute(
                AuthenticationState.Authenticated,
                appState(AppRoute.SELECT_PROFILE, selectedRepository),
                selectedProfile.copy(retryRequired = true, error = "retry"),
            )
        )
    }

    private fun appState(
        route: AppRoute,
        repositoryState: RepositoryState = RepositoryState(),
    ) = AppUiState(route, repositoryState)

    private fun profileState(
        refreshing: Boolean = false,
        profiles: List<PosProfile> = emptyList(),
        selected: String? = null,
    ) = ProfileSelectionUiState(
        profiles = profiles,
        selectedProfileName = selected,
        selectionRequired = profiles.size > 1 && selected == null,
        refreshing = refreshing,
        error = null,
        retryRequired = false,
        anyActionEnabled = selected != null,
    )

    private fun repositoryState(
        profileCount: Int,
        selected: Boolean = false,
        stale: Boolean = false,
    ): RepositoryState {
        val profiles = profiles(profileCount)
        val opening = if (stale) {
            OpeningSession(
                name = "OPEN-1",
                posProfile = "PROFILE-1",
                company = "Company",
                user = "cashier@example.com",
                status = OpeningStatus.OPEN,
                postingDate = "2026-08-01",
                periodStartDate = "2026-08-01T08:00:00Z",
                openingBalances = emptyList(),
                warnings = listOf(OpeningWarning("STALE_OPENING", "Opening is old", emptyMap())),
            )
        } else null
        return RepositoryState(
            bootstrap = BootstrapData(
                user = BootstrapUser("cashier@example.com", "Cashier"),
                profiles = profiles,
                selectedProfile = if (selected) profiles.firstOrNull() else null,
                opening = opening,
                capabilities = PosCapabilities.DISABLED,
                posMode = "POS Invoice",
            )
        )
    }

    private fun profiles(count: Int) = (1..count).map { index ->
        PosProfile(
            name = "PROFILE-$index",
            company = "Company",
            warehouse = "Warehouse",
            currency = "IDR",
            sellingPriceList = "Standard",
            customer = "Walk In",
            allowPartialPayment = false,
            invoiceMode = "POS Invoice",
        )
    }
}
