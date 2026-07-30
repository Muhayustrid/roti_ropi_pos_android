package com.rotiropi.pos_erpnext.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data object Empty : DashboardUiState
    data object Offline : DashboardUiState
    data object Unavailable : DashboardUiState
    data class Error(val message: String) : DashboardUiState
    data class Populated(val content: DashboardContent) : DashboardUiState
}

data class DashboardContent(
    val outletName: String,
    val sales: DashboardMetric,
    val transactions: DashboardMetric,
    val quickActions: List<DashboardQuickAction>,
    val recentTransactions: List<RecentTransaction>,
    val lowStockItems: List<LowStockItem>,
    val demoData: Boolean,
)

data class DashboardMetric(
    val label: String,
    val value: String,
    val supportingLabel: String,
)

data class DashboardQuickAction(
    val id: String,
    val label: String,
    val enabled: Boolean,
)

data class RecentTransaction(
    val reference: String,
    val customerName: String,
    val amount: String,
    val time: String,
)

data class LowStockItem(
    val itemName: String,
    val availableQuantity: String,
    val uom: String,
    val warehouse: String,
)

fun dashboardGridColumns(layoutMode: PosLayoutMode): Int =
    if (layoutMode == PosLayoutMode.EXPANDED) 4 else 2

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    layoutMode: PosLayoutMode,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onQuickAction: (DashboardQuickAction) -> Unit = {},
) {
    when (state) {
        DashboardUiState.Loading -> DashboardStatePanel(
            title = "Dashboard",
            message = "Loading outlet overview",
            stateLabel = "Loading",
            modifier = modifier.semantics { contentDescription = "Loading dashboard" },
            showProgress = true,
        )
        DashboardUiState.Empty -> DashboardStatePanel(
            title = "Dashboard",
            message = "No dashboard activity yet",
            supportingMessage = "Recent transactions will appear after integration.",
            stateLabel = "Empty",
            modifier = modifier,
        )
        DashboardUiState.Offline -> DashboardStatePanel(
            title = "Dashboard",
            message = "Dashboard is offline",
            supportingMessage = "Reconnect, then retry. No transaction was started.",
            stateLabel = "Offline",
            modifier = modifier,
            actionLabel = "Retry",
            onAction = onRetry,
        )
        DashboardUiState.Unavailable -> DashboardUnavailable(
            layoutMode = layoutMode,
            modifier = modifier,
            onQuickAction = onQuickAction,
        )
        is DashboardUiState.Error -> DashboardStatePanel(
            title = "Dashboard",
            message = state.message,
            supportingMessage = "Try loading the outlet overview again.",
            stateLabel = "Error",
            modifier = modifier,
            actionLabel = "Retry",
            onAction = onRetry,
        )
        is DashboardUiState.Populated -> DashboardPopulated(
            content = state.content,
            layoutMode = layoutMode,
            modifier = modifier,
            onQuickAction = onQuickAction,
        )
    }
}

@Composable
private fun DashboardUnavailable(
    layoutMode: PosLayoutMode,
    modifier: Modifier,
    onQuickAction: (DashboardQuickAction) -> Unit,
) {
    val actions = listOf(
        DashboardQuickAction("open-session", "Open session", false),
        DashboardQuickAction("start-sale", "Start sale", false),
    )
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .semantics { stateDescription = "Unavailable" },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(PosDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { DashboardHeader(outletName = "Outlet unavailable", demoData = false) }
        item {
            SnapshotNotice(
                title = "Complete dashboard metrics unavailable",
                body = "Current Mobile POS endpoints do not provide complete daily sales, transaction, or low-stock aggregates.",
            )
        }
        item {
            MetricGrid(
                metrics = listOf(
                    DashboardMetric("Sales today", "—", "Complete aggregate unavailable"),
                    DashboardMetric("Transactions", "—", "Complete aggregate unavailable"),
                ),
                columns = dashboardGridColumns(layoutMode),
            )
        }
        item { SectionTitle("Quick actions") }
        item { QuickActionGrid(actions, onQuickAction) }
    }
}

@Composable
private fun DashboardPopulated(
    content: DashboardContent,
    layoutMode: PosLayoutMode,
    modifier: Modifier,
    onQuickAction: (DashboardQuickAction) -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .semantics { stateDescription = "Dashboard content" },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(PosDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { DashboardHeader(content.outletName, content.demoData) }
        item {
            MetricGrid(
                metrics = listOf(content.sales, content.transactions),
                columns = dashboardGridColumns(layoutMode),
            )
        }
        item { SectionTitle("Quick actions") }
        item { QuickActionGrid(content.quickActions, onQuickAction) }
        item { SectionTitle("Recent transactions") }
        if (content.recentTransactions.isEmpty()) {
            item { MutedMessage("No recent transactions in this snapshot.") }
        } else {
            items(content.recentTransactions, key = { it.reference }) { transaction ->
                RecentTransactionCard(transaction)
            }
        }
        item { SectionTitle("Low stock") }
        if (content.lowStockItems.isEmpty()) {
            item { MutedMessage("No low-stock items in this snapshot.") }
        } else {
            items(content.lowStockItems) { item ->
                LowStockCard(item)
            }
        }
    }
}

@Composable
private fun DashboardHeader(outletName: String, demoData: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = outletName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
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
private fun SnapshotNotice(title: String, body: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MetricGrid(metrics: List<DashboardMetric>, columns: Int) {
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
private fun MetricCard(metric: DashboardMetric, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
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
private fun QuickActionGrid(
    actions: List<DashboardQuickAction>,
    onQuickAction: (DashboardQuickAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(2).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowActions.forEach { action ->
                    Button(
                        onClick = { onQuickAction(action) },
                        enabled = action.enabled,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = PosDimensions.touchTarget)
                            .testTag("dashboard-quick-action-${action.id}"),
                    ) {
                        Text(action.label)
                    }
                }
                repeat(2 - rowActions.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RecentTransactionCard(transaction: RecentTransaction) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.customerName, style = MaterialTheme.typography.titleMedium)
                Text(
                    transaction.reference,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(transaction.amount, style = MaterialTheme.typography.titleMedium)
                Text(transaction.time, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LowStockCard(item: LowStockItem) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(item.itemName, style = MaterialTheme.typography.titleMedium)
            Text(
                "${item.availableQuantity} ${item.uom} · ${item.warehouse} stock snapshot",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DashboardStatePanel(
    title: String,
    message: String,
    stateLabel: String,
    modifier: Modifier,
    supportingMessage: String? = null,
    actionLabel: String? = null,
    showProgress: Boolean = false,
    onAction: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(PosDimensions.screenPadding)
            .semantics { stateDescription = stateLabel },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            if (showProgress) CircularProgressIndicator()
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics {
                    if (stateLabel == "Error") liveRegion = LiveRegionMode.Assertive
                },
            )
            supportingMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            actionLabel?.let {
                Button(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = PosDimensions.touchTarget),
                ) {
                    Text(it)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun MutedMessage(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}
