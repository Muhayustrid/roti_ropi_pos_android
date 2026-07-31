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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

fun reportsGridColumns(layoutMode: PosLayoutMode): Int =
    if (layoutMode == PosLayoutMode.EXPANDED) 4 else 2

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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PosDimensions.screenPadding)
            .semantics { stateDescription = "Unavailable" },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Reports",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Reports unavailable",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Complete report data is not available from the current server contract.",
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
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(rootTag)
            .semantics { stateDescription = "Reports content" },
        contentPadding = PaddingValues(PosDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ReportsHeader(demoData = content.demoData)
        }
        item {
            PeriodChips(
                selectedPeriod = content.selectedPeriod,
                onPeriodSelected = onPeriodSelected,
            )
        }
        item {
            MetricGrid(
                metrics = content.metrics,
                columns = reportsGridColumns(layoutMode),
            )
        }
        if (layoutMode == PosLayoutMode.EXPANDED) {
            item {
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
                        SectionTitle("Sales Trend")
                        ReportsChartSection(content)
                        SectionTitle("Category Breakdown")
                        BreakdownList(content.breakdown)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reports-expanded-top-products-pane"),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SectionTitle("Top Products")
                        TopProductsList(content.topProducts)
                    }
                }
            }
        } else {
            item {
                SectionTitle("Sales Trend")
            }
            item {
                ReportsChartSection(content)
            }
            item {
                SectionTitle("Category Breakdown")
            }
            item {
                BreakdownList(content.breakdown)
            }
            item {
                SectionTitle("Top Products")
            }
            items(content.topProducts, key = { it.id }) { product ->
                TopProductCard(product)
            }
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
            text = "Reports",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (demoData) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = "Demo data",
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
                label = { Text(period.label) },
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            content.chartBars.forEach { bar ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    contentDescription = "Sales trend chart. ${content.chartSummary}"
                },
        ) {
            val barCount = content.chartBars.size
            if (barCount > 0) {
                val availableWidth = size.width
                val availableHeight = size.height
                val barWidth = (availableWidth / (barCount * 2)).coerceAtLeast(10f)
                val spacing = (availableWidth - (barWidth * barCount)) / (barCount + 1)

                content.chartBars.forEachIndexed { index, bar ->
                    val barHeight = availableHeight * bar.safeFraction
                    val x = spacing + index * (barWidth + spacing)
                    val y = availableHeight - barHeight

                    drawRect(
                        color = primaryColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            content.chartBars.forEach { bar ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
