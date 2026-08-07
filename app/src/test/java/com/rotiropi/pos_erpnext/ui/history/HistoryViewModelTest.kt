package com.rotiropi.pos_erpnext.ui.history

import com.rotiropi.pos_erpnext.data.SaleHistoryPage
import com.rotiropi.pos_erpnext.data.SaleReadResult
import com.rotiropi.pos_erpnext.data.api.PageDto
import com.rotiropi.pos_erpnext.data.api.SaleStatus
import com.rotiropi.pos_erpnext.data.api.SaleSummaryDto
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @Test fun `pagination is profile scoped and duplicate rows are not added`() {
        val calls = mutableListOf<Pair<HistoryIdentity, Int>>()
        val viewModel = HistoryViewModel(
            dispatcher = UnconfinedTestDispatcher(),
            listSales = { identity, _, start, _ ->
                calls += identity to start
                SaleReadResult.Success(SaleHistoryPage(listOf(sale("INV-1")), PageDto(start, 20, start == 0)))
            },
        )

        viewModel.bind(HistoryIdentity("cashier@example.com", "OUTLET-01"))
        viewModel.loadMore()

        val state = viewModel.state.value as HistoryUiState.Content
        assertEquals(listOf(0, 1), calls.map { it.second })
        assertTrue(calls.all { it.first.posProfile == "OUTLET-01" })
        assertEquals(listOf("INV-1"), state.sales.map { it.name })
    }

    private fun sale(name: String) = SaleSummaryDto(
        doctype = "POS Invoice", name = name, status = SaleStatus.PAID, customer = "Walk In",
        walk_in_customer_name = "Ari", currency = "IDR", grand_total = "100", paid_amount = "100",
        change_amount = "0", posting_date = "2026-08-07", posting_time = "10:00:00",
    )
}
