package com.rotiropi.pos_erpnext.ui

import android.content.Context
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.products.ProductItem
import com.rotiropi.pos_erpnext.ui.products.priceSnapshotLabel
import com.rotiropi.pos_erpnext.ui.products.productGridColumns
import com.rotiropi.pos_erpnext.ui.products.stockSnapshotLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class ProductsStateTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun product_grid_expands_from_phone_to_tablet() {
        assertEquals(2, productGridColumns(PosLayoutMode.COMPACT))
        assertEquals(4, productGridColumns(PosLayoutMode.EXPANDED))
    }

    @Test
    @Config(qualifiers = "en")
    fun product_labels_keep_price_and_stock_non_authoritative() {
        val product = product()

        assertEquals("IDR 25000 · Outlet Retail snapshot", product.priceSnapshotLabel(context))
        assertEquals("18 Pack · Outlet 01 - RR stock snapshot", product.stockSnapshotLabel(context))
    }

    /**
     * Indonesian lives in `values/`, so any locale without its own translation resolves to it.
     * The server-owned currency, amount, price list, warehouse, quantity, and UoM are unchanged.
     */
    @Test
    @Config(qualifiers = "fr")
    fun product_labels_fall_back_to_indonesian_and_keep_server_values() {
        val product = product()

        assertEquals("IDR 25000 · Cuplikan Outlet Retail", product.priceSnapshotLabel(context))
        assertEquals("18 Pack · Cuplikan stok Outlet 01 - RR", product.stockSnapshotLabel(context))
    }

    private fun product() = ProductItem(
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
}
