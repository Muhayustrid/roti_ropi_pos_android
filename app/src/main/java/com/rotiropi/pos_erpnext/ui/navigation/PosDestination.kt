package com.rotiropi.pos_erpnext.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.rotiropi.pos_erpnext.R

/**
 * The three top-level destinations, mirroring the approved prototype's `TopLevel`.
 * Closing is a child route reached from More, not a root.
 */
enum class PosDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    CASHIER("cashier", R.string.nav_cashier, R.drawable.ic_cashier),
    HISTORY("history", R.string.nav_history, R.drawable.ic_history),
    MORE("more", R.string.nav_more, R.drawable.ic_more),
}

enum class PosLayoutMode {
    COMPACT,
    EXPANDED,
}

/**
 * The root whose tab stays selected while a child route is showing. Child routes have
 * no tab of their own, so without this the bar would highlight the start destination
 * while the user is two levels into More.
 */
fun parentDestinationOf(route: String?): PosDestination = when {
    route == null -> PosDestination.CASHIER
    route.startsWith("sale/") || route.startsWith("return/") -> PosDestination.HISTORY
    route == "closing" -> PosDestination.MORE
    else -> PosDestination.CASHIER
}

fun posLayoutModeForWidth(widthDp: Int): PosLayoutMode =
    if (widthDp >= 600) PosLayoutMode.EXPANDED else PosLayoutMode.COMPACT
