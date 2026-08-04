package com.rotiropi.pos_erpnext.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomerSearchSheet(
    state: CustomerSearchUiState,
    onQueryChanged: (String) -> Unit,
    onWalkInNameChanged: (String) -> Unit,
    onSelectWalkIn: () -> Unit,
    onSelectRegistered: (CustomerRecord) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("customer-search-sheet")) {
        CustomerSearchContent(
            state = state,
            onQueryChanged = onQueryChanged,
            onWalkInNameChanged = onWalkInNameChanged,
            onSelectWalkIn = onSelectWalkIn,
            onSelectRegistered = onSelectRegistered,
            onRetry = onRetry,
            onLoadMore = onLoadMore,
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CustomerSearchContent(
    state: CustomerSearchUiState,
    onQueryChanged: (String) -> Unit,
    onWalkInNameChanged: (String) -> Unit,
    onSelectWalkIn: () -> Unit,
    onSelectRegistered: (CustomerRecord) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.testTag("customer-results"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stickyHeader {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp)
                    .testTag("customer-search-header"),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Customer", modifier = Modifier.testTag("customer-selection").semantics {
                        stateDescription = when (val selection = state.selection) {
                            is CustomerSelection.WalkIn -> "Walk-in customer selected"
                            is CustomerSelection.Registered -> "Registered customer ${selection.displayLabel} selected"
                            null -> "No customer selected"
                        }
                    })
                    Button(onClick = onDismiss, modifier = Modifier.requiredHeight(PosDimensions.touchTarget).testTag("customer-dismiss")) { Text("Done") }
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChanged,
                    label = { Text("Search customers") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("customer-search-input"),
                )
            }
        }
        item {
            val selection = state.selection
            if (selection is CustomerSelection.WalkIn) {
                OutlinedTextField(
                    value = selection.displayName,
                    onValueChange = onWalkInNameChanged,
                    label = { Text("Walk-in display name") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("customer-walk-in-name"),
                )
            } else {
                Button(onClick = onSelectWalkIn, modifier = Modifier.padding(horizontal = 16.dp).requiredHeight(PosDimensions.touchTarget).testTag("customer-select-walk-in")) { Text("Walk-in customer") }
            }
        }
        if (state.loading) item { Text("Loading customers", Modifier.padding(horizontal = 16.dp).testTag("customer-loading").semantics { liveRegion = LiveRegionMode.Polite }) }
        state.error?.let { error -> item {
            Text(error.toUiMessage(), Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Assertive })
            Button(onClick = onRetry, modifier = Modifier.padding(horizontal = 16.dp).requiredHeight(PosDimensions.touchTarget).testTag("customer-retry")) { Text("Retry") }
        } }
        if (!state.loading && state.error == null && state.customers.isEmpty()) item { Text("No customers found", Modifier.padding(horizontal = 16.dp).testTag("customer-empty")) }
        items(state.customers, key = CustomerRecord::id) { customer ->
            ListItem(
                headlineContent = { Text(customer.displayLabel) },
                supportingContent = { customer.mobile?.let { Text(it) } },
                modifier = Modifier.testTag("customer-${customer.id}").semantics { contentDescription = "Select ${customer.displayLabel}" }.clickable { onSelectRegistered(customer) },
            )
        }
        if (state.hasMore) item { Button(onClick = onLoadMore, enabled = !state.loading, modifier = Modifier.padding(horizontal = 16.dp).requiredHeight(PosDimensions.touchTarget).testTag("customer-load-more")) { Text("Load more") } }
        state.pageError?.let { error -> item {
            Text(error.toUiMessage(), Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Assertive })
            Button(onClick = onRetry, modifier = Modifier.padding(horizontal = 16.dp).requiredHeight(PosDimensions.touchTarget).testTag("customer-page-retry")) { Text("Retry") }
        } }
    }
}
