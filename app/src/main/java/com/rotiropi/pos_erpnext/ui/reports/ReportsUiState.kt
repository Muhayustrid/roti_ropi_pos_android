package com.rotiropi.pos_erpnext.ui.reports

import androidx.annotation.StringRes
import com.rotiropi.pos_erpnext.R

enum class ReportPeriod(@param:StringRes val labelRes: Int) {
    TODAY(R.string.reports_period_today),
    WEEK(R.string.reports_period_week),
    MONTH(R.string.reports_period_month),
}

data class ReportMetric(
    val id: String,
    val label: String,
    val value: String,
    val supportingLabel: String,
)

data class ReportBreakdown(
    val id: String,
    val label: String,
    val value: String,
)

data class ReportChartBar(
    val id: String,
    val label: String,
    val valueLabel: String,
    val fraction: Float,
) {
    val safeFraction: Float = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
}

data class ReportTopProduct(
    val id: String,
    val label: String,
    val value: String,
)

data class ReportsContent(
    val selectedPeriod: ReportPeriod,
    val metrics: List<ReportMetric>,
    val breakdown: List<ReportBreakdown>,
    val chartBars: List<ReportChartBar>,
    val chartSummary: String,
    val topProducts: List<ReportTopProduct>,
    val demoData: Boolean,
)

sealed interface ReportsUiState {
    data object Unavailable : ReportsUiState
    data class Content(val content: ReportsContent) : ReportsUiState
}
