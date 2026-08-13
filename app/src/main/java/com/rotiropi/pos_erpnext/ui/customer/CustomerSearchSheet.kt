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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.R
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
    val walkInFocus = remember { FocusRequester() }
    val firstCustomerFocus = remember { FocusRequester() }
    val initialRetryFocus = remember { FocusRequester() }
    val loadMoreFocus = remember { FocusRequester() }
    val pageRetryFocus = remember { FocusRequester() }
    val doneFocus = remember { FocusRequester() }
    val afterCustomers = when {
        state.hasMore -> loadMoreFocus
        state.pageError != null -> pageRetryFocus
        else -> doneFocus
    }
    val afterWalkIn = if (state.customers.isNotEmpty()) firstCustomerFocus else when {
        state.error != null -> initialRetryFocus
        else -> afterCustomers
    }
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
                    val walkInSelected = stringResource(R.string.customer_walk_in_selected)
                    val registeredSelected = (state.selection as? CustomerSelection.Registered)
                        ?.let { stringResource(R.string.customer_registered_selected, it.displayLabel) }
                    val noneSelected = stringResource(R.string.customer_none_selected)
                    Text(stringResource(R.string.customer_label), modifier = Modifier.testTag("customer-selection").semantics {
                        stateDescription = when (state.selection) {
                            is CustomerSelection.WalkIn -> walkInSelected
                            is CustomerSelection.Registered -> registeredSelected ?: noneSelected
                            null -> noneSelected
                        }
                    })
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(doneFocus)
                            .requiredHeight(PosDimensions.touchTarget)
                            .testTag("customer-dismiss"),
                    ) { Text(stringResource(R.string.action_done)) }
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChanged,
                    label = { Text(stringResource(R.string.customer_search_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .keyboardNext(walkInFocus)
                        .focusProperties {
                            next = walkInFocus
                            down = walkInFocus
                        }
                        .testTag("customer-search-input"),
                )
            }
        }
        item {
            val selection = state.selection
            if (selection is CustomerSelection.WalkIn) {
                OutlinedTextField(
                    value = selection.displayName,
                    onValueChange = onWalkInNameChanged,
                    label = { Text(stringResource(R.string.customer_walk_in_name_label)) },
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .keyboardNext(afterWalkIn)
                        .focusProperties {
                            next = afterWalkIn
                            down = afterWalkIn
                        }
                        .focusRequester(walkInFocus)
                        .testTag("customer-walk-in-name"),
                )
            } else {
                Button(
                    onClick = onSelectWalkIn,
                    modifier = Modifier.keyboardNext(afterWalkIn)
                        .focusProperties {
                        next = afterWalkIn
                        down = afterWalkIn
                    }
                        .focusRequester(walkInFocus)
                        .padding(horizontal = 16.dp)
                        .requiredHeight(PosDimensions.touchTarget)
                        .testTag("customer-select-walk-in"),
                ) { Text(stringResource(R.string.customer_walk_in)) }
            }
        }
        if (state.loading) item { Text(stringResource(R.string.customer_loading), Modifier.padding(horizontal = 16.dp).testTag("customer-loading").semantics { liveRegion = LiveRegionMode.Polite }) }
        state.error?.let { error -> item {
            Text(stringResource(error.toUiMessage()), Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Assertive })
            Button(
                onClick = onRetry,
                modifier = Modifier.keyboardNext(doneFocus)
                    .focusProperties {
                    next = doneFocus
                    down = doneFocus
                }
                    .focusRequester(initialRetryFocus)
                    .padding(horizontal = 16.dp)
                    .requiredHeight(PosDimensions.touchTarget)
                    .testTag("customer-retry"),
            ) { Text(stringResource(R.string.action_retry)) }
        } }
        if (!state.loading && state.error == null && state.customers.isEmpty()) item { Text(stringResource(R.string.customer_empty), Modifier.padding(horizontal = 16.dp).testTag("customer-empty")) }
        itemsIndexed(state.customers, key = { _, customer -> customer.id }) { index, customer ->
            val selectDescription = stringResource(R.string.customer_select_description, customer.displayLabel)
            ListItem(
                headlineContent = { Text(customer.displayLabel) },
                supportingContent = { customer.mobile?.let { Text(it) } },
                modifier = Modifier
                    .then(if (index == state.customers.lastIndex) Modifier.keyboardNext(afterCustomers) else Modifier)
                    .then(if (index == state.customers.lastIndex) Modifier.focusProperties {
                        next = afterCustomers
                        down = afterCustomers
                    } else Modifier)
                    .then(if (index == 0) Modifier.focusRequester(firstCustomerFocus) else Modifier)
                    .testTag("customer-${customer.id}")
                    .semantics { contentDescription = selectDescription }
                    .clickable { onSelectRegistered(customer) },
            )
        }
        if (state.hasMore) item {
            Button(
                onClick = onLoadMore,
                enabled = !state.loading,
                modifier = Modifier.keyboardNext(if (state.pageError != null) pageRetryFocus else doneFocus)
                    .focusProperties {
                    val target = if (state.pageError != null) pageRetryFocus else doneFocus
                    next = target
                    down = target
                }
                    .focusRequester(loadMoreFocus)
                    .padding(horizontal = 16.dp)
                    .requiredHeight(PosDimensions.touchTarget)
                    .testTag("customer-load-more"),
            ) { Text(stringResource(R.string.action_load_more)) }
        }
        state.pageError?.let { error -> item {
            Text(stringResource(error.toUiMessage()), Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Assertive })
            Button(
                onClick = onRetry,
                modifier = Modifier.keyboardNext(doneFocus)
                    .focusProperties {
                    next = doneFocus
                    down = doneFocus
                }
                    .focusRequester(pageRetryFocus)
                    .padding(horizontal = 16.dp)
                    .requiredHeight(PosDimensions.touchTarget)
                    .testTag("customer-page-retry"),
            ) { Text(stringResource(R.string.action_retry)) }
        } }
    }
}

private fun Modifier.keyboardNext(target: FocusRequester): Modifier = onPreviewKeyEvent { event ->
    if (event.key != Key.Tab && event.key != Key.DirectionDown) return@onPreviewKeyEvent false
    if (event.type == KeyEventType.KeyDown) target.requestFocus()
    true
}
