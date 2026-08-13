package com.rotiropi.pos_erpnext.ui.cashier

import android.content.Context
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.ui.UiText
import com.rotiropi.pos_erpnext.ui.resolve
import com.rotiropi.pos_erpnext.ui.payment.CheckoutUiState
import com.rotiropi.pos_erpnext.ui.receipt.ReceiptContent

const val MAX_CART_ROWS = 50

sealed interface CashierUiState {
    data object Unavailable : CashierUiState
    data class Active(val content: CashierContent) : CashierUiState
    data class Receipt(val content: ReceiptContent) : CashierUiState
    data class Error(val message: String) : CashierUiState
}

data class CashierContent(
    val query: String,
    val barcode: String,
    val categories: List<CashierCategory>,
    val selectedCategoryId: String?,
    val products: List<CashierProduct>,
    val cart: CartSnapshot,
    val checkoutState: CheckoutUiState,
    val catalogLoading: Boolean = false,
    val catalogHasMore: Boolean = false,
    val catalogError: UiText? = null,
    val scanLoading: Boolean = false,
    val scanError: UiText? = null,
    val quoteLoading: Boolean = false,
    val quoteError: UiText? = null,
    val invalidQuantityForLine: String? = null,
)

data class CashierCategory(
    val id: String,
    val label: UiText,
)

data class CashierProduct(
    val itemCode: String,
    val itemName: String,
    val categoryId: String,
    val price: String,
    val currency: String,
    /**
     * A [UiText] because the catalog endpoint does not name a price list: server responses
     * that do carry one pass it through as [UiText.Raw], and the Cashier's own stand-in
     * label resolves from resources so it is not stuck in English.
     */
    val priceList: UiText,
    val availableQuantity: String,
    val uom: String,
    val warehouse: String,
)

data class CartLine(
    val id: String,
    val itemCode: String,
    val itemName: String,
    val quantity: String,
    val priceLabel: UiText,
    val uom: String,
    val batchNo: String? = null,
    val serialNo: String? = null,
    val warningLabel: String? = null,
)

data class CartSnapshot(
    val lines: List<CartLine>,
    val itemCountLabel: UiText,
    val payableLabel: UiText,
) {
    val visibleLines: List<CartLine> = lines.take(MAX_CART_ROWS)
}

/**
 * The label takes a [Context] rather than being `@Composable` so the same rendering can be
 * asserted from a unit test. Currency, amount, price-list, warehouse, and UoM are all
 * server-owned and pass through as format arguments.
 */
fun CashierProduct.priceSnapshotLabel(context: Context): String =
    context.getString(R.string.cashier_price_snapshot, currency, price, priceList.resolve(context))

fun CashierProduct.stockSnapshotLabel(context: Context): String =
    context.getString(R.string.cashier_stock_snapshot, availableQuantity, uom, warehouse)
