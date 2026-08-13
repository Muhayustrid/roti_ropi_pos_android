package com.rotiropi.pos_erpnext.ui

import android.view.View
import com.rotiropi.pos_erpnext.auth.AuthenticationState
import com.rotiropi.pos_erpnext.data.RepositoryState
import com.rotiropi.pos_erpnext.databinding.Task4RootBinding
import com.rotiropi.pos_erpnext.ui.profile.ProfileSelectionUiState

class Task4RootController(
    private val binding: Task4RootBinding,
    onRetry: () -> Unit = {},
    onLogout: () -> com.rotiropi.pos_erpnext.session.LogoutResult = {
        com.rotiropi.pos_erpnext.session.LogoutResult.LoggedOut
    },
    private val onLogoutResult: (com.rotiropi.pos_erpnext.session.LogoutResult) -> Unit = {},
) {
    private var profileChangeRequested = false
    private var latestProfileState: ProfileSelectionUiState? = null

    init {
        binding.task4Retry.setOnClickListener { onRetry() }
        binding.task4Logout.setOnClickListener { onLogoutResult(onLogout()) }
        binding.task4ChangeProfile.setOnClickListener {
            profileChangeRequested = true
            showProfileChooser()
        }
    }

    fun render(
        authenticationState: AuthenticationState,
        appState: AppUiState,
        profileState: ProfileSelectionUiState,
    ) {
        latestProfileState = profileState
        val authenticated = authenticationState === AuthenticationState.Authenticated
        if (!authenticated) {
            profileChangeRequested = false
            binding.task4Status.visibility = View.GONE
            binding.task4Profile.visibility = View.GONE
            binding.task4LegacyShell.visibility = View.VISIBLE
            return
        }

        if (appState.route == AppRoute.LOADING_BOOTSTRAP ||
            appState.route == AppRoute.SELECT_PROFILE ||
            profileChangeRequested
        ) {
            binding.task4Status.visibility = View.GONE
            binding.task4LegacyShell.visibility = View.GONE
            binding.task4Profile.visibility = View.VISIBLE
            binding.task4Profile.render(
                if (appState.route == AppRoute.LOADING_BOOTSTRAP) {
                    profileState.copy(refreshing = true)
                } else {
                    profileState
                }
            )
            return
        }

        profileChangeRequested = false
        binding.task4Profile.visibility = View.GONE
        binding.task4LegacyShell.visibility = View.VISIBLE
        binding.task4Status.visibility = View.VISIBLE
        binding.task4ProfileName.text = appState.repositoryState.selectedProfile?.name.orEmpty()
        binding.task4ChangeProfile.visibility = if (appState.repositoryState.profiles.size > 1) {
            View.VISIBLE
        } else {
            View.GONE
        }
        val warning = staleOpeningWarning(appState.repositoryState)
        binding.task4Warning.text = warning.orEmpty()
        binding.task4Warning.visibility = if (warning == null) View.GONE else View.VISIBLE
        binding.task4Error.text = appState.error?.resolve(binding.root.context).orEmpty()
        binding.task4Error.visibility = if (appState.error == null) View.GONE else View.VISIBLE
        binding.task4Retry.visibility = if (appState.retryRequired) View.VISIBLE else View.GONE
    }

    fun completeProfileAction() {
        profileChangeRequested = false
    }

    private fun showProfileChooser() {
        binding.task4Status.visibility = View.GONE
        binding.task4LegacyShell.visibility = View.GONE
        binding.task4Profile.visibility = View.VISIBLE
        latestProfileState?.let(binding.task4Profile::render)
    }
}

fun shouldSynchronizeProfileRoute(
    authenticationState: AuthenticationState,
    appState: AppUiState,
    profileState: ProfileSelectionUiState,
): Boolean = authenticationState === AuthenticationState.Authenticated &&
    appState.route == AppRoute.SELECT_PROFILE &&
    profileState.selectedProfileName != null &&
    !profileState.refreshing &&
    !profileState.retryRequired &&
    profileState.error == null

fun staleOpeningWarning(repositoryState: RepositoryState): String? =
    repositoryState.opening?.warnings
        ?.firstOrNull { it.code == "STALE_OPENING" }
        ?.let { "STALE_OPENING: ${it.message}" }
