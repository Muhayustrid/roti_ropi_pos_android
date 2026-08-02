package com.rotiropi.pos_erpnext.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rotiropi.pos_erpnext.session.LogoutResult
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Task4RootHost(
    activity: ComponentActivity,
    private val application: MobilePosApplication,
    private val binding: Task4RootBinding,
) {
    private val logoutResult = MutableStateFlow<LogoutResult?>(null)
    private val recoveryViewModel = com.rotiropi.pos_erpnext.recovery.RecoveryViewModel(
        authenticationSnapshot = { application.authenticationOwner.snapshot },
        currentIdentity = application::currentRecoveryIdentity,
        recoveryState = { application.recoveryCoordinator.uiState.value },
        readTerminalResult = application.recoveryCoordinator::readTerminalResult,
        acknowledgeTerminal = application.recoveryCoordinator::acknowledge,
        quarantine = { application.recoveryCoordinator.quarantine(it) },
    )
    private val controller = Task4RootController(
        binding = binding,
        onRetry = ::retry,
        onLogout = ::logout,
        onLogoutResult = { logoutResult.value = it },
    )

    private fun logout(): LogoutResult = application.logoutCoordinator.logout().also {
        logoutResult.value = it
        if (it is LogoutResult.LoggedOut) {
            application.recoveryCoordinator.clearUiState()
            recoveryViewModel.onAuthenticationChanged(application.authenticationOwner.snapshot)
        }
        application.profileSelectionViewModel.setRecoveryState(it, application.recoveryCoordinator.uiState.value)
    }

    private fun acknowledgeRecovery(transactionId: String) {
        val terminal = recoveryViewModel.state.value as? com.rotiropi.pos_erpnext.recovery.RecoveryScreenState.Terminal
            ?: return
        if (terminal.transactionId != transactionId) return
        val acknowledgement = recoveryViewModel.acknowledge()
        if (acknowledgement is com.rotiropi.pos_erpnext.recovery.RecoveryAcknowledgement.Acknowledged) {
            logoutResult.value = null
        }
        application.profileSelectionViewModel.setRecoveryState(logoutResult.value, recoveryViewModel.state.value)
    }

    init {
        binding.task4Profile.setOnProfileSelected(::selectProfile)
        binding.task4Profile.setOnRetry(::retryProfile)
        binding.task4Profile.setOnLogout { logout() }
        binding.task4Profile.setOnAcknowledgeRecovery(::acknowledgeRecovery)
        binding.task4Profile.setOnReauthenticateRecovery(application.authenticationOwner::beginAuthorization)
        binding.task4LegacyShell.setContent {
            val preferences = remember { ThemePreferences.from(activity.applicationContext) }
            var selection by remember { mutableStateOf(preferences.read()) }
            val darkTheme = when (selection.mode) {
                PosThemeMode.SYSTEM -> isSystemInDarkTheme()
                PosThemeMode.LIGHT -> false
                PosThemeMode.DARK -> true
            }
            application.recoveryCoordinator.uiState.collectAsState().value
            val recovery = recoveryViewModel.state.collectAsState().value
            PosTheme(darkTheme = darkTheme, accent = selection.accent) {
                PosShell(
                    authenticationOwner = application.authenticationOwner,
                    onLogout = ::logout,
                    logoutResult = logoutResult.collectAsState().value,
                    recoveryState = recovery,
                    onAcknowledgeRecovery = ::acknowledgeRecovery,
                    onReauthenticateRecovery = application.authenticationOwner::beginAuthorization,
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
                    application.recoveryCoordinator.uiState,
                ) { authentication, app, profile, recovery ->
                    RootState(authentication, app, profile, recovery)
                }.collect { (authentication, app, profile, _) ->
                        application.appViewModel.onAuthenticationStateChanged(authentication)
                        val currentApp = application.appViewModel.state.value
                        val snapshot = application.authenticationOwner.snapshot
                        recoveryViewModel.onAuthenticationChanged(snapshot)
                        if (authentication != AuthenticationState.Authenticated) {
                            application.recoveryCoordinator.clearUiState()
                            application.profileSelectionViewModel.setRecoveryState(null, com.rotiropi.pos_erpnext.recovery.RecoveryUiState())
                        }
                        val repositoryState = application.mobilePosRepository.state
                        val recoveryIdentity = application.currentRecoveryIdentity()
                        if (recoveryBootstrapReady(authentication, currentApp, repositoryState, recoveryIdentity)) {
                            val readyIdentity = requireNotNull(recoveryIdentity)
                            recoveryViewModel.refresh(snapshot, readyIdentity)
                            if (application.authenticationOwner.snapshot == snapshot &&
                                application.currentRecoveryIdentity() == readyIdentity
                            ) {
                                application.profileSelectionViewModel.setRecoveryState(
                                    logoutResult.value,
                                    recoveryViewModel.state.value,
                                )
                            }
                            activity.lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    application.recoverPendingMutationsAfterBootstrap(
                                        allowReauthenticationResume = currentApp.route != AppRoute.LOADING_BOOTSTRAP && currentApp.error == null,
                                    )
                                }
                            }
                        }
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

    private fun recoveryBootstrapReady(
        authentication: AuthenticationState,
        app: AppUiState,
        repository: com.rotiropi.pos_erpnext.data.RepositoryState,
        identity: com.rotiropi.pos_erpnext.recovery.RecoveryIdentity?,
    ): Boolean = authentication == AuthenticationState.Authenticated &&
        identity != null &&
        app.route != AppRoute.LOADING_BOOTSTRAP &&
        app.error == null &&
        app.repositoryState.bootstrap == repository.bootstrap

    private data class RootState(
        val authentication: AuthenticationState,
        val app: AppUiState,
        val profile: ProfileSelectionUiState,
        val recovery: com.rotiropi.pos_erpnext.recovery.RecoveryUiState,
    )

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
