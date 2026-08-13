package com.rotiropi.pos_erpnext.ui.demo

import com.rotiropi.pos_erpnext.ui.UiText
import com.rotiropi.pos_erpnext.ui.cashier.CartLine
import com.rotiropi.pos_erpnext.ui.cashier.CartSnapshot
import com.rotiropi.pos_erpnext.ui.cashier.CashierCategory
import com.rotiropi.pos_erpnext.ui.cashier.CashierContent
import com.rotiropi.pos_erpnext.ui.cashier.CashierProduct
import com.rotiropi.pos_erpnext.ui.cashier.CashierUiState
import com.rotiropi.pos_erpnext.ui.payment.CheckoutUiState
import com.rotiropi.pos_erpnext.ui.products.ProductCategory
import com.rotiropi.pos_erpnext.ui.products.ProductItem
import com.rotiropi.pos_erpnext.ui.products.ProductsContent
import com.rotiropi.pos_erpnext.ui.products.ProductsUiState
import com.rotiropi.pos_erpnext.ui.reports.ReportBreakdown
import com.rotiropi.pos_erpnext.ui.reports.ReportChartBar
import com.rotiropi.pos_erpnext.ui.reports.ReportMetric
import com.rotiropi.pos_erpnext.ui.reports.ReportPeriod
import com.rotiropi.pos_erpnext.ui.reports.ReportTopProduct
import com.rotiropi.pos_erpnext.ui.reports.ReportsContent
import com.rotiropi.pos_erpnext.ui.reports.ReportsUiState

/**
 * Synthetic populated layouts for debug builds and previews only. Every state is labeled
 * `Demo data`; the release source set replaces this object with an unsupported stub so no
 * fixture is ever packaged. See `docs/mobile-pos/testing-strategy.md`.
 */
object PosDemoStates {

    const val supported = true

    val outletLabel: String? = "Outlet Menteng"
    val userSessionLabel: String? = "Ayu · Open session"

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

    val products: ProductsUiState = ProductsUiState.Populated(
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

    val cashier: CashierUiState = CashierUiState.Active(
        CashierContent(
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
            demoData = true,
        )
    )

    val reports: ReportsUiState = ReportsUiState.Content(
        ReportsContent(
            selectedPeriod = ReportPeriod.TODAY,
            metrics = listOf(
                ReportMetric("net-sales", "Net sales", "IDR 825,000", "Sales today"),
                ReportMetric("total-orders", "Total orders", "18", "Paid transactions"),
                ReportMetric("average-order", "Average order", "IDR 45,833", "Per completed sale"),
            ),
            breakdown = listOf(
                ReportBreakdown("cash", "Cash", "IDR 525,000"),
                ReportBreakdown("bank", "Bank Transfer", "IDR 300,000"),
            ),
            chartBars = listOf(
                ReportChartBar("08:00", "08:00", "IDR 120,000", 0.4f),
                ReportChartBar("10:00", "10:00", "IDR 210,000", 0.7f),
                ReportChartBar("12:00", "12:00", "IDR 300,000", 1.0f),
                ReportChartBar("14:00", "14:00", "IDR 195,000", 0.65f),
            ),
            chartSummary = "Peak sales reached IDR 300,000 around 12:00.",
            topProducts = listOf(
                ReportTopProduct("croissant", "Croissant Pack", "12 sold"),
                ReportTopProduct("coffee", "Coffee Beans", "8 sold"),
            ),
            demoData = true,
        )
    )
}
