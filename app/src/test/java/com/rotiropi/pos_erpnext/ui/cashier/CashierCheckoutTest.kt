package com.rotiropi.pos_erpnext.ui.cashier

import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.data.CatalogFailure
import com.rotiropi.pos_erpnext.data.CatalogPage
import com.rotiropi.pos_erpnext.data.CatalogProduct
import com.rotiropi.pos_erpnext.data.CatalogQuote
import com.rotiropi.pos_erpnext.data.CatalogQuoteRequest
import com.rotiropi.pos_erpnext.data.CatalogQuoteResult
import com.rotiropi.pos_erpnext.data.CatalogScanResult
import com.rotiropi.pos_erpnext.data.CatalogSearchResult
import com.rotiropi.pos_erpnext.data.CheckoutQuoteResult
import com.rotiropi.pos_erpnext.data.api.ApiCallCancellation
import com.rotiropi.pos_erpnext.data.api.QuoteCartRequestDto
import com.rotiropi.pos_erpnext.ui.uiText
import com.rotiropi.pos_erpnext.ui.payment.CheckoutQuote
import com.rotiropi.pos_erpnext.ui.payment.PaymentAmountPolicy
import com.rotiropi.pos_erpnext.ui.payment.PaymentMode
import com.rotiropi.pos_erpnext.ui.payment.PaymentValidationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CashierCheckoutTest {
    @Test
    fun `checkout exposes every mode and only prefills one default`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var quoteRequest: QuoteCartRequestDto? = null
        val viewModel = CashierViewModel(
            dispatcher,
            searchCatalog = { _, _ -> CatalogSearchResult.Success(CatalogPage(listOf(product()), 0, 20, false)) },
            scanCatalog = { _, _ -> CatalogScanResult.Failure(CatalogFailure.Unavailable) },
            quoteItem = { request, _ -> CatalogQuoteResult.Success(itemQuote(request)) },
            quoteCart = { request, _ ->
                quoteRequest = request
                CheckoutQuoteResult.Success(checkoutQuote())
            },
        )
        viewModel.bind(CashierIdentity("cashier", "OPEN-1", "OUTLET", "WALK-IN", "Ayu"))
        viewModel.onQueryChanged("item")
        advanceTimeBy(300)
        runCurrent()
        viewModel.onProductSelected(CashierProduct("ITEM", "Item", "all", "10", "IDR", uiText(R.string.checkout_server_price), "1", "Nos", "WH"))
        runCurrent()

        viewModel.onOpenCheckout()
        runCurrent()

        val state = (viewModel.state.value as CashierUiState.Active).content.checkoutState
        assertTrue(state is com.rotiropi.pos_erpnext.ui.payment.CheckoutUiState.Ready)
        state as com.rotiropi.pos_erpnext.ui.payment.CheckoutUiState.Ready
        assertEquals(listOf("Cash", "Card"), state.payments.map { it.modeOfPayment })
        assertEquals(listOf("10", ""), state.payments.map { it.amount })
        assertTrue(state.validation is PaymentValidationResult.Valid)
        assertEquals("Ayu", quoteRequest?.walk_in_customer_name)
    }

    private fun product() = CatalogProduct("ITEM", "Item", "", null, "Nos", "10", "IDR", "1")
    private fun itemQuote(request: CatalogQuoteRequest) = CatalogQuote(request.itemCode, request.quantity, "Nos", "1", "WH", "1", "10", "0", "10", null, emptyList())
    private fun checkoutQuote() = CheckoutQuote(
        "10", "10", "IDR", listOf(PaymentMode("Cash", true, "IDR"), PaymentMode("Card", false, "IDR")),
        PaymentAmountPolicy("IDR", 2, "0.01", "ascii_decimal_dot", "reject", "v1"), emptyList(), emptyList(), 0,
    )
}
