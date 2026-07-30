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
import com.rotiropi.pos_erpnext.ui.components.RootNavigationBar
import com.rotiropi.pos_erpnext.ui.dashboard.DashboardScreen
import com.rotiropi.pos_erpnext.ui.dashboard.DashboardUiState
import com.rotiropi.pos_erpnext.ui.placeholder.PlaceholderScreen
import com.rotiropi.pos_erpnext.ui.products.ProductsScreen
import com.rotiropi.pos_erpnext.ui.products.ProductsUiState
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

@Composable
fun PosShell(modifier: Modifier = Modifier) {
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
                    listOf(
                        PosDestination.CASHIER,
                        PosDestination.REPORTS,
                        PosDestination.MORE,
                    ).forEach { destination ->
                        composable(destination.route) {
                            PlaceholderScreen(
                                destination = destination,
                                modifier = Modifier.testTag(
                                    "destination-content-${destination.route}"
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
