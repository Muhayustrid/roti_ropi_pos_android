package com.rotiropi.pos_erpnext.ui.cashier

import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.data.CatalogFailure
import com.rotiropi.pos_erpnext.data.CatalogPage
import com.rotiropi.pos_erpnext.data.CatalogProduct
import com.rotiropi.pos_erpnext.data.CatalogQuote
import com.rotiropi.pos_erpnext.data.CatalogQuoteRequest
import com.rotiropi.pos_erpnext.data.CatalogQuoteResult
import com.rotiropi.pos_erpnext.data.CatalogScan
import com.rotiropi.pos_erpnext.data.CatalogScanResult
import com.rotiropi.pos_erpnext.data.CatalogSearchResult
import com.rotiropi.pos_erpnext.data.api.ApiCallCancellation
import com.rotiropi.pos_erpnext.ui.uiText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Deterministic real-thread regression tests for the CashierViewModel B1
 * synchronization fix. The ViewModel's scope runs on a real single- or
 * two-thread executor while user mutations run on the test thread, so every
 * test exercises at least two actual threads. Completion and mutation
 * interleavings are forced with latches/barriers only; no arbitrary sleeps.
 */
class CashierViewModelConcurrencyTest {

    @Test
    fun `quote completion racing with clear cannot publish or restore state`() {
        val (executor, dispatcher) = newSingleThreadDispatcher()
        val quoteEntered = CountDownLatch(1)
        val releaseQuote = CountDownLatch(1)
        val viewModel = viewModel(
            dispatcher = dispatcher,
            scanCatalog = { request, _ -> scanResult(request.value) },
            quoteItem = { request, _ ->
                quoteEntered.countDown()
                releaseQuote.await(10, TimeUnit.SECONDS)
                CatalogQuoteResult.Success(quote("1", request.itemCode))
            },
        )
        viewModel.bind(identity())
        viewModel.onBarcodeChanged("SER-1")
        viewModel.onBarcodeSubmit()
        assertTrue("quote never started", quoteEntered.await(10, TimeUnit.SECONDS))

        viewModel.clear()
        assertTrue(viewModel.state.value is CashierUiState.Unavailable)

        releaseQuote.countDown()
        executor.shutdown()
        assertTrue("completion thread did not terminate", executor.awaitTermination(10, TimeUnit.SECONDS))

        assertTrue("stale completion must not restore state", viewModel.state.value is CashierUiState.Unavailable)
    }

    @Test
    fun `quote completion racing with bind customer change cannot add stale line`() {
        val (executor, dispatcher) = newSingleThreadDispatcher()
        val quoteBEntered = CountDownLatch(1)
        val releaseQuoteB = CountDownLatch(1)
        val viewModel = viewModel(
            dispatcher = dispatcher,
            scanCatalog = { request, _ -> scanResult(request.value) },
            quoteItem = { request, _ ->
                if (request.itemCode == "ITEM-B") {
                    quoteBEntered.countDown()
                    releaseQuoteB.await(10, TimeUnit.SECONDS)
                }
                CatalogQuoteResult.Success(quote("1", request.itemCode))
            },
        )
        viewModel.bind(identity())
        viewModel.onBarcodeChanged("ITEM-A")
        viewModel.onBarcodeSubmit()
        awaitUntil({ lines(viewModel).map(CartLine::itemCode) == listOf("ITEM-A") }, "ITEM-A committed")

        viewModel.onBarcodeChanged("ITEM-B")
        viewModel.onBarcodeSubmit()
        assertTrue("quote B never started", quoteBEntered.await(10, TimeUnit.SECONDS))

        viewModel.bind(identity().copy(customer = "CUST-1"))
        releaseQuoteB.countDown()
        executor.shutdown()
        assertTrue("completion thread did not terminate", executor.awaitTermination(10, TimeUnit.SECONDS))

        assertTrue(viewModel.state.value is CashierUiState.Active)
        assertEquals(listOf("ITEM-A"), lines(viewModel).map(CartLine::itemCode))
    }

    @Test
    fun `scan completion racing with logout clear cannot restore state`() {
        val (executor, dispatcher) = newSingleThreadDispatcher()
        val scanEntered = CountDownLatch(1)
        val releaseScan = CountDownLatch(1)
        val viewModel = viewModel(
            dispatcher = dispatcher,
            scanCatalog = { _, _ ->
                scanEntered.countDown()
                releaseScan.await(10, TimeUnit.SECONDS)
                scanResult("SER-1")
            },
            quoteItem = { request, _ -> CatalogQuoteResult.Success(quote("1", request.itemCode)) },
        )
        viewModel.bind(identity())
        viewModel.onBarcodeChanged("SER-1")
        viewModel.onBarcodeSubmit()
        assertTrue("scan never started", scanEntered.await(10, TimeUnit.SECONDS))

        viewModel.clear()
        assertTrue(viewModel.state.value is CashierUiState.Unavailable)

        releaseScan.countDown()
        executor.shutdown()
        assertTrue("completion thread did not terminate", executor.awaitTermination(10, TimeUnit.SECONDS))

        assertTrue("stale scan completion must not restore state", viewModel.state.value is CashierUiState.Unavailable)
    }

    @Test
    fun `line removal racing with quote publication cannot re-add removed line`() {
        val (executor, dispatcher) = newSingleThreadDispatcher()
        val requoteEntered = CountDownLatch(1)
        val releaseRequote = CountDownLatch(1)
        val viewModel = viewModel(
            dispatcher = dispatcher,
            scanCatalog = { request, _ -> scanResult(request.value) },
            quoteItem = { request, _ ->
                if (request.quantity == "2") {
                    requoteEntered.countDown()
                    releaseRequote.await(10, TimeUnit.SECONDS)
                }
                CatalogQuoteResult.Success(quote(request.quantity, request.itemCode))
            },
        )
        viewModel.bind(identity())
        viewModel.onBarcodeChanged("ITEM-A")
        viewModel.onBarcodeSubmit()
        awaitUntil({ lines(viewModel).map(CartLine::itemCode) == listOf("ITEM-A") }, "ITEM-A committed")
        val line = lines(viewModel).single()

        viewModel.onIncreaseQuantity(line)
        assertTrue("requote never started", requoteEntered.await(10, TimeUnit.SECONDS))

        viewModel.onRemoveLine(line)
        assertTrue("line must be removed", lines(viewModel).isEmpty())

        releaseRequote.countDown()
        executor.shutdown()
        assertTrue("completion thread did not terminate", executor.awaitTermination(10, TimeUnit.SECONDS))

        assertTrue("stale completion must not re-add the removed line", lines(viewModel).isEmpty())
    }

    @Test
    fun `queue advancement racing with queue clearing cannot start a queued quote`() {
        val executor = Executors.newFixedThreadPool(2)
        val dispatcher = executor.asCoroutineDispatcher()
        val quoteAEntered = CountDownLatch(1)
        val releaseQuoteA = CountDownLatch(1)
        val scanBEntered = CountDownLatch(1)
        val quotedItems = CopyOnWriteArrayList<String>()
        val viewModel = viewModel(
            dispatcher = dispatcher,
            scanCatalog = { request, _ ->
                if (request.value == "ITEM-B") scanBEntered.countDown()
                scanResult(request.value)
            },
            quoteItem = { request, _ ->
                if (request.itemCode == "ITEM-A") {
                    quoteAEntered.countDown()
                    releaseQuoteA.await(10, TimeUnit.SECONDS)
                }
                quotedItems += request.itemCode
                CatalogQuoteResult.Success(quote("1", request.itemCode))
            },
        )
        viewModel.bind(identity())
        viewModel.onBarcodeChanged("ITEM-A")
        viewModel.onBarcodeSubmit()
        assertTrue("quote A never started", quoteAEntered.await(10, TimeUnit.SECONDS))

        viewModel.onBarcodeChanged("ITEM-B")
        viewModel.onBarcodeSubmit()
        assertTrue("scan B never completed", scanBEntered.await(10, TimeUnit.SECONDS))

        viewModel.clear()
        assertTrue(viewModel.state.value is CashierUiState.Unavailable)

        releaseQuoteA.countDown()
        executor.shutdown()
        assertTrue("completion thread did not terminate", executor.awaitTermination(10, TimeUnit.SECONDS))

        assertEquals("queued quote B must not start after clear", listOf("ITEM-A"), quotedItems)
        assertTrue(viewModel.state.value is CashierUiState.Unavailable)
    }

    @Test
    fun `concurrent completions cannot add lines fifty and fifty one`() {
        val executor = Executors.newFixedThreadPool(2)
        val dispatcher = executor.asCoroutineDispatcher()
        val finalQuoteEntered = CountDownLatch(1)
        val releaseFinalQuotes = CountDownLatch(1)
        val finalItems = setOf("ITEM-49", "ITEM-50")
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { _, _ ->
                CatalogSearchResult.Success(
                    CatalogPage((0..50).map { product("ITEM-$it") }, 0, 20, false),
                )
            },
            quoteItem = { request, _ ->
                if (request.itemCode in finalItems) {
                    finalQuoteEntered.countDown()
                    releaseFinalQuotes.await(10, TimeUnit.SECONDS)
                }
                CatalogQuoteResult.Success(quote("1", request.itemCode))
            },
        )
        viewModel.bind(identity())
        viewModel.onQueryChanged("")
        awaitUntil({ (viewModel.state.value as? CashierUiState.Active)?.content?.products?.size == 51 }, "catalog loaded")

        repeat(49) { index ->
            viewModel.onProductSelected(cashierProduct("ITEM-$index"))
        }
        awaitUntil({ lines(viewModel).size == MAX_CART_ROWS - 1 }, "49 lines pre-filled")

        val barrier = CyclicBarrier(2)
        val first = thread("select-49") { barrier.await(); viewModel.onProductSelected(cashierProduct("ITEM-49")) }
        val second = thread("select-50") { barrier.await(); viewModel.onProductSelected(cashierProduct("ITEM-50")) }
        first.join()
        second.join()

        assertTrue("no final quote started", finalQuoteEntered.await(10, TimeUnit.SECONDS))
        releaseFinalQuotes.countDown()
        awaitUntil(
            { content(viewModel).quoteError == uiText(R.string.cart_error_row_limit) },
            "row limit error published",
        )

        executor.shutdown()
        assertTrue("completion threads did not terminate", executor.awaitTermination(10, TimeUnit.SECONDS))

        val committed = lines(viewModel)
        assertEquals(MAX_CART_ROWS, committed.size)
        assertEquals(1, committed.map(CartLine::itemCode).count { it in finalItems })
        assertEquals(
            uiText(R.string.cart_error_row_limit),
            content(viewModel).quoteError,
        )
    }

    @Test
    fun `old quote result cannot replace a newer line for the same serial`() {
        val (executor, dispatcher) = newSingleThreadDispatcher()
        val releaseQuote1 = CountDownLatch(1)
        val releaseQuote2 = CountDownLatch(1)
        val viewModel = viewModel(
            dispatcher = dispatcher,
            scanCatalog = { request, _ -> scanResult(request.value, serialNo = request.value) },
            quoteItem = { request, _ ->
                if (request.itemCode == "SER-1") releaseQuote1.await(10, TimeUnit.SECONDS) else releaseQuote2.await(10, TimeUnit.SECONDS)
                CatalogQuoteResult.Success(quote("1", request.itemCode))
            },
        )
        viewModel.bind(identity())
        viewModel.onBarcodeChanged("SER-1")
        viewModel.onBarcodeSubmit()
        awaitUntil({ viewModel.activeQuoteAuthority != null }, "first quote started")
        val oldAuthority = viewModel.activeQuoteAuthority
        requireNotNull(oldAuthority)

        viewModel.onBarcodeChanged("SER-2")
        viewModel.onBarcodeSubmit()
        releaseQuote1.countDown()
        awaitUntil({ viewModel.activeQuoteAuthority?.requestId?.let { it > oldAuthority.requestId } == true }, "second quote started")
        val newAuthority = viewModel.activeQuoteAuthority
        requireNotNull(newAuthority)

        releaseQuote2.countDown()
        awaitUntil({ lines(viewModel).size == 2 }, "both serial lines committed")
        executor.shutdown()
        assertTrue("completion thread did not terminate", executor.awaitTermination(10, TimeUnit.SECONDS))

        assertEquals(setOf("SER-1", "SER-2"), lines(viewModel).map(CartLine::serialNo).toSet())

        // Replay the old SER-1 completion out of order: it must be dropped,
        // not replace the newer SER-1 line.
        viewModel.publishQuoteForAuthority(oldAuthority, CatalogQuoteResult.Success(quote("1", "SER-1")))
        assertEquals(setOf("SER-1", "SER-2"), lines(viewModel).map(CartLine::serialNo).toSet())
    }

    private fun viewModel(
        dispatcher: CoroutineDispatcher,
        searchCatalog: (CatalogSearchRequest, ApiCallCancellation) -> CatalogSearchResult = { _, _ ->
            CatalogSearchResult.Success(CatalogPage(emptyList(), 0, 20, false))
        },
        scanCatalog: (CatalogScanRequest, ApiCallCancellation) -> CatalogScanResult = { _, _ ->
            CatalogScanResult.Failure(CatalogFailure.Unavailable)
        },
        quoteItem: (CatalogQuoteRequest, ApiCallCancellation) -> CatalogQuoteResult,
    ) = CashierViewModel(
        dispatcher = dispatcher,
        searchCatalog = searchCatalog,
        scanCatalog = scanCatalog,
        quoteItem = quoteItem,
    )

    private fun newSingleThreadDispatcher(): Pair<ExecutorService, CoroutineDispatcher> {
        val executor = Executors.newSingleThreadExecutor()
        return executor to executor.asCoroutineDispatcher()
    }

    private fun thread(name: String, block: () -> Unit): Thread =
        Thread(block, name).also { it.start() }

    private fun awaitUntil(condition: () -> Boolean, what: String, timeoutMillis: Long = 10_000) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!condition()) {
            if (System.nanoTime() > deadline) fail("Timed out waiting for: $what")
            Thread.sleep(2)
        }
    }

    private fun content(viewModel: CashierViewModel): CashierContent =
        (viewModel.state.value as CashierUiState.Active).content

    private fun lines(viewModel: CashierViewModel): List<CartLine> = content(viewModel).cart.lines

    private fun identity() = CashierIdentity(
        cashier = "cashier@example.com",
        sessionName = "OPEN-1",
        posProfile = "OUTLET-01",
        customer = "WALK-IN-01",
    )

    private fun cashierProduct(itemCode: String) = CashierProduct(
        itemCode = itemCode,
        itemName = itemCode,
        categoryId = "all",
        price = "1000",
        currency = "IDR",
        priceList = uiText(R.string.checkout_server_price),
        availableQuantity = "10",
        uom = "Nos",
        warehouse = "Warehouse",
    )

    private fun product(itemCode: String) = CatalogProduct(
        itemCode = itemCode,
        itemName = itemCode,
        description = "",
        image = null,
        uom = "Nos",
        priceListRate = "1000",
        currency = "IDR",
        availableQuantity = "10",
    )

    private fun scanResult(itemCode: String, serialNo: String? = null) = CatalogScanResult.Success(
        CatalogScan(
            itemCode = itemCode,
            barcode = itemCode,
            batchNo = null,
            serialNo = serialNo,
            uom = "Nos",
            conversionFactor = "1",
            warehouse = "Outlet 01 - RR",
        ),
        warnings = emptyList(),
    )

    private fun quote(quantity: String, itemCode: String) = CatalogQuote(
        itemCode = itemCode,
        quantity = quantity,
        uom = "Nos",
        conversionFactor = "1",
        warehouse = "Outlet 01 - RR",
        availableQuantity = "10",
        priceListRate = "1000",
        discountPercentage = "0",
        rate = "1000",
        itemTaxTemplate = null,
        warnings = emptyList(),
    )
}
