package com.rotiropi.pos_erpnext.ui.cashier

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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
    onCategorySelected: (CashierCategory) -> Unit = {},
    onProductSelected: (CashierProduct) -> Unit = {},
    onOpenCart: () -> Unit = {},
    onDismissCart: () -> Unit = {},
    onDecreaseQuantity: (CartLine) -> Unit = {},
    onIncreaseQuantity: (CartLine) -> Unit = {},
    onRetry: () -> Unit = {},
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
            onRetry = onRetry,
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
    onRetry: () -> Unit,
) {
    if (layoutMode == PosLayoutMode.EXPANDED) {
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
                    onRetry = onRetry,
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
                Text("${content.cart.itemCountLabel} · ${content.cart.payableLabel}")
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
                    onRetry = onRetry,
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
    columns: GridCells,
    modifier: Modifier = Modifier,
    gridBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier.semantics { stateDescription = "Cashier demo visuals" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CashierHeader(content.demoData)
        OutlinedTextField(
            value = content.query,
            onValueChange = onQueryChange,
            label = { Text("Search products") },
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
            label = { Text("Barcode (manual or HID scanner)") },
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
                FilterChip(
                    selected = category.id == content.selectedCategoryId,
                    onClick = { onCategorySelected(category) },
                    label = { Text(category.label) },
                    modifier = Modifier
                        .heightIn(min = PosDimensions.touchTarget)
                        .semantics { contentDescription = "Cashier category ${category.label}" },
                )
            }
        }
        LazyVerticalGrid(
            columns = columns,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = gridBottomPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(content.products, key = { it.itemCode }) { product ->
                CashierProductCard(
                    product = product,
                    onClick = { onProductSelected(product) },
                )
            }
        }
    }
}

@Composable
private fun CashierHeader(demoData: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Cashier",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Static sale composition",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (demoData) DemoBadge()
    }
}

@Composable
private fun CashierProductCard(
    product: CashierProduct,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PosDimensions.touchTarget)
            .semantics { contentDescription = "Add ${product.itemName} to cart" }
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
                Text(product.priceSnapshotLabel(), style = MaterialTheme.typography.bodyMedium)
                Text(
                    product.stockSnapshotLabel(),
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
        Text("Cashier unavailable", style = MaterialTheme.typography.headlineMedium)
        customerState?.let { state ->
            val label = when (val selection = state.selection) {
                is com.rotiropi.pos_erpnext.ui.customer.CustomerSelection.WalkIn -> selection.displayName.ifBlank { "Walk-in customer" }
                is com.rotiropi.pos_erpnext.ui.customer.CustomerSelection.Registered -> selection.displayLabel
                null -> "Customer"
            }
            Button(onClick = onOpenCustomerSheet, modifier = Modifier.heightIn(min = PosDimensions.touchTarget).testTag("customer-open")) { Text(label) }
        }
        Text("Catalog, authoritative payable, and payment modes are not integrated.")
    }
}

@Composable
private fun CashierError(message: String, modifier: Modifier, onRetry: () -> Unit) {
    CashierStatePanel(
        title = "Cashier could not load",
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
                    Text("Retry")
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
            text = "Demo data",
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
