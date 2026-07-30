package com.rotiropi.pos_erpnext.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.products.ProductCategory
import com.rotiropi.pos_erpnext.ui.products.ProductItem
import com.rotiropi.pos_erpnext.ui.products.ProductsContent
import com.rotiropi.pos_erpnext.ui.products.ProductsScreen
import com.rotiropi.pos_erpnext.ui.products.ProductsUiState
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import com.rotiropi.pos_erpnext.ui.theme.PosTheme

private val croissant = ProductItem(
    itemCode = "CROISSANT-PACK",
    itemName = "Croissant Pack",
    itemGroup = "Pastry",
    description = "Six butter croissants",
    priceList = "Outlet Retail",
    price = "25000",
    currency = "IDR",
    warehouse = "Outlet 01 - RR",
    availableQuantity = "18",
    uom = "Pack",
)

private val productsDemoState = ProductsUiState.Populated(
    ProductsContent(
        query = "",
        categories = listOf(
            ProductCategory("all", "All"),
            ProductCategory("pastry", "Pastry"),
            ProductCategory("drinks", "Drinks"),
        ),
        selectedCategoryId = "all",
        products = listOf(
            croissant,
            croissant.copy(
                itemCode = "PAIN-AU-CHOCOLAT",
                itemName = "Pain au Chocolat",
                price = "18000",
                availableQuantity = "9",
                uom = "Nos",
            ),
            croissant.copy(
                itemCode = "COFFEE-LATTE",
                itemName = "Coffee Latte",
                itemGroup = "Drinks",
                price = "28000",
                availableQuantity = "12",
                uom = "Cup",
            ),
            croissant.copy(
                itemCode = "CINNAMON-ROLL",
                itemName = "Cinnamon Roll",
                price = "22000",
                availableQuantity = "6",
                uom = "Nos",
            ),
        ),
        selectedProduct = croissant,
        demoData = true,
    )
)

@Preview(name = "Products phone light", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun ProductsPhonePreview() {
    ProductsPreview(darkTheme = false, accent = PosAccent.BLUE, layoutMode = PosLayoutMode.COMPACT)
}

@Preview(name = "Products phone landscape 1.5x", widthDp = 800, heightDp = 360, fontScale = 1.5f)
@Composable
fun ProductsLandscapeFontScalePreview() {
    ProductsPreview(darkTheme = false, accent = PosAccent.TEAL, layoutMode = PosLayoutMode.EXPANDED)
}

@Preview(name = "Products tablet dark", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
fun ProductsTabletDarkPreview() {
    ProductsPreview(darkTheme = true, accent = PosAccent.TEAL, layoutMode = PosLayoutMode.EXPANDED)
}

@Preview(name = "Products release unavailable", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun ProductsUnavailablePreview() {
    PosTheme {
        ProductsScreen(ProductsUiState.Unavailable, PosLayoutMode.COMPACT)
    }
}

@Composable
private fun ProductsPreview(
    darkTheme: Boolean,
    accent: PosAccent,
    layoutMode: PosLayoutMode,
) {
    PosTheme(darkTheme = darkTheme, accent = accent) {
        ProductsScreen(productsDemoState, layoutMode)
    }
}
