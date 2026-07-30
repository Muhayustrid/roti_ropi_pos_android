package com.rotiropi.pos_erpnext.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rotiropi.pos_erpnext.ui.cashier.CashierScreen
import com.rotiropi.pos_erpnext.ui.cashier.CashierUiState
import com.rotiropi.pos_erpnext.ui.components.RootNavigationBar
import com.rotiropi.pos_erpnext.ui.dashboard.DashboardScreen
import com.rotiropi.pos_erpnext.ui.dashboard.DashboardUiState
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
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedDestination = PosDestination.entries.firstOrNull {
        it.route == backStackEntry?.destination?.route
    } ?: PosDestination.HOME

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
                            state = DashboardUiState.Unavailable,
                            layoutMode = layoutMode,
                            modifier = Modifier.testTag("destination-content-home"),
                        )
                    }
                    composable(PosDestination.PRODUCTS.route) {
                        ProductsScreen(
                            state = ProductsUiState.Unavailable,
                            layoutMode = layoutMode,
                            modifier = Modifier.testTag("destination-content-products"),
                        )
                    }
                    composable(PosDestination.CASHIER.route) {
                        CashierScreen(
                            state = CashierUiState.Unavailable,
                            layoutMode = layoutMode,
                            modifier = Modifier.testTag("destination-content-cashier"),
                        )
                    }
                    composable(PosDestination.REPORTS.route) {
                        ReportsScreen(
                            state = ReportsUiState.Unavailable,
                            layoutMode = layoutMode,
                            modifier = Modifier.testTag("destination-content-reports"),
                        )
                    }
                    composable(PosDestination.MORE.route) {
                        MoreScreen(
                            state = MoreUiState(
                                outletLabel = null,
                                userSessionLabel = null,
                                themeMode = themeMode,
                                accent = accent,
                                demoData = false,
                            ),
                            layoutMode = layoutMode,
                            modifier = Modifier.testTag("destination-content-more"),
                            onThemeModeSelected = onThemeModeSelected,
                            onAccentSelected = onAccentSelected,
                        )
                    }
                }
            }
        }
    }
}
