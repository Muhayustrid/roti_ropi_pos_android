package com.rotiropi.pos_erpnext.ui.cashier

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
    val demoData: Boolean,
    val catalogLoading: Boolean = false,
    val catalogHasMore: Boolean = false,
    val catalogError: String? = null,
    val scanLoading: Boolean = false,
    val scanError: String? = null,
    val quoteLoading: Boolean = false,
    val quoteError: String? = null,
    val invalidQuantityForLine: String? = null,
)

data class CashierCategory(
    val id: String,
    val label: String,
)

data class CashierProduct(
    val itemCode: String,
    val itemName: String,
    val categoryId: String,
    val price: String,
    val currency: String,
    val priceList: String,
    val availableQuantity: String,
    val uom: String,
    val warehouse: String,
)

data class CartLine(
    val id: String,
    val itemCode: String,
    val itemName: String,
    val quantity: String,
    val priceLabel: String,
    val uom: String,
    val batchNo: String? = null,
    val serialNo: String? = null,
    val warningLabel: String? = null,
)

data class CartSnapshot(
    val lines: List<CartLine>,
    val itemCountLabel: String,
    val payableLabel: String,
) {
    val visibleLines: List<CartLine> = lines.take(MAX_CART_ROWS)
}

fun CashierProduct.priceSnapshotLabel(): String =
    "$currency $price · $priceList server snapshot"

fun CashierProduct.stockSnapshotLabel(): String =
    "$availableQuantity $uom · $warehouse server stock snapshot"
