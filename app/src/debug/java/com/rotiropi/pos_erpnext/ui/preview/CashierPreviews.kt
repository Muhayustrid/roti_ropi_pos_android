package com.rotiropi.pos_erpnext.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.ui.UiText
import com.rotiropi.pos_erpnext.ui.cashier.CartLine
import com.rotiropi.pos_erpnext.ui.cashier.CartSnapshot
import com.rotiropi.pos_erpnext.ui.cashier.CashierCategory
import com.rotiropi.pos_erpnext.ui.cashier.CashierContent
import com.rotiropi.pos_erpnext.ui.cashier.CashierProduct
import com.rotiropi.pos_erpnext.ui.cashier.CashierScreen
import com.rotiropi.pos_erpnext.ui.cashier.CashierUiState
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.payment.CheckoutPanel
import com.rotiropi.pos_erpnext.ui.payment.CheckoutUiState
import com.rotiropi.pos_erpnext.ui.receipt.ReceiptContent
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import com.rotiropi.pos_erpnext.ui.theme.PosTheme

private val cashierProduct = CashierProduct(
    itemCode = "CROISSANT-PACK",
    itemName = "Croissant Pack",
    categoryId = "pastry",
    price = "25,000",
    currency = "IDR",
    priceList = UiText.Raw("Outlet Retail"),
    availableQuantity = "18",
    uom = "Pack",
    warehouse = "Outlet 01 - RR",
)

private val cashierDemoContent = CashierContent(
    query = "",
    barcode = "",
    categories = listOf(
        CashierCategory("all", UiText.Raw("All")),
        CashierCategory("pastry", UiText.Raw("Pastry")),
        CashierCategory("drinks", UiText.Raw("Drinks")),
    ),
    selectedCategoryId = "all",
    products = listOf(
        cashierProduct,
        cashierProduct.copy(
            itemCode = "PAIN-AU-CHOCOLAT",
            itemName = "Pain au Chocolat",
            price = "18,000",
            availableQuantity = "9",
            uom = "Nos",
        ),
        cashierProduct.copy(
            itemCode = "COFFEE-LATTE",
            itemName = "Coffee Latte",
            categoryId = "drinks",
            price = "28,000",
            availableQuantity = "12",
            uom = "Cup",
        ),
        cashierProduct.copy(
            itemCode = "CINNAMON-ROLL",
            itemName = "Cinnamon Roll",
            price = "22,000",
            availableQuantity = "6",
            uom = "Nos",
        ),
    ),
    cart = CartSnapshot(
        lines = listOf(
            CartLine(
                id = "line-croissant",
                itemCode = "CROISSANT-PACK",
                itemName = "Croissant Pack",
                quantity = "2",
                priceLabel = UiText.Raw("Demo line IDR 50,000"),
                uom = "Pack",
            ),
            CartLine(
                id = "line-coffee",
                itemCode = "COFFEE-LATTE",
                itemName = "Coffee Latte",
                quantity = "1",
                priceLabel = UiText.Raw("Demo line IDR 28,000"),
                uom = "Cup",
            ),
        ),
        itemCountLabel = UiText.Raw("3 items"),
        payableLabel = UiText.Raw("Demo total IDR 78,000"),
    ),
    checkoutState = CheckoutUiState.Unavailable,
)

private val cashierReceipt = ReceiptContent(
    saleId = "SINV-DEMO-0001",
    customerLabel = "Walk-in Customer",
    total = "IDR 78,000",
    paid = "IDR 78,000",
    changeAmount = "IDR 0",
    status = R.string.sale_status_paid,
)

@Preview(name = "Cashier compact cart", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun CashierCompactPreview() {
    PosTheme {
        CashierScreen(
            state = CashierUiState.Active(cashierDemoContent),
            layoutMode = PosLayoutMode.COMPACT,
            cartVisible = true,
        )
    }
}

@Preview(name = "Cashier compact dark", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun CashierCompactDarkPreview() {
    PosTheme(darkTheme = true, accent = PosAccent.TEAL) {
        CashierScreen(CashierUiState.Active(cashierDemoContent), PosLayoutMode.COMPACT)
    }
}

@Preview(name = "Cashier expanded", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
fun CashierExpandedPreview() {
    PosTheme(accent = PosAccent.BLUE) {
        CashierScreen(CashierUiState.Active(cashierDemoContent), PosLayoutMode.EXPANDED)
    }
}

@Preview(name = "Cashier font 1.5x", widthDp = 800, heightDp = 1280, fontScale = 1.5f)
@Composable
fun CashierFontScalePreview() {
    PosTheme(accent = PosAccent.TEAL) {
        CashierScreen(CashierUiState.Active(cashierDemoContent), PosLayoutMode.EXPANDED)
    }
}

@Preview(name = "Checkout visual states", widthDp = 600, heightDp = 1200, showBackground = true)
@Composable
fun CheckoutStatesPreview() {
    PosTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            CheckoutPanel(CheckoutUiState.Unavailable)
            CheckoutPanel(CheckoutUiState.OfflineNotSubmitted)
            CheckoutPanel(CheckoutUiState.PriceChanged(UiText.Raw("Server price changed. Review cart snapshots."), emptyMap()))
            CheckoutPanel(CheckoutUiState.Submitting)
            CheckoutPanel(CheckoutUiState.Error(UiText.Raw("Sale was not submitted.")))
        }
    }
}

@Preview(name = "Terminal receipt", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun ReceiptPreview() {
    PosTheme {
        CashierScreen(CashierUiState.Receipt(cashierReceipt), PosLayoutMode.COMPACT)
    }
}
