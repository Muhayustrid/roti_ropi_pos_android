package com.rotiropi.pos_erpnext.ui.returning

import com.rotiropi.pos_erpnext.data.SaleReadResult
import com.rotiropi.pos_erpnext.data.api.ApiCallCancellation
import com.rotiropi.pos_erpnext.data.api.ReturnQuoteDto
import com.rotiropi.pos_erpnext.data.api.ReturnabilityDto
import com.rotiropi.pos_erpnext.recovery.RecoveryExecution
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReturnViewModelTest {
    @Test fun `closing then reopening the same sale rejects its old quote`() =
        assertInFlightQuoteIsRejected { viewModel ->
            viewModel.clear()
            viewModel.show("INV-1", listOf(row()), policy(), listOf("Cash", "Bank"), true)
        }

    @Test fun `input changes reject an old quote`() {
        assertInFlightQuoteIsRejected { it.updateQuantity("row-1", "2") }
        assertInFlightQuoteIsRejected { it.updateReason("Wrong size") }
        assertInFlightQuoteIsRejected { it.updateRefundMode("Bank") }
    }

    @Test fun `sale or profile refresh rejects an old quote for the same invoice`() =
        assertInFlightQuoteIsRejected { viewModel ->
            viewModel.show("INV-1", listOf(row()), policy(), listOf("Cash", "Bank"), true)
        }

    private fun assertInFlightQuoteIsRejected(mutate: (ReturnViewModel) -> Unit) {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val quoteStarted = CountDownLatch(1)
        val releaseQuote = CountDownLatch(1)
        val quoteFinished = CountDownLatch(1)
        val viewModel = ReturnViewModel(
            dispatcher,
            quoteReturn = { _, _: ApiCallCancellation ->
                quoteStarted.countDown()
                releaseQuote.await()
                SaleReadResult.Success(ReturnQuoteDto("INV-1", emptyList(), "-1", "1", emptyList(), "Cash"))
                    .also { quoteFinished.countDown() }
            },
            createReturn = { RecoveryExecution.BlockedIdentity },
        )
        try {
            viewModel.show("INV-1", listOf(row()), policy(), listOf("Cash", "Bank"), true)
            viewModel.updateReason("Damaged")
            viewModel.updateQuantity("row-1", "1")
            viewModel.updateRefundMode("Cash")
            viewModel.requestQuote()
            assertTrue(quoteStarted.await(5, TimeUnit.SECONDS))
            mutate(viewModel)
            releaseQuote.countDown()
            assertTrue(quoteFinished.await(5, TimeUnit.SECONDS))
            runBlocking { withContext(dispatcher) {} }

            assertEquals(null, (viewModel.state.value as ReturnUiState.Editing).quote)
        } finally {
            releaseQuote.countDown()
            dispatcher.close()
        }
    }

    @Test fun `single refund mode is server selected and never sent by the client`() {
        var refundMode: String? = "unexpected"
        val viewModel = ReturnViewModel(
            UnconfinedTestDispatcher(),
            quoteReturn = { request, _ -> refundMode = request.refund_mode; SaleReadResult.Failure("unused") },
            createReturn = { RecoveryExecution.BlockedIdentity },
        )
        viewModel.show("INV-1", listOf(row()), policy(), listOf("Cash"), false)
        viewModel.updateReason("Damaged")
        viewModel.updateQuantity("row-1", "1")
        viewModel.requestQuote()

        assertEquals(null, refundMode)
    }

    @Test fun `multiple refund modes require a valid selection`() {
        val viewModel = ReturnViewModel(UnconfinedTestDispatcher(), { _, _ -> SaleReadResult.Failure("unused") }, { RecoveryExecution.BlockedIdentity })
        viewModel.show("INV-1", listOf(row()), policy(), listOf("Cash", "Bank"), true)
        viewModel.updateReason("Damaged")
        viewModel.updateQuantity("row-1", "1")
        viewModel.requestQuote()

        assertEquals("refund_mode_required", (viewModel.state.value as ReturnUiState.Editing).error)
        viewModel.updateRefundMode("Unknown")
        viewModel.requestQuote()
        assertTrue((viewModel.state.value as ReturnUiState.Editing).error == "refund_mode_not_allowed")
    }

    private fun row() = ReturnabilityDto("row-1", "BREAD", "2", "0", "2", "Nos", emptyList(), emptyList(), true)
    private fun policy() = ReturnQuantityPolicy(2, "0.01", "999999999999.99", "ascii_decimal_dot", "reject", "return-quantity/v1")
}
