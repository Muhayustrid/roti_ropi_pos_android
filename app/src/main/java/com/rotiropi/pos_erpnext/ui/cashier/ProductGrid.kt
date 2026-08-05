package com.rotiropi.pos_erpnext.ui.cashier

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ProductGrid(
    products: List<CashierProduct>,
    columns: GridCells,
    onProductSelected: (CashierProduct) -> Unit,
    modifier: Modifier = Modifier,
    gridBottomPadding: Dp = 0.dp,
) {
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = gridBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(products, key = { it.itemCode }) { product ->
            CashierProductCard(
                product = product,
                onClick = { onProductSelected(product) },
            )
        }
    }
}
