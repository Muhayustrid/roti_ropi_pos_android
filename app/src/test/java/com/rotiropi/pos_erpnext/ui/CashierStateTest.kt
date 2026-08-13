package com.rotiropi.pos_erpnext.ui

import android.content.Context
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.ui.UiText
import com.rotiropi.pos_erpnext.ui.uiText
import com.rotiropi.pos_erpnext.ui.cashier.CartLine
import com.rotiropi.pos_erpnext.ui.cashier.CartSnapshot
import com.rotiropi.pos_erpnext.ui.cashier.CashierProduct
import com.rotiropi.pos_erpnext.ui.cashier.priceSnapshotLabel
import com.rotiropi.pos_erpnext.ui.cashier.stockSnapshotLabel
import com.rotiropi.pos_erpnext.ui.receipt.ReceiptContent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class CashierStateTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun cart_visuals_are_bounded_to_fifty_rows() {
        val cart = CartSnapshot(
            lines = (1..55).map { index ->
                CartLine(
                    id = "line-$index",
                    itemCode = "ITEM-$index",
                    itemName = "Item $index",
                    quantity = "1",
                    priceLabel = UiText.Raw("IDR 1,000"),
                    uom = "Pack",
                )
            },
            itemCountLabel = UiText.Raw("55 items"),
            payableLabel = UiText.Raw("Demo total IDR 55,000"),
        )

        assertEquals(50, cart.visibleLines.size)
        assertEquals("ITEM-50", cart.visibleLines.last().itemCode)
    }

    @Test
    @Config(qualifiers = "en")
    fun cashier_product_labels_remain_non_authoritative() {
        val product = product()

        assertEquals(
            "IDR 25,000 · Outlet Retail server snapshot",
            product.priceSnapshotLabel(context),
        )
        assertEquals(
            "18 Pack · Outlet 01 - RR server stock snapshot",
            product.stockSnapshotLabel(context),
        )
    }

    /**
     * Indonesian lives in `values/`, so any locale without its own translation resolves to it.
     * Currency, amount, price list, warehouse, quantity, and UoM stay exactly as the server
     * supplied them.
     */
    @Test
    @Config(qualifiers = "fr")
    fun cashier_product_labels_fall_back_to_indonesian_and_keep_server_values() {
        val product = product()

        assertEquals("IDR 25,000 · Cuplikan server Outlet Retail", product.priceSnapshotLabel(context))
        assertEquals("18 Pack · Cuplikan stok server Outlet 01 - RR", product.stockSnapshotLabel(context))
    }

    /**
     * The catalog endpoint names no price list, so the Cashier supplies its own stand-in
     * label. Being a resource rather than text, it follows the interface language while the
     * currency and amount beside it stay exactly as the server sent them.
     */
    @Test
    @Config(qualifiers = "fr")
    fun cashier_own_price_list_label_follows_the_interface_language() {
        val product = product().copy(priceList = uiText(R.string.checkout_server_price))

        assertEquals("IDR 25,000 · Cuplikan server Harga server", product.priceSnapshotLabel(context))
    }

    @Test
    fun receipt_keeps_server_change_as_supplied() {
        val receipt = ReceiptContent(
            saleId = "SINV-0001",
            customerLabel = "Walk-in Customer",
            total = "IDR 55,000",
            paid = "IDR 55,000",
            changeAmount = "IDR 0",
            status = R.string.sale_status_paid,
        )

        assertEquals("IDR 0", receipt.changeAmount)
    }

    private fun product() = CashierProduct(
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
}
