package com.rotiropi.pos_erpnext.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rotiropi.pos_erpnext.MobilePosApplication
import com.rotiropi.pos_erpnext.auth.AuthenticationState
import com.rotiropi.pos_erpnext.databinding.Task4RootBinding
import com.rotiropi.pos_erpnext.ui.navigation.PosShell
import com.rotiropi.pos_erpnext.ui.profile.ProfileSelectionUiState
import com.rotiropi.pos_erpnext.ui.settings.PosThemeMode
import com.rotiropi.pos_erpnext.ui.settings.ThemePreferences
import com.rotiropi.pos_erpnext.ui.theme.PosTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Task4RootHost(
    activity: ComponentActivity,
    private val application: MobilePosApplication,
    private val binding: Task4RootBinding,
) {
    private val controller = Task4RootController(
        binding = binding,
        onRetry = ::retry,
        onLogout = application.logoutCoordinator::logout,
    )

    init {
        binding.task4Profile.setOnProfileSelected(::selectProfile)
        binding.task4Profile.setOnRetry(::retryProfile)
        binding.task4Profile.setOnLogout(application.logoutCoordinator::logout)
        binding.task4LegacyShell.setContent {
            val preferences = remember { ThemePreferences.from(activity.applicationContext) }
            var selection by remember { mutableStateOf(preferences.read()) }
            val darkTheme = when (selection.mode) {
                PosThemeMode.SYSTEM -> isSystemInDarkTheme()
                PosThemeMode.LIGHT -> false
                PosThemeMode.DARK -> true
            }
            PosTheme(darkTheme = darkTheme, accent = selection.accent) {
                PosShell(
                    authenticationOwner = application.authenticationOwner,
                    onLogout = application.logoutCoordinator::logout,
                    themeMode = selection.mode,
                    accent = selection.accent,
                    onThemeModeSelected = { mode ->
                        selection = selection.copy(mode = mode)
                        preferences.writeMode(mode)
                    },
                    onAccentSelected = { accent ->
                        selection = selection.copy(accent = accent)
                        preferences.writeAccent(accent)
                    },
                )
            }
        }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    application.authenticationOwner.state,
                    application.appViewModel.state,
                    application.profileSelectionViewModel.state,
                ) { authentication, app, profile -> Triple(authentication, app, profile) }
                    .collect { (authentication, app, profile) ->
                        application.appViewModel.onAuthenticationStateChanged(authentication)
                        val repositoryState = application.mobilePosRepository.state
                        if (profile.profiles != repositoryState.profiles ||
                            profile.selectedProfileName != repositoryState.selectedProfile?.name
                        ) {
                            application.profileSelectionViewModel.synchronizeFromRepository()
                        }
                        if (app.repositoryState != repositoryState ||
                            shouldSynchronizeProfileRoute(authentication, app, profile)
                        ) {
                            application.appViewModel.synchronizeRouteFromRepository()
                        }
                        controller.render(
                            authentication,
                            application.appViewModel.state.value,
                            application.profileSelectionViewModel.state.value,
                        )
                    }
            }
        }
    }

    private fun selectProfile(name: String) {
        runProfileAction { application.profileSelectionViewModel.selectProfile(name) }
    }

    private fun retryProfile() {
        runProfileAction(application.profileSelectionViewModel::retry)
    }

    private fun retry() {
        applicationScopeIo(application.appViewModel::retry)
    }

    private fun runProfileAction(action: () -> Unit) {
        applicationScopeIo {
            action()
            application.appViewModel.synchronizeRouteFromRepository()
        }
    }

    private fun applicationScopeIo(action: () -> Unit) {
        binding.root.post {
            val activity = binding.root.context as? ComponentActivity ?: return@post
            activity.lifecycleScope.launch {
                withContext(Dispatchers.IO) { action() }
                application.profileSelectionViewModel.synchronizeFromRepository()
                controller.completeProfileAction()
                controller.render(
                    application.authenticationOwner.state.value,
                    application.appViewModel.state.value,
                    application.profileSelectionViewModel.state.value,
                )
            }
        }
    }
}
