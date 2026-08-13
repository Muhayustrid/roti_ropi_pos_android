package com.rotiropi.pos_erpnext.ui.cashier

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.ui.LocalPosWindow
import com.rotiropi.pos_erpnext.ui.resolve
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.receipt.ReceiptScreen
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions
import com.rotiropi.pos_erpnext.ui.customer.CustomerSearchSheet
import com.rotiropi.pos_erpnext.ui.customer.CustomerSearchUiState
import com.rotiropi.pos_erpnext.ui.customer.CustomerRecord

@Composable
fun CashierScreen(
    state: CashierUiState,
    layoutMode: PosLayoutMode,
    modifier: Modifier = Modifier,
    cartVisible: Boolean = false,
    onQueryChange: (String) -> Unit = {},
    onBarcodeChange: (String) -> Unit = {},
    onBarcodeSubmit: () -> Unit = {},
    onLoadMoreCatalog: () -> Unit = {},
    onCategorySelected: (CashierCategory) -> Unit = {},
    onProductSelected: (CashierProduct) -> Unit = {},
    onOpenCart: () -> Unit = {},
    onDismissCart: () -> Unit = {},
    onDecreaseQuantity: (CartLine) -> Unit = {},
    onIncreaseQuantity: (CartLine) -> Unit = {},
    onEditQuantity: (CartLine, String) -> Unit = { _, _ -> },
    onRemoveLine: (CartLine) -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenCheckout: () -> Unit = {},
    onUpdatePaymentAmount: (String, String) -> Unit = { _, _ -> },
    onSubmitPayment: () -> Unit = {},
    onCloseReceipt: () -> Unit = {},
    customerState: CustomerSearchUiState? = null,
    customerSheetVisible: Boolean = false,
    onOpenCustomerSheet: () -> Unit = {},
    onDismissCustomerSheet: () -> Unit = {},
    onCustomerQueryChanged: (String) -> Unit = {},
    onWalkInNameChanged: (String) -> Unit = {},
    onSelectWalkIn: () -> Unit = {},
    onSelectRegistered: (CustomerRecord) -> Unit = {},
    onCustomerRetry: () -> Unit = {},
    onCustomerLoadMore: () -> Unit = {},
) {
    when (state) {
        CashierUiState.Unavailable -> CashierUnavailable(
            modifier, customerState, onOpenCustomerSheet,
        )
        is CashierUiState.Error -> CashierError(state.message, modifier, onRetry)
        is CashierUiState.Receipt -> ReceiptScreen(state.content, modifier, onCloseReceipt)
        is CashierUiState.Active -> CashierActive(
            content = state.content,
            layoutMode = layoutMode,
            modifier = modifier,
            cartVisible = cartVisible,
            onQueryChange = onQueryChange,
            onBarcodeChange = onBarcodeChange,
            onBarcodeSubmit = onBarcodeSubmit,
            onCategorySelected = onCategorySelected,
            onProductSelected = onProductSelected,
            onOpenCart = onOpenCart,
            onDismissCart = onDismissCart,
            onDecreaseQuantity = onDecreaseQuantity,
            onIncreaseQuantity = onIncreaseQuantity,
            onEditQuantity = onEditQuantity,
            onRetry = onRetry,
            onOpenCheckout = onOpenCheckout,
            onUpdatePaymentAmount = onUpdatePaymentAmount,
            onSubmitPayment = onSubmitPayment,
            onLoadMoreCatalog = onLoadMoreCatalog,
            onRemoveLine = onRemoveLine,
            customerState = customerState,
            onOpenCustomerSheet = onOpenCustomerSheet,
        )
    }
    if (customerSheetVisible && customerState != null) {
        CustomerSearchSheet(customerState, onCustomerQueryChanged, onWalkInNameChanged, onSelectWalkIn, onSelectRegistered, onCustomerRetry, onCustomerLoadMore, onDismissCustomerSheet)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CashierActive(
    content: CashierContent,
    layoutMode: PosLayoutMode,
    modifier: Modifier,
    cartVisible: Boolean,
    onQueryChange: (String) -> Unit,
    onBarcodeChange: (String) -> Unit,
    onBarcodeSubmit: () -> Unit,
    onCategorySelected: (CashierCategory) -> Unit,
    onProductSelected: (CashierProduct) -> Unit,
    onOpenCart: () -> Unit,
    onDismissCart: () -> Unit,
    onDecreaseQuantity: (CartLine) -> Unit,
    onIncreaseQuantity: (CartLine) -> Unit,
    onEditQuantity: (CartLine, String) -> Unit,
    onRemoveLine: (CartLine) -> Unit,
    onRetry: () -> Unit,
    onOpenCheckout: () -> Unit,
    onUpdatePaymentAmount: (String, String) -> Unit,
    onSubmitPayment: () -> Unit,
    onLoadMoreCatalog: () -> Unit,
    customerState: CustomerSearchUiState?,
    onOpenCustomerSheet: () -> Unit,
) {
    if (layoutMode == PosLayoutMode.EXPANDED && LocalPosWindow.current.isTall) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(PosDimensions.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CashierBrowser(
                content = content,
                onQueryChange = onQueryChange,
                onBarcodeChange = onBarcodeChange,
                onBarcodeSubmit = onBarcodeSubmit,
                onCategorySelected = onCategorySelected,
                onProductSelected = onProductSelected,
                onRetry = onRetry,
                onLoadMoreCatalog = onLoadMoreCatalog,
                customerState = customerState,
                onOpenCustomerSheet = onOpenCustomerSheet,
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.weight(1f),
            )
            Surface(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .testTag("cashier-cart-pane"),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 2.dp,
            ) {
                CartContent(
                    cart = content.cart,
                    checkoutState = content.checkoutState,
                    onDecreaseQuantity = onDecreaseQuantity,
                    onIncreaseQuantity = onIncreaseQuantity,
                    onEditQuantity = onEditQuantity,
                    onRemoveLine = onRemoveLine,
                    onRetry = onRetry,
                    onOpenCheckout = onOpenCheckout,
                    onUpdatePaymentAmount = onUpdatePaymentAmount,
                    onSubmitPayment = onSubmitPayment,
                    invalidQuantityForLine = content.invalidQuantityForLine,
                )
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            CashierBrowser(
                content = content,
                onQueryChange = onQueryChange,
                onBarcodeChange = onBarcodeChange,
                onBarcodeSubmit = onBarcodeSubmit,
                onCategorySelected = onCategorySelected,
                onProductSelected = onProductSelected,
                onRetry = onRetry,
                onLoadMoreCatalog = onLoadMoreCatalog,
                customerState = customerState,
                onOpenCustomerSheet = onOpenCustomerSheet,
                columns = GridCells.Fixed(2),
                gridBottomPadding = 88.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PosDimensions.screenPadding),
            )
            Button(
                onClick = onOpenCart,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(PosDimensions.screenPadding)
                    .heightIn(min = PosDimensions.touchTarget)
                    .testTag("cashier-cart-summary"),
            ) {
                Text(
                    stringResource(
                        R.string.cart_line_summary,
                        content.cart.itemCountLabel.resolve(),
                        content.cart.payableLabel.resolve(),
                    ),
                )
            }
        }
        if (cartVisible) {
            ModalBottomSheet(
                onDismissRequest = onDismissCart,
                modifier = Modifier.testTag("cashier-cart-sheet"),
            ) {
                CartContent(
                    cart = content.cart,
                    checkoutState = content.checkoutState,
                    modifier = Modifier.heightIn(max = 640.dp),
                    onDecreaseQuantity = onDecreaseQuantity,
                    onIncreaseQuantity = onIncreaseQuantity,
                    onEditQuantity = onEditQuantity,
                    onRemoveLine = onRemoveLine,
                    onRetry = onRetry,
                    onOpenCheckout = onOpenCheckout,
                    onUpdatePaymentAmount = onUpdatePaymentAmount,
                    onSubmitPayment = onSubmitPayment,
                    invalidQuantityForLine = content.invalidQuantityForLine,
                )
            }
        }
    }
}

@Composable
private fun CashierBrowser(
    content: CashierContent,
    onQueryChange: (String) -> Unit,
    onBarcodeChange: (String) -> Unit,
    onBarcodeSubmit: () -> Unit,
    onCategorySelected: (CashierCategory) -> Unit,
    onProductSelected: (CashierProduct) -> Unit,
    onRetry: () -> Unit,
    onLoadMoreCatalog: () -> Unit,
    customerState: CustomerSearchUiState?,
    onOpenCustomerSheet: () -> Unit,
    columns: GridCells,
    modifier: Modifier = Modifier,
    gridBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val focusManager = LocalFocusManager.current
    val demoState = stringResource(R.string.cashier_state_demo)
    val catalogState = stringResource(R.string.cashier_state_catalog)
    Column(
        modifier = modifier.semantics {
            stateDescription = if (content.demoData) demoState else catalogState
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CashierHeader(content.demoData, customerState, onOpenCustomerSheet)
        if (content.catalogLoading && content.products.isEmpty()) {
            Text(
                text = stringResource(R.string.cashier_loading_catalog),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else if (content.catalogError == null && content.products.isEmpty()) {
            Text(
                text = stringResource(R.string.cashier_no_products),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        content.catalogError?.let { error ->
            Text(error.resolve(), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = PosDimensions.touchTarget).testTag("cashier-catalog-retry"),
            ) { Text(stringResource(R.string.action_retry)) }
        }
        if (content.catalogLoading && content.products.isNotEmpty()) {
            Text(
                text = stringResource(R.string.cashier_loading_more),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (content.scanLoading) {
            Text(
                text = stringResource(R.string.cashier_scanning),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        content.scanError?.let { error ->
            Text(error.resolve(), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = PosDimensions.touchTarget).testTag("cashier-scan-retry"),
            ) { Text(stringResource(R.string.action_retry)) }
        }
        if (content.quoteLoading) {
            Text(
                text = stringResource(R.string.cashier_updating_quote),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        content.quoteError?.let { error ->
            Text(error.resolve(), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = PosDimensions.touchTarget).testTag("cashier-quote-retry"),
            ) { Text(stringResource(R.string.action_retry)) }
        }
        OutlinedTextField(
            value = content.query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.cashier_search_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PosDimensions.touchTarget)
                .testTag("cashier-search"),
        )
        OutlinedTextField(
            value = content.barcode,
            onValueChange = onBarcodeChange,
            label = { Text(stringResource(R.string.cashier_barcode_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onBarcodeSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PosDimensions.touchTarget)
                .testTag("cashier-barcode"),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(content.categories, key = { it.id }) { category ->
                val categoryLabel = category.label.resolve()
                val categoryDescription = stringResource(R.string.cashier_category_description, categoryLabel)
                FilterChip(
                    selected = category.id == content.selectedCategoryId,
                    onClick = { onCategorySelected(category) },
                    label = { Text(categoryLabel) },
                    modifier = Modifier
                        .heightIn(min = PosDimensions.touchTarget)
                        .semantics { contentDescription = categoryDescription },
                )
            }
        }
        ProductGrid(
            products = content.products,
            columns = columns,
            onProductSelected = onProductSelected,
            modifier = Modifier.weight(1f),
            gridBottomPadding = gridBottomPadding,
        )
        if (content.catalogHasMore) {
            Button(
                onClick = onLoadMoreCatalog,
                enabled = !content.catalogLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PosDimensions.touchTarget)
                    .testTag("cashier-load-more"),
            ) { Text(stringResource(R.string.action_load_more)) }
        }
    }
}

@Composable
private fun CashierHeader(
    demoData: Boolean,
    customerState: CustomerSearchUiState?,
    onOpenCustomerSheet: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.cashier_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = if (demoData) {
                    stringResource(R.string.cashier_subtitle_demo)
                } else {
                    stringResource(R.string.cashier_subtitle_snapshots)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (demoData) {
            DemoBadge()
        } else if (customerState != null) {
            val customerLabel = customerLabel(customerState)
            Button(
                onClick = onOpenCustomerSheet,
                modifier = Modifier
                    .heightIn(min = PosDimensions.touchTarget)
                    .testTag("customer-open"),
            ) { Text(customerLabel) }
        }
    }
}

/**
 * The button label for the customer selector. A registered customer's display label and a
 * walk-in name the cashier typed are server- or user-supplied and pass through verbatim;
 * only the two fallbacks are translated.
 */
@Composable
private fun customerLabel(state: CustomerSearchUiState): String =
    when (val selection = state.selection) {
        is com.rotiropi.pos_erpnext.ui.customer.CustomerSelection.WalkIn ->
            selection.displayName.ifBlank { stringResource(R.string.customer_walk_in) }
        is com.rotiropi.pos_erpnext.ui.customer.CustomerSelection.Registered -> selection.displayLabel
        null -> stringResource(R.string.customer_label)
    }

@Composable
internal fun CashierProductCard(
    product: CashierProduct,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val addDescription = stringResource(R.string.cashier_add_to_cart_description, product.itemName)
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PosDimensions.touchTarget)
            .semantics { contentDescription = addDescription }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(product.itemCode.take(2).uppercase(), style = MaterialTheme.typography.headlineSmall)
            }
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(product.itemName, style = MaterialTheme.typography.titleMedium)
                Text(
                    product.priceSnapshotLabel(LocalContext.current),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    product.stockSnapshotLabel(LocalContext.current),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CashierUnavailable(
    modifier: Modifier,
    customerState: CustomerSearchUiState?,
    onOpenCustomerSheet: () -> Unit,
) {
    Column(modifier = modifier.padding(PosDimensions.screenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.cashier_unavailable), style = MaterialTheme.typography.headlineMedium)
        customerState?.let { state ->
            val label = customerLabel(state)
            Button(onClick = onOpenCustomerSheet, modifier = Modifier.heightIn(min = PosDimensions.touchTarget).testTag("customer-open")) { Text(label) }
        }
        Text(stringResource(R.string.cashier_unavailable_detail))
    }
}

@Composable
private fun CashierError(message: String, modifier: Modifier, onRetry: () -> Unit) {
    CashierStatePanel(
        title = stringResource(R.string.cashier_could_not_load),
        message = message,
        modifier = modifier,
        error = true,
        onRetry = onRetry,
    )
}

@Composable
private fun CashierStatePanel(
    title: String,
    message: String,
    modifier: Modifier,
    error: Boolean = false,
    onRetry: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(PosDimensions.screenPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics {
                    if (error) liveRegion = LiveRegionMode.Assertive
                },
            )
            if (error) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = PosDimensions.touchTarget),
                ) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
    }
}

@Composable
private fun DemoBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = stringResource(R.string.badge_demo_data),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
