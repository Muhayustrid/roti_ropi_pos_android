package com.rotiropi.pos_erpnext.ui

import com.rotiropi.pos_erpnext.ui.dashboard.dashboardGridColumns
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.products.ProductItem
import com.rotiropi.pos_erpnext.ui.products.priceSnapshotLabel
import com.rotiropi.pos_erpnext.ui.products.productGridColumns
import com.rotiropi.pos_erpnext.ui.products.stockSnapshotLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardProductsStateTest {

    @Test
    fun dashboard_grid_expands_without_changing_compact_density() {
        assertEquals(2, dashboardGridColumns(PosLayoutMode.COMPACT))
        assertEquals(4, dashboardGridColumns(PosLayoutMode.EXPANDED))
    }

    @Test
    fun product_grid_expands_from_phone_to_tablet() {
        assertEquals(2, productGridColumns(PosLayoutMode.COMPACT))
        assertEquals(4, productGridColumns(PosLayoutMode.EXPANDED))
    }

    @Test
    fun product_labels_keep_price_and_stock_non_authoritative() {
        val product = ProductItem(
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

        assertEquals("IDR 25000 · Outlet Retail snapshot", product.priceSnapshotLabel())
        assertEquals("18 Pack · Outlet 01 - RR stock snapshot", product.stockSnapshotLabel())
    }
}
