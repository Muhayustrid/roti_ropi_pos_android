package com.rotiropi.pos_erpnext.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.auth.AuthenticationOwner
import com.rotiropi.pos_erpnext.auth.AuthenticationState
import com.rotiropi.pos_erpnext.session.LogoutResult
import com.rotiropi.pos_erpnext.ui.auth.SignInScreen
import com.rotiropi.pos_erpnext.ui.auth.signInErrorMessage
import com.rotiropi.pos_erpnext.ui.LocalPosWindow
import com.rotiropi.pos_erpnext.ui.PosWindow
import com.rotiropi.pos_erpnext.ui.cashier.CashierScreen
import com.rotiropi.pos_erpnext.ui.cashier.CashierUiState
import com.rotiropi.pos_erpnext.ui.customer.CustomerSearchUiState
import com.rotiropi.pos_erpnext.ui.customer.CustomerRecord
import com.rotiropi.pos_erpnext.ui.components.RootNavigationBar
import com.rotiropi.pos_erpnext.ui.opening.OpeningScreen
import com.rotiropi.pos_erpnext.ui.opening.OpeningUiState
import com.rotiropi.pos_erpnext.ui.history.HistoryScreen
import com.rotiropi.pos_erpnext.ui.history.HistoryUiState
import com.rotiropi.pos_erpnext.ui.history.SaleDetailScreen
import com.rotiropi.pos_erpnext.ui.history.SaleDetailUiState
import com.rotiropi.pos_erpnext.ui.returning.ReturnScreen
import com.rotiropi.pos_erpnext.ui.returning.ReturnUiState
import com.rotiropi.pos_erpnext.ui.closing.ClosingScreen
import com.rotiropi.pos_erpnext.ui.closing.ClosingUiState
import com.rotiropi.pos_erpnext.data.api.SaleDetailDto
import com.rotiropi.pos_erpnext.ui.settings.MoreScreen
import com.rotiropi.pos_erpnext.ui.settings.MoreUiState
import com.rotiropi.pos_erpnext.ui.settings.PosLanguage
import com.rotiropi.pos_erpnext.ui.settings.PosThemeMode
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions
import com.rotiropi.pos_erpnext.ui.theme.WarmCommerceTheme

@Composable
fun PosShell(
    themeMode: PosThemeMode = PosThemeMode.SYSTEM,
    accent: PosAccent = PosAccent.BLUE,
    language: PosLanguage = PosLanguage.DEFAULT,
    onThemeModeSelected: (PosThemeMode) -> Unit = {},
    onAccentSelected: (PosAccent) -> Unit = {},
    onLanguageSelected: (PosLanguage) -> Unit = {},
    modifier: Modifier = Modifier,
    authenticationOwner: AuthenticationOwner? = null,
    serverOrigin: String? = null,
    onLogout: (() -> LogoutResult)? = null,
    logoutResult: LogoutResult? = null,
    recoveryState: com.rotiropi.pos_erpnext.recovery.RecoveryScreenState = com.rotiropi.pos_erpnext.recovery.RecoveryScreenState.Hidden,
    onAcknowledgeRecovery: (String) -> Unit = {},
    onReauthenticateRecovery: () -> Unit = {},
    onRecoverManualClosing: (String) -> Unit = {},
    openingState: OpeningUiState? = null,
    startDestination: PosDestination = PosDestination.CASHIER,
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
    onOpenCheckout: () -> Unit = {},
    onUpdatePaymentAmount: (String, String) -> Unit = { _, _ -> },
    onSubmitPayment: () -> Unit = {},
    onCloseReceipt: () -> Unit = {},
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
    historyState: HistoryUiState = HistoryUiState.Unavailable,
    saleDetailState: SaleDetailUiState = SaleDetailUiState.Unavailable,
    returnState: ReturnUiState = ReturnUiState.Unavailable,
    onHistoryQueryChanged: (String) -> Unit = {},
    onHistoryLoadMore: () -> Unit = {},
    onHistoryRetry: () -> Unit = {},
    onHistorySaleSelected: (String) -> Unit = {},
    onStartReturn: (SaleDetailDto) -> Unit = {},
    onReturnReasonChanged: (String) -> Unit = {},
    onReturnQuantityChanged: (String, String) -> Unit = { _, _ -> },
    onReturnRefundModeChanged: (String?) -> Unit = {},
    onReturnQuote: () -> Unit = {},
    onReturnSubmit: () -> Unit = {},
    onCloseReturnReceipt: () -> Unit = {},
    closingAvailable: Boolean = false,
    closingState: ClosingUiState = ClosingUiState.Unavailable,
    onOpenClosing: () -> Unit = {},
    onClosingAmountChanged: (String, String) -> Unit = { _, _ -> },
    onSubmitClosing: () -> Unit = {},
    onCheckClosingStatus: () -> Unit = {},
    onRetryClosingPreview: (String) -> Unit = {},
    onCloseClosingReceipt: () -> Boolean = { true },
) {
    val authState = authenticationOwner?.state?.collectAsState()?.value
    if (authenticationOwner != null && authState != AuthenticationState.Authenticated) {
        WarmCommerceTheme {
            SignInScreen(
                onSignInClick = authenticationOwner::beginAuthorization,
                modifier = modifier,
                errorMessage = (authState as? AuthenticationState.Error)?.reason
                    ?.let { stringResource(signInErrorMessage(it)) },
                signingIn = authState == AuthenticationState.Authorizing,
                serverOrigin = serverOrigin,
            )
        }
        return
    }

    if (openingState != null) {
        WarmCommerceTheme {
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
                onRecoverClosing = {
                    (recoveryState as? com.rotiropi.pos_erpnext.recovery.RecoveryScreenState.ManualRecovery)
                        ?.takeIf { it.canRecoverClosing }
                        ?.transactionId
                        ?.let(onRecoverManualClosing)
                },
            )
        }
        return
    }

    AuthenticatedPosShell(
        themeMode = themeMode,
        accent = accent,
        language = language,
        onThemeModeSelected = onThemeModeSelected,
        onAccentSelected = onAccentSelected,
        onLanguageSelected = onLanguageSelected,
        onLogout = onLogout ?: authenticationOwner?.let { owner ->
            { owner.logout(); LogoutResult.LoggedOut }
        } ?: { LogoutResult.LoggedOut },
        logoutResult = logoutResult,
        recoveryState = recoveryState,
        onAcknowledgeRecovery = onAcknowledgeRecovery,
        onReauthenticateRecovery = onReauthenticateRecovery,
        onRecoverManualClosing = onRecoverManualClosing,
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
        onOpenCheckout = onOpenCheckout,
        onUpdatePaymentAmount = onUpdatePaymentAmount,
        onSubmitPayment = onSubmitPayment,
        onCloseReceipt = onCloseReceipt,
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
        historyState = historyState,
        saleDetailState = saleDetailState,
        returnState = returnState,
        onHistoryQueryChanged = onHistoryQueryChanged,
        onHistoryLoadMore = onHistoryLoadMore,
        onHistoryRetry = onHistoryRetry,
        onHistorySaleSelected = onHistorySaleSelected,
        onStartReturn = onStartReturn,
        onReturnReasonChanged = onReturnReasonChanged,
        onReturnQuantityChanged = onReturnQuantityChanged,
        onReturnRefundModeChanged = onReturnRefundModeChanged,
        onReturnQuote = onReturnQuote,
        onReturnSubmit = onReturnSubmit,
        onCloseReturnReceipt = onCloseReturnReceipt,
        closingAvailable = closingAvailable,
        closingState = closingState,
        onOpenClosing = onOpenClosing,
        onClosingAmountChanged = onClosingAmountChanged,
        onSubmitClosing = onSubmitClosing,
        onCheckClosingStatus = onCheckClosingStatus,
        onRetryClosingPreview = onRetryClosingPreview,
        onCloseClosingReceipt = onCloseClosingReceipt,
        startDestination = startDestination,
        modifier = modifier,
    )
}

@Composable
private fun AuthenticatedPosShell(
    themeMode: PosThemeMode,
    accent: PosAccent,
    language: PosLanguage,
    onThemeModeSelected: (PosThemeMode) -> Unit,
    onAccentSelected: (PosAccent) -> Unit,
    onLanguageSelected: (PosLanguage) -> Unit,
    onLogout: () -> LogoutResult,
    logoutResult: LogoutResult?,
    recoveryState: com.rotiropi.pos_erpnext.recovery.RecoveryScreenState,
    onAcknowledgeRecovery: (String) -> Unit,
    onReauthenticateRecovery: () -> Unit,
    onRecoverManualClosing: (String) -> Unit,
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
    onOpenCheckout: () -> Unit,
    onUpdatePaymentAmount: (String, String) -> Unit,
    onSubmitPayment: () -> Unit,
    onCloseReceipt: () -> Unit,
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
    historyState: HistoryUiState,
    saleDetailState: SaleDetailUiState,
    returnState: ReturnUiState,
    onHistoryQueryChanged: (String) -> Unit,
    onHistoryLoadMore: () -> Unit,
    onHistoryRetry: () -> Unit,
    onHistorySaleSelected: (String) -> Unit,
    onStartReturn: (SaleDetailDto) -> Unit,
    onReturnReasonChanged: (String) -> Unit,
    onReturnQuantityChanged: (String, String) -> Unit,
    onReturnRefundModeChanged: (String?) -> Unit,
    onReturnQuote: () -> Unit,
    onReturnSubmit: () -> Unit,
    onCloseReturnReceipt: () -> Unit,
    closingAvailable: Boolean,
    closingState: ClosingUiState,
    onOpenClosing: () -> Unit,
    onClosingAmountChanged: (String, String) -> Unit,
    onSubmitClosing: () -> Unit,
    onCheckClosingStatus: () -> Unit,
    onRetryClosingPreview: (String) -> Unit,
    onCloseClosingReceipt: () -> Boolean,
    startDestination: PosDestination,
    modifier: Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val selectedDestination = PosDestination.entries.firstOrNull { it.route == route }
        ?: parentDestinationOf(route)
    val closingTerminal = closingState is ClosingUiState.Receipt ||
        closingState is ClosingUiState.Failed

    LaunchedEffect(closingTerminal, route) {
        if (closingTerminal && route != null && route != "closing") {
            navController.navigate("closing") {
                launchSingleTop = true
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val layoutMode = posLayoutModeForWidth(maxWidth.value.toInt())
        val posWindow = PosWindow(width = maxWidth, height = maxHeight)
        CompositionLocalProvider(LocalPosWindow provides posWindow) {
            Scaffold(
                modifier = Modifier.testTag("shell-${layoutMode.name.lowercase()}"),
                bottomBar = {
                    RootNavigationBar(
                        selectedDestination = selectedDestination,
                        onDestinationSelected = { destination ->
                            if (destination == selectedDestination && destination.route != route) {
                                // Already on this tab, but inside one of its child routes.
                                // Re-tapping a tab returns to that tab's root.
                                navController.popBackStack(destination.route, false)
                            } else {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
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
                        startDestination = startDestination.route,
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
                        composable(PosDestination.CASHIER.route) {
                            CashierScreen(
                                state = cashierState,
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
                                onOpenCheckout = onOpenCheckout,
                                onUpdatePaymentAmount = onUpdatePaymentAmount,
                                onSubmitPayment = onSubmitPayment,
                                onCloseReceipt = onCloseReceipt,
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
                            )
                        }
                        composable(PosDestination.HISTORY.route) {
                            HistoryScreen(historyState, onHistoryQueryChanged, { name -> onHistorySaleSelected(name); navController.navigate("sale/$name") }, onHistoryLoadMore, onHistoryRetry, Modifier.testTag("destination-content-history"))
                        }
                        composable("sale/{name}") {
                            SaleDetailScreen(saleDetailState, { sale -> onStartReturn(sale); navController.navigate("return/${sale.summary.name}") }, Modifier.testTag("destination-content-sale-detail"))
                        }
                        composable("return/{name}") {
                            ReturnScreen(returnState, onReturnReasonChanged, onReturnQuantityChanged, onReturnRefundModeChanged, onReturnQuote, onReturnSubmit, { onCloseReturnReceipt(); navController.popBackStack("history", false) }, Modifier.testTag("destination-content-return"))
                        }
                        composable(PosDestination.MORE.route) {
                            MoreScreen(
                                state = MoreUiState(
                                    outletLabel = null,
                                    userSessionLabel = null,
                                    themeMode = themeMode,
                                    accent = accent,
                                    language = language,
                                    logoutMessage = (logoutResult as? LogoutResult.Blocked)?.let {
                                        stringResource(
                                            R.string.more_sign_out_blocked,
                                            it.cashier,
                                            it.state.name.lowercase(),
                                        )
                                    },
                                    recovery = recoveryState,
                                    closingAvailable = closingAvailable,
                                ),
                                layoutMode = layoutMode,
                                modifier = Modifier.testTag("destination-content-more"),
                                logoutVisible = logoutVisible,
                                onThemeModeSelected = onThemeModeSelected,
                                onAccentSelected = onAccentSelected,
                                onLanguageSelected = onLanguageSelected,
                                onLogout = { onLogout() },
                                onOpenClosing = {
                                    onOpenClosing()
                                    navController.navigate("closing")
                                },
                                onAcknowledgeRecovery = onAcknowledgeRecovery,
                                onReauthenticateRecovery = onReauthenticateRecovery,
                                onRecoverManualClosing = onRecoverManualClosing,
                            )
                        }
                        composable("closing") {
                            ClosingScreen(
                                state = closingState,
                                onCountedAmountChanged = onClosingAmountChanged,
                                onSubmit = onSubmitClosing,
                                onCheckStatus = onCheckClosingStatus,
                                onReauthenticate = onReauthenticateRecovery,
                                onRetryPreview = onRetryClosingPreview,
                                onDone = {
                                    if (onCloseClosingReceipt()) {
                                        navController.popBackStack(PosDestination.MORE.route, false)
                                    }
                                },
                                onBack = {
                                    navController.popBackStack(PosDestination.MORE.route, false)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
