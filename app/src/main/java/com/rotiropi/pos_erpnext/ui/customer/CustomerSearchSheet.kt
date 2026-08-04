package com.rotiropi.pos_erpnext.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
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

@OptIn(ExperimentalMaterial3Api::class)
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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Customer", modifier = Modifier.semantics {
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
                modifier = Modifier.fillMaxWidth().heightIn(min = PosDimensions.touchTarget).testTag("customer-search-input"),
            )
            val selection = state.selection
            if (selection is CustomerSelection.WalkIn) {
                OutlinedTextField(
                    value = selection.displayName,
                    onValueChange = onWalkInNameChanged,
                    label = { Text("Walk-in display name") },
                    modifier = Modifier.fillMaxWidth().testTag("customer-walk-in-name"),
                )
            } else {
                Button(onClick = onSelectWalkIn, modifier = Modifier.requiredHeight(PosDimensions.touchTarget).testTag("customer-select-walk-in")) { Text("Walk-in customer") }
            }
            if (state.loading) Text("Loading customers", Modifier.testTag("customer-loading").semantics { liveRegion = LiveRegionMode.Polite })
            state.error?.let { error ->
                Text(error.toString(), Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                Button(onClick = onRetry, modifier = Modifier.requiredHeight(PosDimensions.touchTarget).testTag("customer-retry")) { Text("Retry") }
            }
            if (!state.loading && state.error == null && state.customers.isEmpty()) {
                Text("No customers found", Modifier.testTag("customer-empty"))
            }
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp).testTag("customer-results")) {
                items(state.customers, key = CustomerRecord::id) { customer ->
                    ListItem(
                        headlineContent = { Text(customer.displayLabel) },
                        supportingContent = { customer.mobile?.let { Text(it) } },
                        modifier = Modifier.testTag("customer-${customer.id}").semantics { contentDescription = "Select ${customer.displayLabel}" }.clickable { onSelectRegistered(customer) },
                    )
                }
                if (state.hasMore) item { Button(onClick = onLoadMore, enabled = !state.loading, modifier = Modifier.requiredHeight(PosDimensions.touchTarget).testTag("customer-load-more")) { Text("Load more") } }
                state.pageError?.let { error -> item {
                    Text(error.toString(), Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                    Button(onClick = onRetry, modifier = Modifier.requiredHeight(PosDimensions.touchTarget).testTag("customer-page-retry")) { Text("Retry") }
                } }
            }
        }
    }
}
