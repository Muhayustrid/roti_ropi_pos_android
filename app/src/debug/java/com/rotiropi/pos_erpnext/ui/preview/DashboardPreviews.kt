package com.rotiropi.pos_erpnext.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rotiropi.pos_erpnext.ui.dashboard.DashboardContent
import com.rotiropi.pos_erpnext.ui.dashboard.DashboardMetric
import com.rotiropi.pos_erpnext.ui.dashboard.DashboardQuickAction
import com.rotiropi.pos_erpnext.ui.dashboard.DashboardScreen
import com.rotiropi.pos_erpnext.ui.dashboard.DashboardUiState
import com.rotiropi.pos_erpnext.ui.dashboard.LowStockItem
import com.rotiropi.pos_erpnext.ui.dashboard.RecentTransaction
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import com.rotiropi.pos_erpnext.ui.theme.PosTheme

private val dashboardDemoState = DashboardUiState.Populated(
    DashboardContent(
        outletName = "Outlet Menteng",
        sales = DashboardMetric("Sales today", "IDR 825,000", "ERPNext snapshot"),
        transactions = DashboardMetric("Transactions", "18", "ERPNext snapshot"),
        quickActions = listOf(
            DashboardQuickAction("open-session", "Open session", true),
            DashboardQuickAction("start-sale", "Start sale", true),
        ),
        recentTransactions = listOf(
            RecentTransaction("ACC-PSINV-00018", "Ayu", "IDR 55,000", "14:25"),
            RecentTransaction("ACC-PSINV-00017", "Walk In Customer", "IDR 42,000", "14:08"),
        ),
        lowStockItems = listOf(
            LowStockItem("Croissant Pack", "3", "Pack", "Outlet 01 - RR"),
            LowStockItem("Coffee Beans", "5", "Bag", "Outlet 01 - RR"),
        ),
        demoData = true,
    )
)

@Preview(name = "Dashboard phone light", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun DashboardPhonePreview() {
    DashboardPreview(darkTheme = false, accent = PosAccent.BLUE, layoutMode = PosLayoutMode.COMPACT)
}

@Preview(name = "Dashboard phone dark", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun DashboardDarkPreview() {
    DashboardPreview(darkTheme = true, accent = PosAccent.TEAL, layoutMode = PosLayoutMode.COMPACT)
}

@Preview(name = "Dashboard tablet 1.5x", widthDp = 800, heightDp = 1280, fontScale = 1.5f)
@Composable
fun DashboardTabletFontScalePreview() {
    DashboardPreview(darkTheme = false, accent = PosAccent.BLUE, layoutMode = PosLayoutMode.EXPANDED)
}

@Preview(name = "Dashboard release unavailable", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun DashboardUnavailablePreview() {
    PosTheme {
        DashboardScreen(DashboardUiState.Unavailable, PosLayoutMode.COMPACT)
    }
}

@Composable
private fun DashboardPreview(
    darkTheme: Boolean,
    accent: PosAccent,
    layoutMode: PosLayoutMode,
) {
    PosTheme(darkTheme = darkTheme, accent = accent) {
        DashboardScreen(dashboardDemoState, layoutMode)
    }
}
