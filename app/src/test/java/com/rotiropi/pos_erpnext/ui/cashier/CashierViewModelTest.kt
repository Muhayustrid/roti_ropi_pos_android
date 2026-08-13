package com.rotiropi.pos_erpnext.ui.cashier

import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.data.CatalogQuote
import com.rotiropi.pos_erpnext.data.CatalogFailure
import com.rotiropi.pos_erpnext.data.CatalogPage
import com.rotiropi.pos_erpnext.data.CatalogProduct
import com.rotiropi.pos_erpnext.data.CatalogQuoteRequest
import com.rotiropi.pos_erpnext.data.CatalogQuoteResult
import com.rotiropi.pos_erpnext.data.CatalogScan
import com.rotiropi.pos_erpnext.data.CatalogScanResult
import com.rotiropi.pos_erpnext.data.CatalogSearchResult
import com.rotiropi.pos_erpnext.ui.uiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CashierViewModelTest {
    @Test
    fun `quantity syntax accepts exact decimal values without rounding`() {
        assertEquals("1", QuantitySyntax.parse("1"))
        assertEquals("1.5", QuantitySyntax.parse("1.500"))
        assertEquals("0.125", QuantitySyntax.parse("0.125"))
        assertEquals("10.000001", QuantitySyntax.parse("10.000001"))
        assertEquals("999999.999999", QuantitySyntax.parse("999999.999999"))
    }

    @Test
    fun `quantity syntax rejects unsupported forms and bounds`() {
        listOf(
            "0", "0.000000", "-1", "+1", "1,5", "1 000", "1e3", ".5",
            "1.", "1.1234567", "1000000",
        ).forEach { assertNull("Expected invalid quantity: $it", QuantitySyntax.parse(it)) }
    }

    @Test
    fun `serial quote line is one unit and retains local serial authority`() {
        val authority = authority(serialNo = "SER-1", requestId = 9)
        val result = CartState().applyQuote(
            draft = draft(quantity = "1", serialNo = "SER-1"),
            quote = quote("1"),
            authority = authority,
        )

        val applied = result as CartMutation.Applied
        assertEquals("SER-1", applied.state.lines.single().serialNo)
        assertEquals("1", applied.state.lines.single().quantity)
        assertEquals(authority, applied.state.lines.single().quoteAuthority)
    }

    @Test
    fun `serial cannot duplicate and non serial rows merge only on exact identity`() {
        val first = CartState().applyQuote(draft("2", batchNo = "B-1"), quote("2"), authority(batchNo = "B-1"))
            as CartMutation.Applied
        val merged = first.state.applyQuote(
            draft("3", batchNo = "B-1"),
            quote("3"),
            authority(batchNo = "B-1", requestId = 2),
        ) as CartMutation.Applied
        assertEquals(1, merged.state.lines.size)
        assertEquals("3", merged.state.lines.single().quantity)

        val differentBatch = merged.state.applyQuote(
            draft("1", batchNo = "B-2"),
            quote("1"),
            authority(batchNo = "B-2", requestId = 3),
        ) as CartMutation.Applied
        assertEquals(2, differentBatch.state.lines.size)

        val duplicateSerial = differentBatch.state.applyQuote(
            draft("1", serialNo = "SER-1"),
            quote("1"),
            authority(serialNo = "SER-1", requestId = 4),
        ) as CartMutation.Applied
        val rejected = duplicateSerial.state.applyQuote(
            draft("1", serialNo = "SER-1"),
            quote("1"),
            authority(serialNo = "SER-1", requestId = 5),
        )
        assertTrue(rejected is CartMutation.DuplicateSerial)
    }

    @Test
    fun `cart rejects fifty first distinct line`() {
        var state = CartState()
        repeat(MAX_CART_ROWS) { index ->
            state = (state.applyQuote(
                draft("1", itemCode = "ITEM-$index"),
                quote("1", itemCode = "ITEM-$index"),
                authority(itemCode = "ITEM-$index", requestId = index.toLong()),
            ) as CartMutation.Applied).state
        }

        val rejected = state.applyQuote(
            draft("1", itemCode = "ITEM-OVERFLOW"),
            quote("1", itemCode = "ITEM-OVERFLOW"),
            authority(itemCode = "ITEM-OVERFLOW", requestId = 51),
        )
        assertTrue(rejected is CartMutation.RowLimit)
        assertEquals(MAX_CART_ROWS, state.lines.size)
    }

    @Test
    fun `catalog search debounces and maps bounded page`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<CatalogSearchRequest>()
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { request, _ ->
                requests += request
                CatalogSearchResult.Success(
                    CatalogPage(
                        items = listOf(product("CROISSANT-PACK")),
                        start = request.start,
                        limit = request.limit,
                        hasMore = true,
                    ),
                )
            },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onQueryChanged(" croissant ")
        advanceTimeBy(299)
        assertTrue(requests.isEmpty())
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(CatalogSearchRequest("croissant", "OUTLET-01", 0, 20)), requests)
        val active = viewModel.state.value as CashierUiState.Active
        assertEquals(listOf("CROISSANT-PACK"), active.content.products.map(CashierProduct::itemCode))
        assertTrue(active.content.catalogHasMore)
    }

    @Test
    fun `catalog load more follows server offset and appends distinct products`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val starts = mutableListOf<Int>()
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { request, _ ->
                starts += request.start
                CatalogSearchResult.Success(
                    CatalogPage(
                        items = listOf(product("ITEM-${request.start}")),
                        start = request.start,
                        limit = request.limit,
                        hasMore = request.start == 0,
                    ),
                )
            },
        )
        viewModel.bind(identity())
        viewModel.onQueryChanged("items")
        advanceTimeBy(300)
        runCurrent()
        viewModel.loadMore()
        runCurrent()

        assertEquals(listOf(0, 20), starts)
        assertEquals(listOf("ITEM-0", "ITEM-20"), (viewModel.state.value as CashierUiState.Active).content.products.map(CashierProduct::itemCode))
        assertTrue(!(viewModel.state.value as CashierUiState.Active).content.catalogHasMore)
    }

    @Test
    fun `serial scan quotes one unit and keeps serial in cart`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scans = mutableListOf<CatalogScanRequest>()
        val quotes = mutableListOf<CatalogQuoteRequest>()
        val viewModel = viewModel(
            dispatcher = dispatcher,
            scanCatalog = { request, _ ->
                scans += request
                CatalogScanResult.Success(
                    CatalogScan(
                        itemCode = "SCALE",
                        barcode = "SER-1",
                        batchNo = null,
                        serialNo = "SER-1",
                        uom = "Nos",
                        conversionFactor = "1",
                        warehouse = "Outlet 01 - RR",
                    ),
                    warnings = emptyList(),
                )
            },
            quoteItem = { request, _ ->
                quotes += request
                CatalogQuoteResult.Success(quote("1", "SCALE"))
            },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onBarcodeChanged("SER-1")
        viewModel.onBarcodeSubmit()
        runCurrent()

        assertEquals(listOf(CatalogScanRequest("OUTLET-01", "SER-1")), scans)
        assertEquals("1", quotes.single().quantity)
        val line = (viewModel.state.value as CashierUiState.Active).content.cart.lines.single()
        assertEquals("SER-1", line.serialNo)
        assertEquals("1", line.quantity)
        viewModel.onRemoveLine(line)
        runCurrent()
        assertTrue((viewModel.state.value as CashierUiState.Active).content.cart.lines.isEmpty())
    }

    @Test
    fun `query replacement cancels obsolete catalog request`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cancellations = mutableListOf<com.rotiropi.pos_erpnext.data.api.ApiCallCancellation>()
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { request, cancellation ->
                cancellations += cancellation
                CatalogSearchResult.Success(CatalogPage(listOf(product(request.query)), 0, 20, false))
            },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onQueryChanged("old")
        advanceTimeBy(300)
        runCurrent()
        viewModel.onQueryChanged("new")
        runCurrent()

        assertTrue(cancellations.single().isCancelled)
        advanceTimeBy(300)
        runCurrent()
        assertEquals("new", (viewModel.state.value as CashierUiState.Active).content.query)
    }

    @Test
    fun `quantity change requotes exact quantity and never calculates payable`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val quantities = mutableListOf<String>()
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { _, _ ->
                CatalogSearchResult.Success(
                    CatalogPage(
                        items = listOf(product("ITEM-1")),
                        start = 0,
                        limit = 20,
                        hasMore = false,
                    ),
                )
            },
            quoteItem = { request, _ ->
                quantities += request.quantity
                CatalogQuoteResult.Success(quote(request.quantity, request.itemCode))
            },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onQueryChanged("ITEM-1")
        advanceTimeBy(300)
        runCurrent()

        viewModel.onProductSelected(
            CashierProduct(
                itemCode = "ITEM-1",
                itemName = "ITEM-1",
                categoryId = "all",
                price = "1000",
                currency = "IDR",
                priceList = uiText(R.string.checkout_server_price),
                availableQuantity = "10",
                uom = "Nos",
                warehouse = "Outlet 01 - RR",
            ),
        )
        runCurrent()
        val line = (viewModel.state.value as CashierUiState.Active).content.cart.lines.single()
        viewModel.onIncreaseQuantity(line)
        runCurrent()

        assertEquals(listOf("1", "2"), quantities)
        val updated = (viewModel.state.value as CashierUiState.Active).content.cart.lines.single()
        assertEquals("2", updated.quantity)
        assertEquals(
            uiText(R.string.cart_estimated_only),
            (viewModel.state.value as CashierUiState.Active).content.cart.payableLabel,
        )
    }

    @Test
    fun `repeated product selection merges non serial line with exact quantity`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val quantities = mutableListOf<String>()
        val product = CashierProduct("ITEM-1", "ITEM-1", "all", "1000", "IDR", uiText(R.string.checkout_server_price), "10", "Nos", "Warehouse")
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { _, _ ->
                CatalogSearchResult.Success(
                    CatalogPage(
                        items = listOf(product("ITEM-1")),
                        start = 0,
                        limit = 20,
                        hasMore = false,
                    ),
                )
            },
            quoteItem = { request, _ ->
                quantities += request.quantity
                CatalogQuoteResult.Success(quote(request.quantity, request.itemCode))
            },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onQueryChanged("ITEM-1")
        advanceTimeBy(300)
        runCurrent()

        viewModel.onProductSelected(product)
        runCurrent()
        viewModel.onProductSelected(product)
        runCurrent()

        assertEquals(listOf("1", "2"), quantities)
        assertEquals("2", (viewModel.state.value as CashierUiState.Active).content.cart.lines.single().quantity)
    }

    @Test
    fun `profile session change clears cart while customer change requotes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val customers = mutableListOf<String?>()
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { _, _ ->
                CatalogSearchResult.Success(
                    CatalogPage(
                        items = listOf(product("ITEM-1")),
                        start = 0,
                        limit = 20,
                        hasMore = false,
                    ),
                )
            },
            quoteItem = { request, _ ->
                customers += request.customer
                CatalogQuoteResult.Success(quote("1", request.itemCode))
            },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onQueryChanged("ITEM-1")
        advanceTimeBy(300)
        runCurrent()

        viewModel.onProductSelected(
            CashierProduct("ITEM-1", "ITEM-1", "all", "1000", "IDR", uiText(R.string.checkout_server_price), "10", "Nos", "Warehouse"),
        )
        runCurrent()
        viewModel.bind(identity().copy(customer = "CUST-1"))
        runCurrent()
        val line = (viewModel.state.value as CashierUiState.Active).content.cart.lines.single()
        assertEquals(listOf("WALK-IN-01", "CUST-1"), customers)
        assertEquals(uiText(R.string.cart_quote_estimate, "1000"), line.priceLabel)
        viewModel.bind(identity().copy(posProfile = "OUTLET-02"))
        runCurrent()
        assertTrue((viewModel.state.value as CashierUiState.Active).content.cart.lines.isEmpty())
    }

    @Test
    fun `decimal quantity edit triggers fresh exact quote`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val quantities = mutableListOf<String>()
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { _, _ ->
                CatalogSearchResult.Success(CatalogPage(listOf(product("ITEM-1")), 0, 20, false))
            },
            quoteItem = { request, _ ->
                quantities += request.quantity
                CatalogQuoteResult.Success(quote(request.quantity, request.itemCode))
            },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onQueryChanged("ITEM-1")
        advanceTimeBy(300)
        runCurrent()

        viewModel.onProductSelected(
            CashierProduct("ITEM-1", "ITEM-1", "all", "1000", "IDR", uiText(R.string.checkout_server_price), "10", "Nos", "Warehouse")
        )
        runCurrent()
        val line = (viewModel.state.value as CashierUiState.Active).content.cart.lines.single()

        viewModel.onQuantityEdited(line, "2.5")
        runCurrent()
        assertEquals(listOf("1", "2.5"), quantities)
        assertEquals("2.5", (viewModel.state.value as CashierUiState.Active).content.cart.lines.single().quantity)
    }

    @Test
    fun `invalid decimal quantity edit does not corrupt the cart line`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { _, _ ->
                CatalogSearchResult.Success(CatalogPage(listOf(product("ITEM-1")), 0, 20, false))
            },
            quoteItem = { request, _ -> CatalogQuoteResult.Success(quote(request.quantity, request.itemCode)) },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onQueryChanged("ITEM-1")
        advanceTimeBy(300)
        runCurrent()

        viewModel.onProductSelected(
            CashierProduct("ITEM-1", "ITEM-1", "all", "1000", "IDR", uiText(R.string.checkout_server_price), "10", "Nos", "Warehouse")
        )
        runCurrent()
        val line = (viewModel.state.value as CashierUiState.Active).content.cart.lines.single()

        listOf("abc", "0", "-1", "1,5", "1.1234567", "1000000", "1e3", "1 000").forEach { raw ->
            viewModel.onQuantityEdited(line, raw)
            val active = viewModel.state.value as CashierUiState.Active
            assertEquals(line.id, active.content.invalidQuantityForLine)
            assertEquals("1", active.content.cart.lines.single().quantity)
        }
    }

    @Test
    fun `scan submission clears the visible field and keeps the captured value`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = viewModel(
            dispatcher = dispatcher,
            scanCatalog = { request, _ ->
                CatalogScanResult.Success(
                    CatalogScan(
                        itemCode = "SCALE",
                        barcode = request.value,
                        batchNo = null,
                        serialNo = request.value,
                        uom = "Nos",
                        conversionFactor = "1",
                        warehouse = "Outlet 01 - RR",
                    ),
                    warnings = emptyList(),
                )
            },
            quoteItem = { request, _ -> CatalogQuoteResult.Success(quote("1", request.itemCode)) },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onBarcodeChanged("SER-1")
        viewModel.onBarcodeSubmit()
        assertEquals("", (viewModel.state.value as CashierUiState.Active).content.barcode)
        runCurrent()
        assertEquals("SER-1", (viewModel.state.value as CashierUiState.Active).content.cart.lines.single().serialNo)

        // Consecutive scans must not concatenate
        viewModel.onBarcodeChanged("SER-2")
        viewModel.onBarcodeSubmit()
        assertEquals("", (viewModel.state.value as CashierUiState.Active).content.barcode)
        runCurrent()
        val lines = (viewModel.state.value as CashierUiState.Active).content.cart.lines
        assertEquals(setOf("SER-1", "SER-2"), lines.map { it.serialNo }.toSet())
    }

    @Test
    fun `out-of-order completion does not overwrite newer quote state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { _, _ ->
                CatalogSearchResult.Success(
                    CatalogPage(
                        items = listOf(product("ITEM-A"), product("ITEM-B")),
                        start = 0,
                        limit = 20,
                        hasMore = false,
                    ),
                )
            },
            quoteItem = { request, _ -> CatalogQuoteResult.Success(quote("1", request.itemCode)) },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onQueryChanged("ITEM")
        advanceTimeBy(300)
        runCurrent()

        // Select ITEM-A -> quotes it (pending)
        viewModel.onProductSelected(
            CashierProduct("ITEM-A", "ITEM-A", "all", "1000", "IDR", uiText(R.string.checkout_server_price), "10", "Nos", "Warehouse")
        )
        val authA = viewModel.activeQuoteAuthority
        requireNotNull(authA)

        // Select ITEM-B -> enqueued behind ITEM-A; ITEM-A's quote is NOT discarded
        viewModel.onProductSelected(
            CashierProduct("ITEM-B", "ITEM-B", "all", "1000", "IDR", uiText(R.string.checkout_server_price), "10", "Nos", "Warehouse")
        )
        runCurrent()

        // Both lines exist; completing ITEM-A's old quote out of order must not replace ITEM-B
        assertEquals(setOf("ITEM-A", "ITEM-B"), (viewModel.state.value as CashierUiState.Active).content.cart.lines.map { it.itemCode }.toSet())
        viewModel.publishQuoteForAuthority(authA, CatalogQuoteResult.Success(quote("1", "ITEM-A")))
        assertEquals(setOf("ITEM-A", "ITEM-B"), (viewModel.state.value as CashierUiState.Active).content.cart.lines.map { it.itemCode }.toSet())
    }

    @Test
    fun `stale quote failure after retry is dropped and retried completion wins`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var failFirst = true
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { _, _ ->
                CatalogSearchResult.Success(CatalogPage(listOf(product("ITEM-1")), 0, 20, false))
            },
            quoteItem = { request, _ ->
                if (failFirst) {
                    failFirst = false
                    CatalogQuoteResult.Failure(CatalogFailure.Unavailable)
                } else {
                    CatalogQuoteResult.Success(quote("1", request.itemCode))
                }
            },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onQueryChanged("ITEM-1")
        advanceTimeBy(300)
        runCurrent()

        viewModel.onProductSelected(
            CashierProduct("ITEM-1", "ITEM-1", "all", "1000", "IDR", uiText(R.string.checkout_server_price), "10", "Nos", "Warehouse")
        )
        val failedAuth = viewModel.activeQuoteAuthority
        requireNotNull(failedAuth)
        runCurrent()
        assertTrue((viewModel.state.value as CashierUiState.Active).content.cart.lines.isEmpty())

        viewModel.retry()
        val retriedAuth = viewModel.activeQuoteAuthority
        requireNotNull(retriedAuth)
        assertTrue(retriedAuth.requestId > failedAuth.requestId)
        runCurrent()
        assertEquals("1", (viewModel.state.value as CashierUiState.Active).content.cart.lines.single().quantity)

        // Old failure completion after retry must be dropped, not clobber the quote state
        viewModel.publishQuoteForAuthority(failedAuth, CatalogQuoteResult.Failure(CatalogFailure.Unavailable))
        assertEquals("1", (viewModel.state.value as CashierUiState.Active).content.cart.lines.single().quantity)
    }

    @Test
    fun `line removal during active quote discards stale completion`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { _, _ ->
                CatalogSearchResult.Success(CatalogPage(listOf(product("ITEM-1")), 0, 20, false))
            },
            quoteItem = { request, _ -> CatalogQuoteResult.Success(quote(request.quantity, request.itemCode)) },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onQueryChanged("ITEM-1")
        advanceTimeBy(300)
        runCurrent()

        viewModel.onProductSelected(
            CashierProduct("ITEM-1", "ITEM-1", "all", "1000", "IDR", uiText(R.string.checkout_server_price), "10", "Nos", "Warehouse")
        )
        runCurrent()
        val line = (viewModel.state.value as CashierUiState.Active).content.cart.lines.single()

        // Start a requote for the line, then remove the line before it completes
        viewModel.onIncreaseQuantity(line)
        val pendingAuth = viewModel.activeQuoteAuthority
        requireNotNull(pendingAuth)
        viewModel.onRemoveLine(line)
        assertTrue((viewModel.state.value as CashierUiState.Active).content.cart.lines.isEmpty())

        // Stale completion for the removed line must be dropped
        viewModel.publishQuoteForAuthority(pendingAuth, CatalogQuoteResult.Success(quote("2", "ITEM-1")))
        assertTrue((viewModel.state.value as CashierUiState.Active).content.cart.lines.isEmpty())
    }

    @Test
    fun `logout during quote discards pending completion`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { _, _ ->
                CatalogSearchResult.Success(CatalogPage(listOf(product("ITEM-1")), 0, 20, false))
            },
            quoteItem = { _, _ -> CatalogQuoteResult.Failure(CatalogFailure.Unavailable) },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onQueryChanged("ITEM-1")
        advanceTimeBy(300)
        runCurrent()

        viewModel.onProductSelected(
            CashierProduct("ITEM-1", "ITEM-1", "all", "1000", "IDR", uiText(R.string.checkout_server_price), "10", "Nos", "Warehouse")
        )
        val pendingAuth = viewModel.activeQuoteAuthority
        requireNotNull(pendingAuth)

        viewModel.clear()
        assertTrue(viewModel.state.value is CashierUiState.Unavailable)

        // Completion after logout must be dropped
        viewModel.publishQuoteForAuthority(pendingAuth, CatalogQuoteResult.Success(quote("1", "ITEM-1")))
        assertTrue(viewModel.state.value is CashierUiState.Unavailable)
    }

    @Test
    fun `serial customer requote preserves exact stable line identity`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = viewModel(
            dispatcher = dispatcher,
            scanCatalog = { request, _ ->
                CatalogScanResult.Success(
                    CatalogScan(
                        itemCode = "SCALE",
                        barcode = request.value,
                        batchNo = null,
                        serialNo = request.value,
                        uom = "Nos",
                        conversionFactor = "1",
                        warehouse = "Outlet 01 - RR",
                    ),
                    warnings = emptyList(),
                )
            },
            quoteItem = { request, _ -> CatalogQuoteResult.Success(quote("1", request.itemCode)) },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onBarcodeChanged("SER-1")
        viewModel.onBarcodeSubmit()
        runCurrent()
        val firstLine = (viewModel.state.value as CashierUiState.Active).content.cart.lines.single()
        assertEquals("SER-1", firstLine.serialNo)

        // Customer change requotes the same serial line
        viewModel.bind(identity().copy(customer = "CUST-1"))
        runCurrent()
        val requotedLine = (viewModel.state.value as CashierUiState.Active).content.cart.lines.single()
        assertEquals("SER-1", requotedLine.serialNo)
        assertEquals(firstLine.id, requotedLine.id)
        assertEquals(1, (viewModel.state.value as CashierUiState.Active).content.cart.lines.size)
    }

    @Test
    fun `scan during unrelated quote keeps both lines`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { _, _ ->
                CatalogSearchResult.Success(CatalogPage(listOf(product("ITEM-1")), 0, 20, false))
            },
            scanCatalog = { request, _ ->
                CatalogScanResult.Success(
                    CatalogScan(
                        itemCode = "SCALE",
                        barcode = request.value,
                        batchNo = null,
                        serialNo = request.value,
                        uom = "Nos",
                        conversionFactor = "1",
                        warehouse = "Outlet 01 - RR",
                    ),
                    warnings = emptyList(),
                )
            },
            quoteItem = { request, _ -> CatalogQuoteResult.Success(quote("1", request.itemCode)) },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onQueryChanged("ITEM-1")
        advanceTimeBy(300)
        runCurrent()

        // Scan an unrelated serial while the item quote is pending
        viewModel.onProductSelected(
            CashierProduct("ITEM-1", "ITEM-1", "all", "1000", "IDR", uiText(R.string.checkout_server_price), "10", "Nos", "Warehouse")
        )
        viewModel.onBarcodeChanged("SER-1")
        viewModel.onBarcodeSubmit()
        runCurrent()

        // Both the pending item and the scanned serial land in the cart
        val lines = (viewModel.state.value as CashierUiState.Active).content.cart.lines
        assertEquals(setOf("ITEM-1", "SCALE"), lines.map { it.itemCode }.toSet())
        assertEquals(2, lines.size)
    }

    @Test
    fun `line fifty first concurrent completion is rejected with row limit`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val items = (0 until 51).map { product("ITEM-$it") }
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { _, _ ->
                CatalogSearchResult.Success(CatalogPage(items, 0, 20, false))
            },
            quoteItem = { request, _ -> CatalogQuoteResult.Success(quote("1", request.itemCode)) },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onQueryChanged("ITEM")
        advanceTimeBy(300)
        runCurrent()

        repeat(51) { index ->
            viewModel.onProductSelected(
                CashierProduct("ITEM-$index", "ITEM-$index", "all", "1000", "IDR", uiText(R.string.checkout_server_price), "10", "Nos", "Warehouse")
            )
        }
        runCurrent()
        val active = viewModel.state.value as CashierUiState.Active
        assertEquals(MAX_CART_ROWS, active.content.cart.lines.size)
        assertEquals(uiText(R.string.cart_error_row_limit), active.content.quoteError)
    }

    @Test
    fun `repeated pagination metadata does not advance twice`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val starts = mutableListOf<Int>()
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { request, _ ->
                starts += request.start
                CatalogSearchResult.Success(
                    CatalogPage(
                        items = listOf(product("ITEM-${request.start}")),
                        start = request.start,
                        limit = request.limit,
                        hasMore = true,
                    ),
                )
            },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onQueryChanged("")
        advanceTimeBy(300)
        runCurrent()
        viewModel.loadMore()
        runCurrent()
        viewModel.loadMore()
        assertEquals(listOf(0, 20), starts)
        val active = viewModel.state.value as CashierUiState.Active
        assertEquals(listOf("ITEM-0", "ITEM-20"), active.content.products.map(CashierProduct::itemCode))
    }

    @Test
    fun `regressive and duplicate pagination metadata stop progression`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val starts = mutableListOf<Int>()
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { request, _ ->
                starts += request.start
                CatalogSearchResult.Success(
                    CatalogPage(
                        items = listOf(product("ITEM-${request.start}")),
                        start = 0,
                        limit = request.limit,
                        hasMore = true,
                    ),
                )
            },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onQueryChanged("")
        advanceTimeBy(300)
        runCurrent()
        val first = viewModel.state.value as CashierUiState.Active
        assertTrue(first.content.catalogHasMore)

        // Regressive metadata (start back at 0 while requested 20) stops progression
        viewModel.loadMore()
        runCurrent()
        val second = viewModel.state.value as CashierUiState.Active
        assertTrue(!second.content.catalogHasMore)
        assertEquals(1, second.content.products.size)
    }

    @Test
    fun `stale quote after quantity change cannot overwrite newer quantity`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = viewModel(
            dispatcher = dispatcher,
            searchCatalog = { _, _ ->
                CatalogSearchResult.Success(CatalogPage(listOf(product("ITEM-1")), 0, 20, false))
            },
            quoteItem = { request, _ -> CatalogQuoteResult.Success(quote(request.quantity, request.itemCode)) },
        )
        viewModel.bind(identity())
        runCurrent()
        viewModel.onQueryChanged("ITEM-1")
        advanceTimeBy(300)
        runCurrent()

        viewModel.onProductSelected(
            CashierProduct("ITEM-1", "ITEM-1", "all", "1000", "IDR", uiText(R.string.checkout_server_price), "10", "Nos", "Warehouse")
        )
        runCurrent()
        assertEquals("1", (viewModel.state.value as CashierUiState.Active).content.cart.lines.single().quantity)

        // Requote to qty 2 and complete it
        viewModel.onIncreaseQuantity((viewModel.state.value as CashierUiState.Active).content.cart.lines.single())
        val qty2Auth = viewModel.activeQuoteAuthority
        requireNotNull(qty2Auth)
        runCurrent()
        assertEquals("2", (viewModel.state.value as CashierUiState.Active).content.cart.lines.single().quantity)

        // Requote to qty 3 and complete it
        viewModel.onIncreaseQuantity((viewModel.state.value as CashierUiState.Active).content.cart.lines.single())
        val qty3Auth = viewModel.activeQuoteAuthority
        requireNotNull(qty3Auth)
        runCurrent()
        assertEquals("3", (viewModel.state.value as CashierUiState.Active).content.cart.lines.single().quantity)

        // Stale qty 2 completion out of order must be dropped
        viewModel.publishQuoteForAuthority(qty2Auth, CatalogQuoteResult.Success(quote("2", "ITEM-1")))
        assertEquals("3", (viewModel.state.value as CashierUiState.Active).content.cart.lines.single().quantity)
    }

    private fun viewModel(
        dispatcher: TestDispatcher,
        searchCatalog: (CatalogSearchRequest, com.rotiropi.pos_erpnext.data.api.ApiCallCancellation) -> CatalogSearchResult = { _, _ ->
            CatalogSearchResult.Success(CatalogPage(emptyList(), 0, 20, false))
        },
        scanCatalog: (CatalogScanRequest, com.rotiropi.pos_erpnext.data.api.ApiCallCancellation) -> CatalogScanResult = { _, _ ->
            CatalogScanResult.Failure(CatalogFailure.Unavailable)
        },
        quoteItem: (CatalogQuoteRequest, com.rotiropi.pos_erpnext.data.api.ApiCallCancellation) -> CatalogQuoteResult = { _, _ ->
            CatalogQuoteResult.Failure(CatalogFailure.Unavailable)
        },
    ) = CashierViewModel(
        dispatcher = dispatcher,
        searchCatalog = searchCatalog,
        scanCatalog = scanCatalog,
        quoteItem = quoteItem,
    )

    private fun identity() = CashierIdentity(
        cashier = "cashier@example.com",
        sessionName = "OPEN-1",
        posProfile = "OUTLET-01",
        customer = "WALK-IN-01",
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

    private fun draft(
        quantity: String,
        itemCode: String = "ITEM-1",
        uom: String = "Nos",
        batchNo: String? = null,
        serialNo: String? = null,
    ) = CartLineDraft(
        itemCode = itemCode,
        itemName = itemCode,
        quantity = quantity,
        uom = uom,
        batchNo = batchNo,
        serialNo = serialNo,
        warehouse = "Outlet 01 - RR",
        conversionFactor = "1",
    )

    private fun quote(
        quantity: String,
        itemCode: String = "ITEM-1",
    ) = CatalogQuote(
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

    private fun authority(
        itemCode: String = "ITEM-1",
        batchNo: String? = null,
        serialNo: String? = null,
        requestId: Long = 1,
    ) = QuoteAuthority(
        cashier = "cashier@example.com",
        sessionName = "OPEN-1",
        posProfile = "OUTLET-01",
        customer = "WALK-IN",
        itemCode = itemCode,
        quantity = "1",
        uom = "Nos",
        batchNo = batchNo,
        serialNo = serialNo,
        generation = 1,
        requestId = requestId,
    )
}
