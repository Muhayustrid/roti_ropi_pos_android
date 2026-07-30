package com.rotiropi.pos_erpnext.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.reports.ReportBreakdown
import com.rotiropi.pos_erpnext.ui.reports.ReportChartBar
import com.rotiropi.pos_erpnext.ui.reports.ReportMetric
import com.rotiropi.pos_erpnext.ui.reports.ReportPeriod
import com.rotiropi.pos_erpnext.ui.reports.ReportTopProduct
import com.rotiropi.pos_erpnext.ui.reports.ReportsContent
import com.rotiropi.pos_erpnext.ui.reports.ReportsScreen
import com.rotiropi.pos_erpnext.ui.reports.ReportsUiState
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import com.rotiropi.pos_erpnext.ui.theme.PosTheme

private val reportsDemoState = ReportsUiState.Content(
    ReportsContent(
        selectedPeriod = ReportPeriod.TODAY,
        metrics = listOf(
            ReportMetric("net-sales", "Net sales", "IDR 825,000", "Sales today"),
            ReportMetric("total-orders", "Total orders", "18", "Paid transactions"),
            ReportMetric("average-order", "Average order", "IDR 45,833", "Per completed sale"),
        ),
        breakdown = listOf(
            ReportBreakdown("cash", "Cash", "IDR 525,000"),
            ReportBreakdown("bank", "Bank Transfer", "IDR 300,000"),
        ),
        chartBars = listOf(
            ReportChartBar("08:00", "08:00", "IDR 120,000", 0.4f),
            ReportChartBar("10:00", "10:00", "IDR 210,000", 0.7f),
            ReportChartBar("12:00", "12:00", "IDR 300,000", 1.0f),
            ReportChartBar("14:00", "14:00", "IDR 195,000", 0.65f),
        ),
        chartSummary = "Peak sales reached IDR 300,000 around 12:00.",
        topProducts = listOf(
            ReportTopProduct("croissant", "Croissant Pack", "12 sold"),
            ReportTopProduct("coffee", "Coffee Beans", "8 sold"),
        ),
        demoData = true,
    )
)

@Preview(name = "Reports compact light Blue", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun ReportsCompactPreview() {
    PosTheme(darkTheme = false, accent = PosAccent.BLUE) {
        ReportsScreen(reportsDemoState, PosLayoutMode.COMPACT)
    }
}

@Preview(name = "Reports expanded dark Teal", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
fun ReportsExpandedPreview() {
    PosTheme(darkTheme = true, accent = PosAccent.TEAL) {
        ReportsScreen(reportsDemoState, PosLayoutMode.EXPANDED)
    }
}

@Preview(name = "Reports font 1.5x", widthDp = 800, heightDp = 1280, fontScale = 1.5f)
@Composable
fun ReportsFontScalePreview() {
    PosTheme(darkTheme = false, accent = PosAccent.BLUE) {
        ReportsScreen(reportsDemoState, PosLayoutMode.EXPANDED)
    }
}
