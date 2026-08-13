package com.rotiropi.pos_erpnext.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

fun reportsGridColumns(layoutMode: PosLayoutMode): Int =
    if (layoutMode == PosLayoutMode.EXPANDED) 4 else 2

internal data class ChartBarSlot(val x: Float, val width: Float)

/** Bars, value labels, and axis labels all share these equal-width slots so they stay aligned. */
internal fun chartBarSlot(index: Int, count: Int, availableWidth: Float): ChartBarSlot {
    val slotWidth = availableWidth / count
    val barWidth = (slotWidth / 2f).coerceAtLeast(10f)
    val center = (index + 0.5f) * slotWidth
    return ChartBarSlot(center - barWidth / 2f, barWidth)
}

@Composable
fun ReportsScreen(
    state: ReportsUiState,
    layoutMode: PosLayoutMode,
    modifier: Modifier = Modifier,
    onPeriodSelected: (ReportPeriod) -> Unit = {},
) {
    when (state) {
        ReportsUiState.Unavailable -> ReportsUnavailable(modifier)
        is ReportsUiState.Content -> ReportsPopulated(
            content = state.content,
            layoutMode = layoutMode,
            modifier = modifier,
            onPeriodSelected = onPeriodSelected,
        )
    }
}

@Composable
private fun ReportsUnavailable(modifier: Modifier = Modifier) {
    val unavailable = stringResource(R.string.state_unavailable)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PosDimensions.screenPadding)
            .semantics { stateDescription = unavailable },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.reports_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.reports_unavailable),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.reports_unavailable_detail),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ReportsPopulated(
    content: ReportsContent,
    layoutMode: PosLayoutMode,
    modifier: Modifier = Modifier,
    onPeriodSelected: (ReportPeriod) -> Unit,
) {
    val rootTag = if (layoutMode == PosLayoutMode.EXPANDED) "reports-expanded" else "reports-compact"
    val contentDescriptionState = stringResource(R.string.reports_state_content)
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(rootTag)
            .padding(PosDimensions.screenPadding)
            .verticalScroll(rememberScrollState())
            .semantics { stateDescription = contentDescriptionState },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ReportsHeader(demoData = content.demoData)
        PeriodChips(
            selectedPeriod = content.selectedPeriod,
            onPeriodSelected = onPeriodSelected,
        )
        MetricGrid(
            metrics = content.metrics,
            columns = reportsGridColumns(layoutMode),
        )
        if (layoutMode == PosLayoutMode.EXPANDED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reports-expanded-primary-pane"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SectionTitle(stringResource(R.string.reports_section_trend))
                    ReportsChartSection(content)
                    SectionTitle(stringResource(R.string.reports_section_breakdown))
                    BreakdownList(content.breakdown)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reports-expanded-top-products-pane"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SectionTitle(stringResource(R.string.reports_section_top_products))
                    TopProductsList(content.topProducts)
                }
            }
        } else {
            SectionTitle(stringResource(R.string.reports_section_trend))
            ReportsChartSection(content)
            SectionTitle(stringResource(R.string.reports_section_breakdown))
            BreakdownList(content.breakdown)
            SectionTitle(stringResource(R.string.reports_section_top_products))
            TopProductsList(content.topProducts)
        }
    }
}

@Composable
private fun ReportsHeader(demoData: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.reports_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (demoData) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = stringResource(R.string.badge_demo_data),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PeriodChips(
    selectedPeriod: ReportPeriod,
    onPeriodSelected: (ReportPeriod) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReportPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selectedPeriod,
                onClick = { onPeriodSelected(period) },
                label = { Text(stringResource(period.labelRes)) },
                modifier = Modifier
                    .heightIn(min = PosDimensions.touchTarget)
                    .testTag("reports-period-${period.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun MetricGrid(metrics: List<ReportMetric>, columns: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        metrics.chunked(columns).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowMetrics.forEach { metric ->
                    MetricCard(metric, Modifier.weight(1f))
                }
                repeat(columns - rowMetrics.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MetricCard(metric: ReportMetric, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.testTag("reports-metric-${metric.id}")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(metric.label, style = MaterialTheme.typography.labelLarge)
            Text(metric.value, style = MaterialTheme.typography.headlineSmall)
            Text(
                metric.supportingLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ReportsChartSection(content: ReportsContent) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val chartDescription = stringResource(R.string.reports_chart_description, content.chartSummary)
        Row(modifier = Modifier.fillMaxWidth()) {
            content.chartBars.forEach { bar ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(bar.valueLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .testTag("reports-chart")
                .semantics {
                    contentDescription = chartDescription
                },
        ) {
            val barCount = content.chartBars.size
            if (barCount > 0) {
                val availableHeight = size.height

                content.chartBars.forEachIndexed { index, bar ->
                    val slot = chartBarSlot(index, barCount, size.width)
                    val barHeight = availableHeight * bar.safeFraction
                    val y = availableHeight - barHeight

                    drawRect(
                        color = primaryColor,
                        topLeft = Offset(slot.x, y),
                        size = Size(slot.width, barHeight),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            content.chartBars.forEach { bar ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(bar.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            text = content.chartSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BreakdownList(breakdown: List<ReportBreakdown>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        breakdown.forEach { item ->
            ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("reports-breakdown-${item.id}")) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.label, style = MaterialTheme.typography.titleMedium)
                    Text(item.value, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun TopProductsList(products: List<ReportTopProduct>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        products.forEach { product ->
            TopProductCard(product)
        }
    }
}

@Composable
private fun TopProductCard(product: ReportTopProduct) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reports-top-product-${product.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(product.label, style = MaterialTheme.typography.titleMedium)
            Text(
                product.value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
}
