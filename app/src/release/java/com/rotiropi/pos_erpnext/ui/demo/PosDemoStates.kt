package com.rotiropi.pos_erpnext.ui.demo

import com.rotiropi.pos_erpnext.ui.cashier.CashierUiState
import com.rotiropi.pos_erpnext.ui.products.ProductsUiState
import com.rotiropi.pos_erpnext.ui.reports.ReportsUiState

/**
 * Release stub. Populated fixtures live only in `app/src/debug/`, so the release build
 * exposes the same shape with unavailable states and no demo toggle.
 */
object PosDemoStates {

    const val supported = false

    val outletLabel: String? = null
    val userSessionLabel: String? = null

    val products: ProductsUiState = ProductsUiState.Unavailable
    val cashier: CashierUiState = CashierUiState.Unavailable
    val reports: ReportsUiState = ReportsUiState.Unavailable
}
