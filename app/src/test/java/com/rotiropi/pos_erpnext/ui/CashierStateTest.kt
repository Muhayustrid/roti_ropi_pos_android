package com.rotiropi.pos_erpnext.ui

import com.rotiropi.pos_erpnext.ui.cashier.CartLine
import com.rotiropi.pos_erpnext.ui.cashier.CartSnapshot
import com.rotiropi.pos_erpnext.ui.cashier.CashierProduct
import com.rotiropi.pos_erpnext.ui.cashier.priceSnapshotLabel
import com.rotiropi.pos_erpnext.ui.cashier.stockSnapshotLabel
import com.rotiropi.pos_erpnext.ui.receipt.ReceiptContent
import org.junit.Assert.assertEquals
import org.junit.Test

class CashierStateTest {

    @Test
    fun cart_visuals_are_bounded_to_fifty_rows() {
        val cart = CartSnapshot(
            lines = (1..55).map { index ->
                CartLine(
                    id = "line-$index",
                    itemCode = "ITEM-$index",
                    itemName = "Item $index",
                    quantity = "1",
                    priceLabel = "IDR 1,000",
                    uom = "Pack",
                )
            },
            itemCountLabel = "55 items",
            payableLabel = "Demo total IDR 55,000",
        )

        assertEquals(50, cart.visibleLines.size)
        assertEquals("ITEM-50", cart.visibleLines.last().itemCode)
    }

    @Test
    fun cashier_product_labels_remain_non_authoritative() {
        val product = CashierProduct(
            itemCode = "CROISSANT-PACK",
            itemName = "Croissant Pack",
            categoryId = "pastry",
            price = "25,000",
            currency = "IDR",
            priceList = "Outlet Retail",
            availableQuantity = "18",
            uom = "Pack",
            warehouse = "Outlet 01 - RR",
        )

        assertEquals(
            "IDR 25,000 · Outlet Retail server snapshot",
            product.priceSnapshotLabel(),
        )
        assertEquals(
            "18 Pack · Outlet 01 - RR server stock snapshot",
            product.stockSnapshotLabel(),
        )
    }

    @Test
    fun receipt_keeps_server_change_as_supplied() {
        val receipt = ReceiptContent(
            saleId = "SINV-0001",
            customerLabel = "Walk-in Customer",
            total = "IDR 55,000",
            paid = "IDR 55,000",
            changeAmount = "IDR 0",
            status = "Paid",
            demoData = true,
        )

        assertEquals("IDR 0", receipt.changeAmount)
    }
}
