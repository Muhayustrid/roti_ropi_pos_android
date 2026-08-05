package com.rotiropi.pos_erpnext.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rotiropi.pos_erpnext.auth.AuthenticationOwner
import com.rotiropi.pos_erpnext.auth.AuthenticationState
import com.rotiropi.pos_erpnext.session.LogoutResult
import com.rotiropi.pos_erpnext.ui.auth.SignInScreen
import com.rotiropi.pos_erpnext.ui.cashier.CashierScreen
import com.rotiropi.pos_erpnext.ui.cashier.CashierUiState
import com.rotiropi.pos_erpnext.ui.customer.CustomerSearchUiState
import com.rotiropi.pos_erpnext.ui.customer.CustomerRecord
import com.rotiropi.pos_erpnext.ui.components.RootNavigationBar
import com.rotiropi.pos_erpnext.ui.dashboard.DashboardScreen
import com.rotiropi.pos_erpnext.ui.dashboard.DashboardUiState
import com.rotiropi.pos_erpnext.ui.demo.PosDemoStates
import com.rotiropi.pos_erpnext.ui.opening.OpeningScreen
import com.rotiropi.pos_erpnext.ui.opening.OpeningUiState
import com.rotiropi.pos_erpnext.ui.products.ProductsScreen
import com.rotiropi.pos_erpnext.ui.products.ProductsUiState
import com.rotiropi.pos_erpnext.ui.reports.ReportsScreen
import com.rotiropi.pos_erpnext.ui.reports.ReportsUiState
import com.rotiropi.pos_erpnext.ui.settings.MoreScreen
import com.rotiropi.pos_erpnext.ui.settings.MoreUiState
import com.rotiropi.pos_erpnext.ui.settings.PosThemeMode
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

@Composable
fun PosShell(
    themeMode: PosThemeMode = PosThemeMode.SYSTEM,
    accent: PosAccent = PosAccent.BLUE,
    onThemeModeSelected: (PosThemeMode) -> Unit = {},
    onAccentSelected: (PosAccent) -> Unit = {},
    modifier: Modifier = Modifier,
    authenticationOwner: AuthenticationOwner? = null,
    onLogout: (() -> LogoutResult)? = null,
    logoutResult: LogoutResult? = null,
    recoveryState: com.rotiropi.pos_erpnext.recovery.RecoveryScreenState = com.rotiropi.pos_erpnext.recovery.RecoveryScreenState.Hidden,
    onAcknowledgeRecovery: (String) -> Unit = {},
    onReauthenticateRecovery: () -> Unit = {},
    openingState: OpeningUiState? = null,
    onOpeningAmountChanged: (String, String) -> Unit = { _, _ -> },
    onOpenSession: () -> Unit = {},
    cashierState: CashierUiState = CashierUiState.Unavailable,
    onCashierQueryChanged: (String) -> Unit = {},
    onCashierBarcodeChanged: (String) -> Unit = {},
    onCashierBarcodeSubmit: () -> Unit = {},
    onLoadMoreCatalog: () -> Unit = {},
    onCashierCategorySelected: (com.rotiropi.pos_erpnext.ui.cashier.CashierCategory) -> Unit = {},
    onCashierProductSelected: (com.rotiropi.pos_erpnext.ui.cashier.CashierProduct) -> Unit = {},
    onOpenCashierCart: () -> Unit = {},
    onDismissCashierCart: () -> Unit = {},
    cashierCartVisible: Boolean = false,
    onDecreaseCashierQuantity: (com.rotiropi.pos_erpnext.ui.cashier.CartLine) -> Unit = {},
    onIncreaseCashierQuantity: (com.rotiropi.pos_erpnext.ui.cashier.CartLine) -> Unit = {},
    onEditCashierQuantity: (com.rotiropi.pos_erpnext.ui.cashier.CartLine, String) -> Unit = { _, _ -> },
    onRemoveCashierLine: (com.rotiropi.pos_erpnext.ui.cashier.CartLine) -> Unit = {},
    onCashierRetry: () -> Unit = {},
    customerState: CustomerSearchUiState? = null,
    customerSheetVisible: Boolean = false,
    onOpenCustomerSheet: () -> Unit = {},
    onDismissCustomerSheet: () -> Unit = {},
    onCustomerQueryChanged: (String) -> Unit = {},
    onWalkInNameChanged: (String) -> Unit = {},
    onSelectWalkIn: () -> Unit = {},
    onSelectRegistered: (CustomerRecord) -> Unit = {},
    onCustomerRetry: () -> Unit = {},
    onCustomerLoadMore: () -> Unit = {},
) {
    val authState = authenticationOwner?.state?.collectAsState()?.value
    if (authenticationOwner != null && authState != AuthenticationState.Authenticated) {
        SignInScreen(
            onSignInClick = authenticationOwner::beginAuthorization,
            modifier = modifier,
            errorMessage = (authState as? AuthenticationState.Error)?.reason?.name,
            signingIn = authState == AuthenticationState.Authorizing,
        )
        return
    }

    if (openingState != null) {
        OpeningScreen(
            state = openingState,
            onAmountChanged = onOpeningAmountChanged,
            onSubmit = onOpenSession,
            modifier = modifier,
            recoveryState = recoveryState,
            onAcknowledgeRecovery = {
                (recoveryState as? com.rotiropi.pos_erpnext.recovery.RecoveryScreenState.Terminal)
                    ?.transactionId
                    ?.let(onAcknowledgeRecovery)
            },
            onReauthenticateRecovery = onReauthenticateRecovery,
        )
        return
    }

    AuthenticatedPosShell(
        themeMode = themeMode,
        accent = accent,
        onThemeModeSelected = onThemeModeSelected,
        onAccentSelected = onAccentSelected,
        onLogout = onLogout ?: authenticationOwner?.let { owner ->
            { owner.logout(); LogoutResult.LoggedOut }
        } ?: { LogoutResult.LoggedOut },
        logoutResult = logoutResult,
        recoveryState = recoveryState,
        onAcknowledgeRecovery = onAcknowledgeRecovery,
        onReauthenticateRecovery = onReauthenticateRecovery,
        logoutVisible = authenticationOwner != null,
        cashierState = cashierState,
        onCashierQueryChanged = onCashierQueryChanged,
        onCashierBarcodeChanged = onCashierBarcodeChanged,
        onCashierBarcodeSubmit = onCashierBarcodeSubmit,
        onLoadMoreCatalog = onLoadMoreCatalog,
        onCashierCategorySelected = onCashierCategorySelected,
        onCashierProductSelected = onCashierProductSelected,
        onOpenCashierCart = onOpenCashierCart,
        onDismissCashierCart = onDismissCashierCart,
        cashierCartVisible = cashierCartVisible,
        onDecreaseCashierQuantity = onDecreaseCashierQuantity,
        onIncreaseCashierQuantity = onIncreaseCashierQuantity,
        onEditCashierQuantity = onEditCashierQuantity,
        onRemoveCashierLine = onRemoveCashierLine,
        onCashierRetry = onCashierRetry,
        customerState = customerState,
        customerSheetVisible = customerSheetVisible,
        onOpenCustomerSheet = onOpenCustomerSheet,
        onDismissCustomerSheet = onDismissCustomerSheet,
        onCustomerQueryChanged = onCustomerQueryChanged,
        onWalkInNameChanged = onWalkInNameChanged,
        onSelectWalkIn = onSelectWalkIn,
        onSelectRegistered = onSelectRegistered,
        onCustomerRetry = onCustomerRetry,
        onCustomerLoadMore = onCustomerLoadMore,
        modifier = modifier,
    )
}

@Composable
private fun AuthenticatedPosShell(
    themeMode: PosThemeMode,
    accent: PosAccent,
    onThemeModeSelected: (PosThemeMode) -> Unit,
    onAccentSelected: (PosAccent) -> Unit,
    onLogout: () -> LogoutResult,
    logoutResult: LogoutResult?,
    recoveryState: com.rotiropi.pos_erpnext.recovery.RecoveryScreenState,
    onAcknowledgeRecovery: (String) -> Unit,
    onReauthenticateRecovery: () -> Unit,
    logoutVisible: Boolean,
    cashierState: CashierUiState,
    onCashierQueryChanged: (String) -> Unit,
    onCashierBarcodeChanged: (String) -> Unit,
    onCashierBarcodeSubmit: () -> Unit,
    onLoadMoreCatalog: () -> Unit,
    onCashierCategorySelected: (com.rotiropi.pos_erpnext.ui.cashier.CashierCategory) -> Unit,
    onCashierProductSelected: (com.rotiropi.pos_erpnext.ui.cashier.CashierProduct) -> Unit,
    onOpenCashierCart: () -> Unit,
    onDismissCashierCart: () -> Unit,
    cashierCartVisible: Boolean,
    onDecreaseCashierQuantity: (com.rotiropi.pos_erpnext.ui.cashier.CartLine) -> Unit,
    onIncreaseCashierQuantity: (com.rotiropi.pos_erpnext.ui.cashier.CartLine) -> Unit,
    onEditCashierQuantity: (com.rotiropi.pos_erpnext.ui.cashier.CartLine, String) -> Unit,
    onRemoveCashierLine: (com.rotiropi.pos_erpnext.ui.cashier.CartLine) -> Unit,
    onCashierRetry: () -> Unit,
    customerState: CustomerSearchUiState?,
    customerSheetVisible: Boolean,
    onOpenCustomerSheet: () -> Unit,
    onDismissCustomerSheet: () -> Unit,
    onCustomerQueryChanged: (String) -> Unit,
    onWalkInNameChanged: (String) -> Unit,
    onSelectWalkIn: () -> Unit,
    onSelectRegistered: (CustomerRecord) -> Unit,
    onCustomerRetry: () -> Unit,
    onCustomerLoadMore: () -> Unit,
    modifier: Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedDestination = PosDestination.entries.firstOrNull {
        it.route == backStackEntry?.destination?.route
    } ?: PosDestination.HOME
    var demoData by rememberSaveable { mutableStateOf(false) }
    val demoActive = PosDemoStates.supported && demoData

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val layoutMode = posLayoutModeForWidth(maxWidth.value.toInt())
        Scaffold(
            modifier = Modifier.testTag("shell-${layoutMode.name.lowercase()}"),
            bottomBar = {
                RootNavigationBar(
                    selectedDestination = selectedDestination,
                    onDestinationSelected = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            },
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                NavHost(
                    navController = navController,
                    startDestination = PosDestination.HOME.route,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 960.dp)
                        .padding(
                            horizontal = if (layoutMode == PosLayoutMode.EXPANDED) {
                                PosDimensions.screenPadding * 2
                            } else {
                                0.dp
                            }
                        ),
                ) {
                    composable(PosDestination.HOME.route) {
                        DashboardScreen(
                            state = if (demoActive) PosDemoStates.dashboard else DashboardUiState.Unavailable,
                            layoutMode = layoutMode,
                            modifier = Modifier.testTag("destination-content-home"),
                        )
                    }
                    composable(PosDestination.PRODUCTS.route) {
                        ProductsScreen(
                            state = if (demoActive) PosDemoStates.products else ProductsUiState.Unavailable,
                            layoutMode = layoutMode,
                            modifier = Modifier.testTag("destination-content-products"),
                        )
                    }
                    composable(PosDestination.CASHIER.route) {
                        CashierScreen(
                            state = if (demoActive) PosDemoStates.cashier else cashierState,
                            layoutMode = layoutMode,
                            modifier = Modifier.testTag("destination-content-cashier"),
                            cartVisible = cashierCartVisible,
                            onQueryChange = onCashierQueryChanged,
                            onBarcodeChange = onCashierBarcodeChanged,
                            onBarcodeSubmit = onCashierBarcodeSubmit,
                            onLoadMoreCatalog = onLoadMoreCatalog,
                            onCategorySelected = onCashierCategorySelected,
                            onProductSelected = onCashierProductSelected,
                            onOpenCart = onOpenCashierCart,
                            onDismissCart = onDismissCashierCart,
                            onDecreaseQuantity = onDecreaseCashierQuantity,
                            onIncreaseQuantity = onIncreaseCashierQuantity,
                            onEditQuantity = onEditCashierQuantity,
                            onRemoveLine = onRemoveCashierLine,
                            onRetry = onCashierRetry,
                            customerState = if (demoActive) null else customerState,
                            customerSheetVisible = customerSheetVisible,
                            onOpenCustomerSheet = onOpenCustomerSheet,
                            onDismissCustomerSheet = onDismissCustomerSheet,
                            onCustomerQueryChanged = onCustomerQueryChanged,
                            onWalkInNameChanged = onWalkInNameChanged,
                            onSelectWalkIn = onSelectWalkIn,
                            onSelectRegistered = onSelectRegistered,
                            onCustomerRetry = onCustomerRetry,
                            onCustomerLoadMore = onCustomerLoadMore,
                        )
                    }
                    composable(PosDestination.REPORTS.route) {
                        ReportsScreen(
                            state = if (demoActive) PosDemoStates.reports else ReportsUiState.Unavailable,
                            layoutMode = layoutMode,
                            modifier = Modifier.testTag("destination-content-reports"),
                        )
                    }
                    composable(PosDestination.MORE.route) {
                        MoreScreen(
                            state = MoreUiState(
                                outletLabel = if (demoActive) PosDemoStates.outletLabel else null,
                                userSessionLabel = if (demoActive) PosDemoStates.userSessionLabel else null,
                                themeMode = themeMode,
                                accent = accent,
                                demoData = demoActive,
                                logoutMessage = (logoutResult as? LogoutResult.Blocked)?.let {
                                    "Sign out blocked: ${it.cashier} has ${it.state.name.lowercase()} recovery."
                                },
                                recovery = recoveryState,
                            ),
                            layoutMode = layoutMode,
                            modifier = Modifier.testTag("destination-content-more"),
                            demoToggleVisible = PosDemoStates.supported,
                            logoutVisible = logoutVisible,
                            onThemeModeSelected = onThemeModeSelected,
                            onAccentSelected = onAccentSelected,
                            onDemoDataToggled = { demoData = it },
                            onLogout = { onLogout() },
                            onAcknowledgeRecovery = onAcknowledgeRecovery,
                            onReauthenticateRecovery = onReauthenticateRecovery,
                        )
                    }
                }
            }
        }
    }
}
