package com.rotiropi.pos_erpnext.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.rotiropi.pos_erpnext.data.api.SaleSummaryDto
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

@Composable
fun HistoryScreen(state: HistoryUiState, onQueryChanged: (String) -> Unit, onSaleSelected: (String) -> Unit, onLoadMore: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        HistoryUiState.Unavailable -> Message("History unavailable", modifier)
        is HistoryUiState.Content -> LazyColumn(modifier.fillMaxSize().testTag("history-screen"), contentPadding = androidx.compose.foundation.layout.PaddingValues(PosDimensions.screenPadding), verticalArrangement = Arrangement.spacedBy(PosDimensions.sectionSpacing)) {
            item { Text("Sale history", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
            item { OutlinedTextField(state.query, onQueryChanged, label = { Text("Search sales") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("history-search")) }
            if (state.loading && state.sales.isEmpty()) item { CircularProgressIndicator() }
            if (!state.loading && state.sales.isEmpty() && state.error == null) item { Text("No sales found") }
            items(state.sales, key = { it.name }) { sale -> SaleRow(sale, onSaleSelected) }
            if (state.error != null) item { Column { Text(state.error, color = MaterialTheme.colorScheme.error); Button(onRetry, Modifier.heightIn(min = PosDimensions.touchTarget)) { Text("Retry") } } }
            if (state.hasMore) item { Button(onLoadMore, Modifier.fillMaxWidth().heightIn(min = PosDimensions.touchTarget).testTag("history-load-more")) { Text("Load more") } }
        }
    }
}

@Composable private fun SaleRow(sale: SaleSummaryDto, onClick: (String) -> Unit) = Column(
    Modifier.fillMaxWidth().clickable { onClick(sale.name) }.padding(vertical = PosDimensions.sectionSpacing).testTag("history-sale-${sale.name}"),
) { Text(sale.walk_in_customer_name ?: sale.customer, style = MaterialTheme.typography.titleMedium); Text(sale.name); Text("${sale.currency} ${sale.grand_total}") }

@Composable private fun Message(value: String, modifier: Modifier) = Column(modifier.fillMaxSize().padding(PosDimensions.screenPadding)) { Text(value) }
