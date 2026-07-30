package com.rotiropi.pos_erpnext.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

sealed interface ProductsUiState {
    data object Loading : ProductsUiState
    data object Empty : ProductsUiState
    data object Offline : ProductsUiState
    data object Unavailable : ProductsUiState
    data class Error(val message: String) : ProductsUiState
    data class Populated(val content: ProductsContent) : ProductsUiState
}

data class ProductsContent(
    val query: String,
    val categories: List<ProductCategory>,
    val selectedCategoryId: String?,
    val products: List<ProductItem>,
    val selectedProduct: ProductItem?,
    val demoData: Boolean,
)

data class ProductCategory(
    val id: String,
    val label: String,
)

data class ProductItem(
    val itemCode: String,
    val itemName: String,
    val itemGroup: String,
    val description: String,
    val priceList: String,
    val price: String,
    val currency: String,
    val warehouse: String,
    val availableQuantity: String,
    val uom: String,
)

fun productGridColumns(layoutMode: PosLayoutMode): Int =
    if (layoutMode == PosLayoutMode.EXPANDED) 4 else 2

fun ProductItem.priceSnapshotLabel(): String =
    "$currency $price · $priceList snapshot"

fun ProductItem.stockSnapshotLabel(): String =
    "$availableQuantity $uom · $warehouse stock snapshot"

@Composable
fun ProductsScreen(
    state: ProductsUiState,
    layoutMode: PosLayoutMode,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onCategorySelected: (ProductCategory) -> Unit = {},
    onProductSelected: (ProductItem) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    when (state) {
        ProductsUiState.Loading -> ProductsStatePanel(
            message = "Loading product snapshots",
            stateLabel = "Loading",
            modifier = modifier.semantics { contentDescription = "Loading products" },
            showProgress = true,
        )
        ProductsUiState.Empty -> ProductsStatePanel(
            message = "No products found",
            supportingMessage = "Change the search or category filter.",
            stateLabel = "Empty",
            modifier = modifier,
        )
        ProductsUiState.Offline -> ProductsStatePanel(
            message = "Products are offline",
            supportingMessage = "Reconnect, then retry. Cached inventory is not shown.",
            stateLabel = "Offline",
            modifier = modifier,
            actionLabel = "Retry",
            onAction = onRetry,
        )
        ProductsUiState.Unavailable -> ProductsStatePanel(
            message = "Products unavailable",
            supportingMessage = "Catalog integration is not active. Product, price, and stock data are not cached locally.",
            stateLabel = "Unavailable",
            modifier = modifier,
        )
        is ProductsUiState.Error -> ProductsStatePanel(
            message = state.message,
            supportingMessage = "Try loading the product catalog again.",
            stateLabel = "Error",
            modifier = modifier,
            actionLabel = "Retry",
            onAction = onRetry,
        )
        is ProductsUiState.Populated -> ProductsPopulated(
            content = state.content,
            layoutMode = layoutMode,
            modifier = modifier,
            onQueryChange = onQueryChange,
            onCategorySelected = onCategorySelected,
            onProductSelected = onProductSelected,
        )
    }
}

@Composable
private fun ProductsPopulated(
    content: ProductsContent,
    layoutMode: PosLayoutMode,
    modifier: Modifier,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (ProductCategory) -> Unit,
    onProductSelected: (ProductItem) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PosDimensions.screenPadding)
            .semantics { stateDescription = "Product snapshots" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProductsHeader(content.demoData)
        OutlinedTextField(
            value = content.query,
            onValueChange = onQueryChange,
            label = { Text("Search products") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PosDimensions.touchTarget),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(content.categories.size, key = { content.categories[it].id }) { index ->
                val category = content.categories[index]
                FilterChip(
                    selected = category.id == content.selectedCategoryId,
                    onClick = { onCategorySelected(category) },
                    label = { Text(category.label) },
                    modifier = Modifier
                        .heightIn(min = PosDimensions.touchTarget)
                        .semantics { contentDescription = "Filter category ${category.label}" },
                )
            }
        }
        if (layoutMode == PosLayoutMode.EXPANDED) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                if (maxWidth >= 520.dp) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ProductGrid(
                            products = content.products,
                            columns = GridCells.Adaptive(160.dp),
                            onProductSelected = onProductSelected,
                            modifier = Modifier.weight(1f),
                        )
                        ProductDetail(
                            product = content.selectedProduct,
                            modifier = Modifier
                                .width(320.dp)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ProductGrid(
                            products = content.products,
                            columns = GridCells.Fixed(1),
                            onProductSelected = onProductSelected,
                            modifier = Modifier.weight(1f),
                        )
                        content.selectedProduct?.let {
                            ProductDetail(
                                product = it,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProductGrid(
                    products = content.products,
                    columns = GridCells.Fixed(productGridColumns(layoutMode)),
                    onProductSelected = onProductSelected,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                content.selectedProduct?.let {
                    ProductDetail(
                        product = it,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductsHeader(demoData: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Products",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "ERPNext catalog snapshots",
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
private fun ProductGrid(
    products: List<ProductItem>,
    columns: GridCells,
    onProductSelected: (ProductItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(products, key = { it.itemCode }) { product ->
            ProductCard(product, onClick = { onProductSelected(product) })
        }
    }
}

@Composable
private fun ProductCard(product: ProductItem, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Product ${product.itemName}" }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = product.itemCode.take(2).uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(product.itemName, style = MaterialTheme.typography.titleMedium)
                Text(
                    product.itemCode,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
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
private fun ProductDetail(product: ProductItem?, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Item details",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (product == null) {
                Text(
                    "Select a product to inspect its ERPNext identity and snapshots.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DetailRow("Item", "${product.itemName} · ${product.itemCode}")
                DetailRow("Item Group", product.itemGroup)
                DetailRow("Description", product.description)
                DetailRow("Price List", product.priceSnapshotLabel())
                DetailRow("Warehouse", product.stockSnapshotLabel())
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ProductsStatePanel(
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
            Text(
                text = "Products",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
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
