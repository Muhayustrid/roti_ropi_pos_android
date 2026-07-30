package com.rotiropi.pos_erpnext.ui.navigation

import androidx.annotation.DrawableRes
import com.rotiropi.pos_erpnext.R

enum class PosDestination(
    val route: String,
    val label: String,
    @param:DrawableRes val iconRes: Int,
) {
    HOME("home", "Home", R.drawable.ic_home),
    PRODUCTS("products", "Products", R.drawable.ic_products),
    CASHIER("cashier", "Cashier", R.drawable.ic_cashier),
    REPORTS("reports", "Reports", R.drawable.ic_reports),
    MORE("more", "More", R.drawable.ic_more),
}

enum class PosLayoutMode {
    COMPACT,
    EXPANDED,
}

fun posLayoutModeForWidth(widthDp: Int): PosLayoutMode =
    if (widthDp >= 600) PosLayoutMode.EXPANDED else PosLayoutMode.COMPACT
