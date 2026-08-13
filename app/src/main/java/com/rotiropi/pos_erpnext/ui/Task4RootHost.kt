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
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.auth.AuthenticationState
import com.rotiropi.pos_erpnext.databinding.Task4RootBinding
import com.rotiropi.pos_erpnext.ui.navigation.PosShell
import com.rotiropi.pos_erpnext.ui.opening.OpeningViewModel
import com.rotiropi.pos_erpnext.ui.opening.OpeningRoutingAuthority
import com.rotiropi.pos_erpnext.ui.opening.OpeningRoutingDestination
import com.rotiropi.pos_erpnext.ui.opening.OpeningRoutingGate
import com.rotiropi.pos_erpnext.ui.opening.OpeningRoutingResult
import com.rotiropi.pos_erpnext.ui.opening.RecoveredOpeningTerminal
import com.rotiropi.pos_erpnext.ui.profile.ProfileSelectionUiState
import com.rotiropi.pos_erpnext.ui.settings.PosThemeMode
import com.rotiropi.pos_erpnext.ui.settings.ThemePreferences
import com.rotiropi.pos_erpnext.ui.settings.applyPosLanguage
import com.rotiropi.pos_erpnext.ui.theme.PosTheme
import com.rotiropi.pos_erpnext.ui.customer.CustomerSearchIdentity
import com.rotiropi.pos_erpnext.ui.customer.CustomerSelection
import com.rotiropi.pos_erpnext.ui.cashier.CashierIdentity
import com.rotiropi.pos_erpnext.ui.history.HistoryIdentity
import com.rotiropi.pos_erpnext.ui.closing.ClosingAuthority
import com.rotiropi.pos_erpnext.ui.closing.ClosingRecoverySynchronizer
import com.rotiropi.pos_erpnext.ui.navigation.PosDestination
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
    private val openingState = MutableStateFlow<com.rotiropi.pos_erpnext.ui.opening.OpeningUiState?>(
        if (application.authenticationOwner.snapshot.state == AuthenticationState.Authenticated) {
            com.rotiropi.pos_erpnext.ui.opening.OpeningUiState(reconciling = true)
        } else {
            null
        },
    )
    private val customerSheetVisible = MutableStateFlow(false)
    private val cashierCartVisible = MutableStateFlow(false)
    private var openingViewModel: OpeningViewModel? = null
    private var openingGate: OpeningRoutingGate? = null
    private var openingAuthority: OpeningRoutingAuthority? = null
    private var openingDestination: OpeningRoutingDestination? = null
    private var handledOpeningTerminal: String? = null
    private val observedOpeningTerminals = mutableSetOf<String>()
    private val observedReturnTerminals = mutableSetOf<String>()
    private var serverClosingReference: String? = null
    private var recoveredClosingTransactionId: String? = null
    private var observedClosingAuthority: ClosingAuthority? = null
    private val closingRecovery = ClosingRecoverySynchronizer(
        application.closingViewModel::recover,
    )
    private var historyIdentity: HistoryIdentity? = null
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
            openingGate = null
            openingAuthority = null
            openingDestination = null
            openingState.value = null
            cashierCartVisible.value = false
            handledOpeningTerminal = null
            synchronized(observedOpeningTerminals) { observedOpeningTerminals.clear() }
            synchronized(observedReturnTerminals) { observedReturnTerminals.clear() }
            closingRecovery.synchronize(null, null)
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

    private fun recoverManualClosing(transactionId: String) {
        val profile = application.mobilePosRepository.state.selectedProfile?.name ?: return
        applicationScopeIo {
            application.recoveryCoordinator.recoverManualClosing(transactionId, profile) {
                application.mobilePosRepository.state.selectedProfile?.name
            }
        }
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
            val cashier = application.cashierViewModel.state.collectAsState().value
            val history = application.historyViewModel.state.collectAsState().value
            val saleDetail = application.saleDetailViewModel.state.collectAsState().value
            val returning = application.returnViewModel.state.collectAsState().value
            val closing = application.closingViewModel.state.collectAsState().value
            PosTheme(darkTheme = darkTheme, accent = selection.accent) {
                PosShell(
                    authenticationOwner = application.authenticationOwner,
                    serverOrigin = application.oauthConfig.canonicalOrigin,
                    onLogout = ::logout,
                    logoutResult = logoutResult.collectAsState().value,
                    recoveryState = recovery,
                    onAcknowledgeRecovery = ::acknowledgeRecovery,
                    onReauthenticateRecovery = application.authenticationOwner::beginAuthorization,
                    onRecoverManualClosing = ::recoverManualClosing,
                    openingState = opening,
                    onOpeningAmountChanged = { mode, amount ->
                        openingViewModel?.updateAmount(mode, amount)
                        openingState.value = openingViewModel?.state?.value
                    },
                    onOpenSession = ::openSession,
                    startDestination = PosDestination.CASHIER,
                    cashierState = cashier,
                    onCashierQueryChanged = application.cashierViewModel::onQueryChanged,
                    onCashierBarcodeChanged = application.cashierViewModel::onBarcodeChanged,
                    onCashierBarcodeSubmit = application.cashierViewModel::onBarcodeSubmit,
                    onLoadMoreCatalog = application.cashierViewModel::loadMore,
                    onCashierCategorySelected = application.cashierViewModel::onCategorySelected,
                    onCashierProductSelected = application.cashierViewModel::onProductSelected,
                    onOpenCashierCart = { cashierCartVisible.value = true },
                    onDismissCashierCart = { cashierCartVisible.value = false },
                    cashierCartVisible = cashierCartVisible.collectAsState().value,
                    onDecreaseCashierQuantity = application.cashierViewModel::onDecreaseQuantity,
                    onIncreaseCashierQuantity = application.cashierViewModel::onIncreaseQuantity,
                    onEditCashierQuantity = application.cashierViewModel::onQuantityEdited,
                    onRemoveCashierLine = application.cashierViewModel::onRemoveLine,
                    onCashierRetry = application.cashierViewModel::retry,
                    onOpenCheckout = application.cashierViewModel::onOpenCheckout,
                    onUpdatePaymentAmount = application.cashierViewModel::onUpdatePaymentAmount,
                    onSubmitPayment = application.cashierViewModel::onSubmitPayment,
                    onCloseReceipt = application.cashierViewModel::closeReceipt,
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
                    historyState = history,
                    saleDetailState = saleDetail,
                    returnState = returning,
                    onHistoryQueryChanged = application.historyViewModel::onQueryChanged,
                    onHistoryLoadMore = application.historyViewModel::loadMore,
                    onHistoryRetry = application.historyViewModel::retry,
                    onHistorySaleSelected = application.saleDetailViewModel::load,
                    onStartReturn = { sale ->
                        val contract = sale.return_contract ?: return@PosShell
                        application.returnViewModel.show(
                            sourceName = sale.summary.name,
                            rows = sale.items.mapNotNull { it.returnability },
                            policy = com.rotiropi.pos_erpnext.ui.returning.ReturnQuantityPolicy(
                                contract.quantity_policy.decimal_places,
                                contract.quantity_policy.minimum,
                                contract.quantity_policy.maximum,
                                contract.quantity_policy.api_syntax,
                                contract.quantity_policy.rounding,
                                contract.quantity_policy.policy_version,
                            ),
                            allowedRefundModes = contract.allowed_refund_modes.map { it.mode_of_payment },
                            refundModeRequired = contract.refund_mode_required,
                        )
                    },
                    onReturnReasonChanged = application.returnViewModel::updateReason,
                    onReturnQuantityChanged = application.returnViewModel::updateQuantity,
                    onReturnRefundModeChanged = application.returnViewModel::updateRefundMode,
                    onReturnQuote = application.returnViewModel::requestQuote,
                    onReturnSubmit = application.returnViewModel::submit,
                    onCloseReturnReceipt = application.returnViewModel::closeReceipt,
                    closingAvailable = application.mobilePosRepository.state.capabilities.closeSession &&
                        application.mobilePosRepository.state.opening != null,
                    closingState = closing,
                    onOpenClosing = {
                        application.mobilePosRepository.state.selectedProfile?.name
                            ?.let(application.closingViewModel::load)
                    },
                    onClosingAmountChanged = application.closingViewModel::updateCountedAmount,
                    onSubmitClosing = application.closingViewModel::submit,
                    onCheckClosingStatus = application.closingViewModel::checkStatus,
                    onRetryClosingPreview = application.closingViewModel::load,
                    onCloseClosingReceipt = application.closingViewModel::closeReceipt,
                    themeMode = selection.mode,
                    accent = selection.accent,
                    language = selection.language,
                    onThemeModeSelected = { mode ->
                        selection = selection.copy(mode = mode)
                        preferences.writeMode(mode)
                    },
                    onAccentSelected = { accent ->
                        selection = selection.copy(accent = accent)
                        preferences.writeAccent(accent)
                    },
                    onLanguageSelected = { language ->
                        selection = selection.copy(language = language)
                        preferences.writeLanguage(language)
                        // Recreates the activity with the new locale, which is how the
                        // already-composed screens pick up the new resources.
                        applyPosLanguage(language)
                    },
                )
            }
        }
        activity.lifecycleScope.launch {
            application.closingViewModel.setForeground(false)
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                application.closingViewModel.setForeground(true)
                try {
                    combine(
                    application.authenticationOwner.state,
                    application.appViewModel.state,
                    application.profileSelectionViewModel.state,
                    application.recoveryCoordinator.uiState,
                ) { authentication, app, profile, recovery ->
                    RootState(authentication, app, profile, recovery)
                }.collect { (authentication, app, profile, recovery) ->
                        application.appViewModel.onAuthenticationStateChanged(authentication)
                        val currentApp = application.appViewModel.state.value
                        val snapshot = application.authenticationOwner.snapshot
                        recoveryViewModel.onAuthenticationChanged(snapshot)
                        if (authentication != AuthenticationState.Authenticated) {
                            closingRecovery.synchronize(null, null)
                            serverClosingReference = null
                            recoveredClosingTransactionId = null
                            application.recoveryCoordinator.clearUiState()
                            application.profileSelectionViewModel.setRecoveryState(null, com.rotiropi.pos_erpnext.recovery.RecoveryUiState())
                        }
                        val repositoryState = application.mobilePosRepository.state
                        val closingAuthority = repositoryState.selectedProfile?.let { profile ->
                            repositoryState.bootstrap?.user?.name?.let { cashier ->
                                ClosingAuthority(
                                    cashier = cashier,
                                    posProfile = profile.name,
                                    authenticationGeneration = snapshot.generation,
                                    repositoryGeneration = application.mobilePosRepository.authorityGeneration,
                                )
                            }
                        }.takeIf { authentication == AuthenticationState.Authenticated }
                        if (closingAuthority != observedClosingAuthority) {
                            observedClosingAuthority = closingAuthority
                            serverClosingReference = null
                            recoveredClosingTransactionId = null
                        }
                        application.closingViewModel.synchronizeAuthority(closingAuthority)
                        if (authentication == AuthenticationState.Authenticated) {
                            closingRecovery.synchronize(
                                recovery.closingQueuedTransactionId,
                                closingAuthority,
                            )
                        }
                        val closingProjection = repositoryState.closing
                        val closingReference = closingProjection?.name?.takeIf {
                            closingProjection.status in setOf(
                                com.rotiropi.pos_erpnext.data.ClosingProjectionState.PROCESSING,
                                com.rotiropi.pos_erpnext.data.ClosingProjectionState.DRAFT,
                                com.rotiropi.pos_erpnext.data.ClosingProjectionState.QUEUED,
                            )
                        }
                        if (recovery.closingQueuedTransactionId == null &&
                            closingReference != null &&
                            closingReference != serverClosingReference
                        ) {
                            serverClosingReference = closingReference
                            application.closingViewModel.recoverProjection(closingReference)
                        }
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
                        synchronizeHistory(authentication)
                        synchronizeCashier(authentication)
                        synchronizeRecoveredOpening(recoveryViewModel.state.value)
                        synchronizeRecoveredReturn(recoveryViewModel.state.value)
                        synchronizeRecoveredClosing(recoveryViewModel.state.value)
                        controller.render(
                            authentication,
                            application.appViewModel.state.value,
                            application.profileSelectionViewModel.state.value,
                        )
                    }
                } finally {
                    application.closingViewModel.setForeground(false)
                }
            }
        }
    }

    private fun synchronizeOpeningFlow(authentication: AuthenticationState, app: AppUiState) {
        if (authentication != AuthenticationState.Authenticated || app.route != AppRoute.AUTHENTICATED_SHELL) {
            openingState.value = null
            openingDestination = null
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
        val authority = OpeningRoutingAuthority(
            cashier = cashier,
            posProfile = profile.name,
            authenticationGeneration = application.authenticationOwner.snapshot.generation,
            repositoryGeneration = application.mobilePosRepository.authorityGeneration,
        )
        if (authority != openingAuthority) {
            openingViewModel?.clear()
            openingViewModel = OpeningViewModel(cashier, profile, application.mobilePosRepository::openSession)
            openingGate = application.openingRoutingGate()
            openingAuthority = authority
            openingDestination = null
            handledOpeningTerminal = null
            synchronized(observedOpeningTerminals) { observedOpeningTerminals.clear() }
            openingState.value = openingViewModel?.state?.value?.copy(reconciling = true)
            application.cashierViewModel.clear()
            applicationScopeIo {
                val result = openingGate?.afterAuthentication(authority) ?: return@applicationScopeIo
                if (isCurrentOpeningAuthority(authority)) completeInitialOpeningReconciliation(result)
            }
            return
        }
        openingState.value = when (openingDestination) {
            null -> openingViewModel?.state?.value?.copy(reconciling = true)
            OpeningRoutingDestination.OPENING -> openingViewModel?.state?.value?.let { state ->
                if (repository.capabilities.openSession) state else state.copy(
                    unavailable = true,
                    canSubmit = false,
                    error = uiText(R.string.opening_error_profile_unavailable),
                )
            }
            OpeningRoutingDestination.CASHIER -> null
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

    private fun synchronizeCashier(authentication: AuthenticationState) {
        val repository = application.mobilePosRepository.state
        val profile = repository.selectedProfile
        val cashier = repository.bootstrap?.user?.name
        val opening = repository.opening
        if (authentication != AuthenticationState.Authenticated ||
            openingDestination != OpeningRoutingDestination.CASHIER ||
            !repository.capabilities.submitSale ||
            profile == null ||
            cashier == null ||
            opening == null ||
            opening.posProfile != profile.name ||
            opening.user != cashier ||
            opening.status != com.rotiropi.pos_erpnext.data.OpeningStatus.OPEN
        ) {
            cashierCartVisible.value = false
            application.cashierViewModel.clear()
            return
        }
        val selection = application.customerSearchViewModel.state.value.selection
        val customer = when (selection) {
            is CustomerSelection.WalkIn -> selection.customerId
            is CustomerSelection.Registered -> selection.customerId
            null -> profile.customer
        }
        application.cashierViewModel.bind(
            CashierIdentity(
                cashier = cashier,
                sessionName = opening.name,
                posProfile = profile.name,
                customer = customer,
                walkInCustomerName = (selection as? CustomerSelection.WalkIn)?.displayName?.takeIf { it.isNotBlank() },
                warehouse = profile.warehouse,
            ),
        )
    }

    private fun synchronizeHistory(authentication: AuthenticationState) {
        val bootstrap = application.mobilePosRepository.state.bootstrap
        val profile = bootstrap?.selectedProfile
        val cashier = bootstrap?.user?.name
        if (authentication != AuthenticationState.Authenticated || profile == null || cashier == null) {
            application.historyViewModel.clear()
            application.saleDetailViewModel.clear()
            application.returnViewModel.clear()
            historyIdentity = null
            return
        }
        val identity = HistoryIdentity(cashier, profile.name)
        if (historyIdentity != identity) {
            application.saleDetailViewModel.clear()
            application.returnViewModel.clear()
            historyIdentity = identity
        }
        application.historyViewModel.bind(identity)
    }

    private fun synchronizeRecoveredClosing(state: com.rotiropi.pos_erpnext.recovery.RecoveryScreenState) {
        val terminal = state as? com.rotiropi.pos_erpnext.recovery.RecoveryScreenState.Terminal ?: return
        if (terminal.identity != application.currentRecoveryIdentity()) return
        val completed = terminal.result as? com.rotiropi.pos_erpnext.recovery.RecoveryTerminalResult.Completed
            ?: return
        if (completed.operation != "Closing" ||
            terminal.transactionId == recoveredClosingTransactionId
        ) return
        recoveredClosingTransactionId = terminal.transactionId
        application.closingViewModel.recover(terminal.transactionId)
    }

    private fun synchronizeRecoveredReturn(state: com.rotiropi.pos_erpnext.recovery.RecoveryScreenState) {
        val terminal = state as? com.rotiropi.pos_erpnext.recovery.RecoveryScreenState.Terminal ?: return
        if (terminal.identity != application.currentRecoveryIdentity()) return
        val completed = terminal.result as? com.rotiropi.pos_erpnext.recovery.RecoveryTerminalResult.Completed
        val rejected = terminal.result as? com.rotiropi.pos_erpnext.recovery.RecoveryTerminalResult.Rejected
        if (rejected?.code == "RETURN_LIMIT_EXCEEDED") {
            application.returnViewModel.rejected(terminal.transactionId)
            return
        }
        if (completed?.operation != "Return") return
        if (!synchronized(observedReturnTerminals) { observedReturnTerminals.add(terminal.transactionId) }) return
        application.returnViewModel.completed(terminal.transactionId)
        applicationScopeIo {
            if (terminal.identity == application.currentRecoveryIdentity()) {
                application.mobilePosRepository.refreshCapabilities(
                    com.rotiropi.pos_erpnext.data.BootstrapRefreshTrigger.RETURN_COMPLETED,
                )
            }
        }
    }

    private fun openSession() {
        applicationScopeIo {
            val execution = openingViewModel?.submit() ?: return@applicationScopeIo
            openingState.value = openingViewModel?.state?.value
            val completed = execution as? com.rotiropi.pos_erpnext.recovery.RecoveryExecution.Completed ?: return@applicationScopeIo
            reconcileOpeningCompletion(completed.transactionId)
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
                ).takeIf { result.code == "SESSION_ALREADY_OPEN" } ?: return
        }
        if (isCurrentOpeningTerminal(recovered)) reconcileOpeningCompletion(recovered.transactionId)
    }

    private fun isCurrentOpeningTerminal(terminal: RecoveredOpeningTerminal): Boolean {
        val snapshot = application.authenticationOwner.snapshot
        return snapshot.state == AuthenticationState.Authenticated &&
            snapshot.generation == terminal.generation &&
            application.currentRecoveryIdentity() == terminal.identity
    }

    private fun isCurrentOpeningAuthority(authority: OpeningRoutingAuthority): Boolean {
        val snapshot = application.authenticationOwner.snapshot
        val repository = application.mobilePosRepository.state
        return snapshot.state == AuthenticationState.Authenticated &&
            snapshot.generation == authority.authenticationGeneration &&
            application.mobilePosRepository.authorityGeneration == authority.repositoryGeneration &&
            repository.selectedProfile?.name == authority.posProfile &&
            repository.bootstrap?.user?.name == authority.cashier
    }

    private fun reconcileOpeningCompletion(transactionId: String) {
        if (!synchronized(observedOpeningTerminals) { observedOpeningTerminals.add(transactionId) }) return
        val authority = openingAuthority ?: return
        applicationScopeIo {
            if (!isCurrentOpeningAuthority(authority)) return@applicationScopeIo
            val result = openingGate?.afterOpeningSucceeded(authority) ?: return@applicationScopeIo
            if (isCurrentOpeningAuthority(authority)) completeOpeningReconciliation(transactionId, result)
        }
    }

    private fun completeInitialOpeningReconciliation(result: OpeningRoutingResult) {
        openingDestination = result.destination
        if (result.failure != null) openingViewModel?.currentSessionFailed()
        openingState.value = when (result.destination) {
            OpeningRoutingDestination.OPENING -> openingViewModel?.state?.value?.let { state ->
                if (application.mobilePosRepository.state.capabilities.openSession) state else state.copy(
                    unavailable = true,
                    canSubmit = false,
                    error = uiText(R.string.opening_error_profile_unavailable),
                )
            }
            OpeningRoutingDestination.CASHIER -> null
        }
        application.appViewModel.synchronizeRouteFromRepository()
        if (result.destination == OpeningRoutingDestination.CASHIER) {
            synchronizeCashier(AuthenticationState.Authenticated)
        }
    }

    private fun completeOpeningReconciliation(transactionId: String, result: OpeningRoutingResult) {
        if (result.destination == OpeningRoutingDestination.CASHIER) {
            handledOpeningTerminal = transactionId
            openingDestination = OpeningRoutingDestination.CASHIER
            openingState.value = null
            application.appViewModel.synchronizeRouteFromRepository()
            synchronizeCashier(AuthenticationState.Authenticated)
            return
        }
        openingDestination = OpeningRoutingDestination.OPENING
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
