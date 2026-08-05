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
import com.rotiropi.pos_erpnext.ui.opening.OpeningDestination
import com.rotiropi.pos_erpnext.ui.opening.OpeningFlowCoordinator
import com.rotiropi.pos_erpnext.ui.opening.OpeningFlowResult
import com.rotiropi.pos_erpnext.ui.opening.OpeningReconciliationRunner
import com.rotiropi.pos_erpnext.ui.opening.OpeningViewModel
import com.rotiropi.pos_erpnext.ui.opening.RecoveredOpeningTerminal
import com.rotiropi.pos_erpnext.ui.profile.ProfileSelectionUiState
import com.rotiropi.pos_erpnext.ui.settings.PosThemeMode
import com.rotiropi.pos_erpnext.ui.settings.ThemePreferences
import com.rotiropi.pos_erpnext.ui.theme.PosTheme
import com.rotiropi.pos_erpnext.ui.customer.CustomerSearchIdentity
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
    private val openingState = MutableStateFlow<com.rotiropi.pos_erpnext.ui.opening.OpeningUiState?>(null)
    private val customerSheetVisible = MutableStateFlow(false)
    private var openingViewModel: OpeningViewModel? = null
    private var openingFlow: OpeningFlowCoordinator? = null
    private var openingReconciliation: OpeningReconciliationRunner? = null
    private var handledOpeningTerminal: String? = null
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
            openingViewModel?.clear()
            openingViewModel = null
            openingFlow = null
            openingReconciliation = null
            openingState.value = null
            handledOpeningTerminal = null
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
            val opening = openingState.collectAsState().value
            val customer = application.customerSearchViewModel.state.collectAsState().value
            PosTheme(darkTheme = darkTheme, accent = selection.accent) {
                PosShell(
                    authenticationOwner = application.authenticationOwner,
                    onLogout = ::logout,
                    logoutResult = logoutResult.collectAsState().value,
                    recoveryState = recovery,
                    onAcknowledgeRecovery = ::acknowledgeRecovery,
                    onReauthenticateRecovery = application.authenticationOwner::beginAuthorization,
                    openingState = opening,
                    onOpeningAmountChanged = { mode, amount ->
                        openingViewModel?.updateAmount(mode, amount)
                        openingState.value = openingViewModel?.state?.value
                    },
                    onOpenSession = ::openSession,
                    customerState = customer,
                    customerSheetVisible = customerSheetVisible.collectAsState().value,
                    onOpenCustomerSheet = {
                        application.customerSearchViewModel.onSelectorOpened()
                        customerSheetVisible.value = true
                    },
                    onDismissCustomerSheet = { customerSheetVisible.value = false },
                    onCustomerQueryChanged = application.customerSearchViewModel::onQueryChanged,
                    onWalkInNameChanged = application.customerSearchViewModel::onWalkInDisplayNameChanged,
                    onSelectWalkIn = application.customerSearchViewModel::selectWalkIn,
                    onSelectRegistered = application.customerSearchViewModel::selectCustomer,
                    onCustomerRetry = application.customerSearchViewModel::retry,
                    onCustomerLoadMore = application.customerSearchViewModel::loadMore,
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
                        synchronizeOpeningFlow(authentication, application.appViewModel.state.value)
                        synchronizeCustomerSearch(authentication)
                        synchronizeRecoveredOpening(recoveryViewModel.state.value)
                        controller.render(
                            authentication,
                            application.appViewModel.state.value,
                            application.profileSelectionViewModel.state.value,
                        )
                    }
            }
        }
    }

    private fun synchronizeOpeningFlow(authentication: AuthenticationState, app: AppUiState) {
        if (authentication != AuthenticationState.Authenticated || app.route != AppRoute.AUTHENTICATED_SHELL) {
            openingState.value = null
            return
        }
        val repository = application.mobilePosRepository.state
        val profile = repository.selectedProfile ?: run {
            openingState.value = null
            return
        }
        val cashier = repository.bootstrap?.user?.name ?: run {
            openingState.value = null
            return
        }
        if (openingViewModel?.belongsTo(cashier, profile.name) != true) {
            openingViewModel?.clear()
            openingViewModel = OpeningViewModel(cashier, profile, application.mobilePosRepository::openSession)
            openingFlow = OpeningFlowCoordinator(
                currentSession = { application.mobilePosRepository.currentSession(profile.name) },
                submitOpening = application.mobilePosRepository::openSession,
                refreshCapabilities = { application.appViewModel.refreshAfterOpeningCompletion() },
            )
            openingReconciliation = OpeningReconciliationRunner(
                flow = requireNotNull(openingFlow),
                dispatch = ::applicationScopeIo,
                isCurrent = ::isCurrentOpeningTerminal,
                onResult = ::completeOpeningReconciliation,
            )
        }
        openingState.value = if (repository.opening == null && repository.capabilities.openSession) {
            openingViewModel?.state?.value
        } else {
            null
        }
    }

    private fun synchronizeCustomerSearch(authentication: AuthenticationState) {
        val bootstrap = application.mobilePosRepository.state.bootstrap
        val profile = bootstrap?.selectedProfile
        val cashier = bootstrap?.user?.name
        if (authentication != AuthenticationState.Authenticated || profile == null || cashier == null) {
            customerSheetVisible.value = false
            application.customerSearchViewModel.clear()
            return
        }
        application.customerSearchViewModel.bind(CustomerSearchIdentity(cashier, profile.name, profile.customer))
    }

    private fun openSession() {
        applicationScopeIo {
            val execution = openingViewModel?.submit() ?: return@applicationScopeIo
            openingState.value = openingViewModel?.state?.value
            openingReconciliation?.immediate(execution)
        }
    }

    private fun synchronizeRecoveredOpening(state: com.rotiropi.pos_erpnext.recovery.RecoveryScreenState) {
        val terminal = state as? com.rotiropi.pos_erpnext.recovery.RecoveryScreenState.Terminal ?: return
        if (terminal.transactionId == handledOpeningTerminal) return
        val recovered = when (val result = terminal.result) {
            is com.rotiropi.pos_erpnext.recovery.RecoveryTerminalResult.Completed -> {
                if (result.operation != "Opening") return
                RecoveredOpeningTerminal.Completed(terminal.identity, terminal.generation, terminal.transactionId)
            }
            is com.rotiropi.pos_erpnext.recovery.RecoveryTerminalResult.Rejected ->
                RecoveredOpeningTerminal.Rejected(
                    terminal.identity,
                    terminal.generation,
                    terminal.transactionId,
                    result.code,
                )
        }
        openingReconciliation?.recovered(recovered)
    }

    private fun isCurrentOpeningTerminal(terminal: RecoveredOpeningTerminal): Boolean {
        val snapshot = application.authenticationOwner.snapshot
        return snapshot.state == AuthenticationState.Authenticated &&
            snapshot.generation == terminal.generation &&
            application.currentRecoveryIdentity() == terminal.identity
    }

    private fun completeOpeningReconciliation(transactionId: String, result: OpeningFlowResult) {
        if (result.destination == OpeningDestination.SHELL) {
            handledOpeningTerminal = transactionId
            openingState.value = null
            application.appViewModel.synchronizeRouteFromRepository()
            return
        }
        if (result.failure != null) {
            openingViewModel?.reconciliationFailed()
            openingState.value = openingViewModel?.state?.value
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
