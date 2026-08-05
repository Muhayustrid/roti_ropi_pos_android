package com.rotiropi.pos_erpnext.ui.cashier

import com.rotiropi.pos_erpnext.data.CatalogFailure
import com.rotiropi.pos_erpnext.data.CatalogPage
import com.rotiropi.pos_erpnext.data.CatalogProduct
import com.rotiropi.pos_erpnext.data.CatalogQuoteRequest
import com.rotiropi.pos_erpnext.data.CatalogQuoteResult
import com.rotiropi.pos_erpnext.data.CatalogScanResult
import com.rotiropi.pos_erpnext.data.CatalogSearchResult
import com.rotiropi.pos_erpnext.data.api.ApiCallCancellation
import com.rotiropi.pos_erpnext.ui.payment.CheckoutUiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque

const val CATALOG_PAGE_SIZE = 20
const val MAX_CATALOG_PRODUCTS = 100

data class CashierIdentity(
    val cashier: String,
    val sessionName: String?,
    val posProfile: String,
    val customer: String?,
    val warehouse: String = "",
)

data class CatalogSearchRequest(
    val query: String,
    val posProfile: String,
    val start: Int,
    val limit: Int,
)

data class CatalogScanRequest(
    val posProfile: String,
    val value: String,
)

data class CatalogSearchAuthority(
    val identity: CashierIdentity,
    val generation: Long,
    val query: String,
    val start: Int,
    val requestId: Long,
)

data class CatalogScanAuthority(
    val identity: CashierIdentity,
    val generation: Long,
    val value: String,
    val requestId: Long,
)

sealed interface CashierEvent {
    data class Bind(val identity: CashierIdentity) : CashierEvent
    data object Clear : CashierEvent
    data class SubmitBarcode(val value: String) : CashierEvent
    data class ChangeQuery(val query: String) : CashierEvent
    data object LoadMoreCatalog : CashierEvent
    data class SelectCategory(val categoryId: String) : CashierEvent
    data class SelectProduct(val code: String) : CashierEvent
    data class IncreaseQuantity(val lineId: String) : CashierEvent
    data class DecreaseQuantity(val lineId: String) : CashierEvent
    data class EditQuantity(val lineId: String, val raw: String) : CashierEvent
    data class RemoveLine(val lineId: String) : CashierEvent
    data object Retry : CashierEvent
    data class CatalogPageReceived(val authority: CatalogSearchAuthority, val result: CatalogSearchResult) : CashierEvent
    data class ScanResultReceived(val authority: CatalogScanAuthority, val result: CatalogScanResult) : CashierEvent
    data class QuoteResultReceived(val authority: QuoteAuthority, val result: CatalogQuoteResult) : CashierEvent
}

class CashierViewModel(
    dispatcher: CoroutineDispatcher,
    private val searchCatalog: (CatalogSearchRequest, ApiCallCancellation) -> CatalogSearchResult,
    private val scanCatalog: (CatalogScanRequest, ApiCallCancellation) -> CatalogScanResult,
    private val quoteItem: (CatalogQuoteRequest, ApiCallCancellation) -> CatalogQuoteResult,
    private val cancellationFactory: () -> ApiCallCancellation = ::ApiCallCancellation,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val stateLock = Any()
    private val _state = MutableStateFlow<CashierUiState>(CashierUiState.Unavailable)
    private var identity: CashierIdentity? = null
    private var query = ""
    private var barcode = ""
    private var products = emptyList<CatalogProduct>()
    private var cart = CartState()
    private var selectedCategoryId = "all"
    private var catalogLoading = false
    private var catalogHasMore = false
    private var catalogError: String? = null
    private var scanLoading = false
    private var scanError: String? = null
    private var quoteLoading = false
    private var quoteError: String? = null
    private var nextCatalogStart: Int? = null
    private var catalogGeneration = 0L
    private var scanGeneration = 0L
    private var quoteGeneration = 0L
    private var nextRequestId = 0L
    private var catalogJob: Job? = null
    private var scanJob: Job? = null
    private var quoteJob: Job? = null
    private var catalogCancellation: ApiCallCancellation? = null
    private var scanCancellation: ApiCallCancellation? = null
    private var quoteCancellation: ApiCallCancellation? = null
    private var activeCatalogAuthority: CatalogSearchAuthority? = null
    private var activeScanAuthority: CatalogScanAuthority? = null
    private var currentQuoteAuthority: QuoteAuthority? = null
    private var activeQuoteDraft: CartLineDraft? = null
    private var lastScanValue: String? = null
    private var invalidQuantityForLine: String? = null
    private val quoteQueue = ArrayDeque<CartLineDraft>()

    val state: StateFlow<CashierUiState> = _state.asStateFlow()

    internal val activeQuoteAuthority: QuoteAuthority?
        get() = synchronized(stateLock) { currentQuoteAuthority }

    private fun processEvent(event: CashierEvent) {
        synchronized(stateLock) {
            when (event) {
            is CashierEvent.Bind -> handleBind(event.identity)
            is CashierEvent.Clear -> handleClear()
            is CashierEvent.SubmitBarcode -> handleSubmitBarcode(event.value)
            is CashierEvent.ChangeQuery -> handleChangeQuery(event.query)
            is CashierEvent.LoadMoreCatalog -> handleLoadMoreCatalog()
            is CashierEvent.SelectCategory -> handleSelectCategory(event.categoryId)
            is CashierEvent.SelectProduct -> handleSelectProduct(event.code)
            is CashierEvent.IncreaseQuantity -> handleIncreaseQuantity(event.lineId)
            is CashierEvent.DecreaseQuantity -> handleDecreaseQuantity(event.lineId)
            is CashierEvent.EditQuantity -> handleEditQuantity(event.lineId, event.raw)
            is CashierEvent.RemoveLine -> handleRemoveLine(event.lineId)
            is CashierEvent.Retry -> handleRetry()
            is CashierEvent.CatalogPageReceived -> publishCatalog(event.authority, event.result)
            is CashierEvent.ScanResultReceived -> publishScan(event.authority, event.result)
            is CashierEvent.QuoteResultReceived -> publishQuote(event.authority, event.result)
            }
        }
    }

    fun bind(value: CashierIdentity) {
        processEvent(CashierEvent.Bind(value))
    }

    private fun handleBind(value: CashierIdentity) {
        val previous = identity
        if (previous == value) return
        identity = value
        if (previous != null && sameWorkspace(previous, value)) {
            val drafts = cart.lines.map { it.toDraft(it.quantity) }
            cancelQuote()
            quoteQueue.clear()
            quoteQueue.addAll(drafts)
            quoteGeneration++
            cart = cart.invalidateQuotes()
            quoteError = null
            publish()
            processQuoteQueue()
            return
        }
        resetWorkspace()
        publish()
        handleChangeQuery("")
    }

    fun onQueryChanged(value: String) {
        processEvent(CashierEvent.ChangeQuery(value))
    }

    private fun handleChangeQuery(value: String) {
        val current = identity ?: return
        query = value.trim()
        catalogGeneration++
        cancelCatalog()
        products = emptyList()
        nextCatalogStart = null
        catalogHasMore = false
        catalogError = null
        catalogLoading = true
        publish()
        val generation = catalogGeneration
        val requestedQuery = query
        catalogJob = scope.launch {
            delay(300)
            beginCatalog(current, generation, requestedQuery, 0)
        }
    }

    fun loadMore() {
        processEvent(CashierEvent.LoadMoreCatalog)
    }

    private fun handleLoadMoreCatalog() {
        val current = identity ?: return
        val start = nextCatalogStart ?: return
        if (catalogLoading) return
        beginCatalog(current, catalogGeneration, query, start)
    }

    fun onBarcodeChanged(value: String) {
        synchronized(stateLock) {
            barcode = value
            scanGeneration++
            cancelScan()
            scanError = null
            publish()
        }
    }

    fun onBarcodeSubmit(value: String = barcode) {
        processEvent(CashierEvent.SubmitBarcode(value))
    }

    private fun handleSubmitBarcode(value: String) {
        val current = identity ?: return
        if (value.isBlank()) return
        // B8: capture the submitted value, clear the visible field, dispatch using the captured value
        lastScanValue = value
        barcode = ""
        cancelScan()
        scanGeneration++
        val authority = CatalogScanAuthority(
            identity = current,
            generation = scanGeneration,
            value = value,
            requestId = ++nextRequestId,
        )
        activeScanAuthority = authority
        scanLoading = true
        scanError = null
        publish()
        val cancellation = cancellationFactory()
        scanCancellation = cancellation
        scanJob = scope.launch {
            val result = scanCatalog(
                CatalogScanRequest(current.posProfile, value),
                cancellation,
            )
            publishScan(authority, result)
        }
    }

    fun onProductSelected(product: CashierProduct) {
        processEvent(CashierEvent.SelectProduct(product.itemCode))
    }

    private fun handleSelectProduct(code: String) {
        if (identity == null) return
        val product = products.firstOrNull { it.itemCode == code }?.toUi(identity?.warehouse ?: "") ?: return
        scanGeneration++
        cancelScan()
        val existing = cart.lines.firstOrNull {
            it.serialNo == null &&
                it.itemCode == product.itemCode &&
                it.uom == product.uom &&
                it.batchNo == null
        }
        val quantity = if (existing == null) "1" else QuantitySyntax.addUnit(existing.quantity)
        if (quantity == null) return
        requestQuote(
            CartLineDraft(
                itemCode = product.itemCode,
                itemName = product.itemName,
                quantity = quantity,
                uom = product.uom,
                batchNo = null,
                serialNo = null,
                warehouse = product.warehouse,
                conversionFactor = "1",
            ),
        )
    }

    fun onCategorySelected(category: CashierCategory) {
        processEvent(CashierEvent.SelectCategory(category.id))
    }

    private fun handleSelectCategory(categoryId: String) {
        selectedCategoryId = categoryId
        publish()
    }

    fun onIncreaseQuantity(line: CartLine) {
        processEvent(CashierEvent.IncreaseQuantity(line.id))
    }

    fun onQuantityEdited(line: CartLine, raw: String) {
        processEvent(CashierEvent.EditQuantity(line.id, raw))
    }

    private fun handleEditQuantity(lineId: String, raw: String) {
        val current = cart.line(lineId) ?: return
        if (current.serialNo != null) return
        val parsed = QuantitySyntax.parse(raw.trim())
        if (parsed == null) {
            // B7: invalid input must not corrupt the previous valid cart line
            invalidQuantityForLine = lineId
            publish()
            return
        }
        invalidQuantityForLine = null
        requestQuote(current.toDraft(parsed))
    }

    private fun handleIncreaseQuantity(lineId: String) {
        val current = cart.line(lineId) ?: return
        if (current.serialNo != null) return
        val quantity = QuantitySyntax.addUnit(current.quantity) ?: return
        scanGeneration++
        cancelScan()
        requestQuote(current.toDraft(quantity))
    }

    fun onDecreaseQuantity(line: CartLine) {
        processEvent(CashierEvent.DecreaseQuantity(line.id))
    }

    private fun handleDecreaseQuantity(lineId: String) {
        val current = cart.line(lineId) ?: return
        if (current.serialNo != null) return
        val quantity = QuantitySyntax.subtractUnit(current.quantity)
        if (quantity == null) {
            scanGeneration++
            cancelScan()
            quoteQueue.clear()
            cancelQuote()
            cart = cart.removeLine(current.id)
            quoteError = null
            publish()
            return
        }
        scanGeneration++
        cancelScan()
        requestQuote(current.toDraft(quantity))
    }

    fun onRemoveLine(line: CartLine) {
        processEvent(CashierEvent.RemoveLine(line.id))
    }

    private fun handleRemoveLine(lineId: String) {
        if (cart.line(lineId) == null) return
        scanGeneration++
        cancelScan()
        quoteQueue.clear()
        cancelQuote()
        cart = cart.removeLine(lineId)
        quoteError = null
        if (invalidQuantityForLine == lineId) invalidQuantityForLine = null
        publish()
    }

    fun retry() {
        processEvent(CashierEvent.Retry)
    }

    private fun handleRetry() {
        val current = identity ?: return
        if (catalogError != null) {
            beginCatalog(current, catalogGeneration, query, nextCatalogStart ?: 0)
        } else if (quoteError != null && activeQuoteDraft != null) {
            requestQuote(activeQuoteDraft!!)
        } else if (scanError != null) {
            handleSubmitBarcode(lastScanValue ?: barcode)
        }
    }

    fun clear() {
        processEvent(CashierEvent.Clear)
    }

    private fun handleClear() {
        cancelAll()
        identity = null
        resetWorkspace()
        _state.value = CashierUiState.Unavailable
    }

    fun publishQuoteForAuthority(authority: QuoteAuthority, result: CatalogQuoteResult) {
        synchronized(stateLock) { publishQuote(authority, result) }
    }

    private fun beginCatalog(
        current: CashierIdentity,
        generation: Long,
        requestedQuery: String,
        start: Int,
    ) {
        synchronized(stateLock) {
            if (identity != current || generation != catalogGeneration) return
            cancelCatalog()
            val authority = CatalogSearchAuthority(current, generation, requestedQuery, start, ++nextRequestId)
            activeCatalogAuthority = authority
            catalogLoading = true
            catalogError = null
            publish()
            val cancellation = cancellationFactory()
            catalogCancellation = cancellation
            catalogJob = scope.launch {
                publishCatalog(
                    authority,
                    searchCatalog(
                        CatalogSearchRequest(requestedQuery, current.posProfile, start, CATALOG_PAGE_SIZE),
                        cancellation,
                    ),
                )
            }
        }
    }

    private fun publishCatalog(authority: CatalogSearchAuthority, result: CatalogSearchResult) {
        synchronized(stateLock) {
            if (activeCatalogAuthority != authority || identity != authority.identity || catalogGeneration != authority.generation) return
            when (result) {
                is CatalogSearchResult.Success -> applyCatalogPage(authority, result.page)
                is CatalogSearchResult.Failure -> {
                    catalogLoading = false
                    catalogError = result.reason.userMessage()
                }
            }
            publish()
        }
    }

    private fun applyCatalogPage(authority: CatalogSearchAuthority, page: CatalogPage) {
        if (page.limit <= 0 || (authority.start > 0 && page.start < authority.start)) {
            catalogLoading = false
            catalogHasMore = false
            nextCatalogStart = null
            return
        }
        products = (if (authority.start == 0) page.items else products + page.items)
            .distinctBy(CatalogProduct::itemCode)
            .take(MAX_CATALOG_PRODUCTS)
        val candidate = page.start + page.limit
        nextCatalogStart = candidate.takeIf {
            page.hasMore && page.start >= authority.start && it > authority.start && products.size < MAX_CATALOG_PRODUCTS
        }
        catalogHasMore = nextCatalogStart != null
        catalogLoading = false
        catalogError = null
    }

    private fun publishScan(authority: CatalogScanAuthority, result: CatalogScanResult) {
        synchronized(stateLock) {
            if (activeScanAuthority != authority || identity != authority.identity || scanGeneration != authority.generation) return
            scanLoading = false
            when (result) {
                is CatalogScanResult.Success -> {
                    scanError = null
                    val existing = cart.lines.firstOrNull {
                        it.serialNo == null &&
                            result.scan.serialNo == null &&
                            it.itemCode == result.scan.itemCode &&
                            it.uom == result.scan.uom &&
                            it.batchNo == result.scan.batchNo
                    }
                    val quantity = if (existing == null) "1" else QuantitySyntax.addUnit(existing.quantity)
                    if (quantity == null) {
                        scanError = "Quantity cannot be increased safely."
                    } else {
                        requestQuote(
                            CartLineDraft(
                                itemCode = result.scan.itemCode,
                                itemName = result.scan.itemCode,
                                quantity = quantity,
                                uom = result.scan.uom,
                                batchNo = result.scan.batchNo,
                                serialNo = result.scan.serialNo,
                                warehouse = result.scan.warehouse,
                                conversionFactor = result.scan.conversionFactor,
                                scanWarnings = result.warnings,
                            ),
                        )
                    }
                }
                is CatalogScanResult.Failure -> scanError = result.reason.userMessage()
            }
            publish()
        }
    }

    private fun requestQuote(draft: CartLineDraft, preserveQueue: Boolean = false) {
        val current = identity ?: return
        val active = activeQuoteDraft
        val sameLine = active != null &&
            active.itemCode == draft.itemCode &&
            active.uom == draft.uom &&
            active.batchNo == draft.batchNo &&
            active.serialNo == draft.serialNo
        if (sameLine) {
            // A newer request for the same logical draft/line replaces the pending one.
            cancelQuote()
            quoteQueue.clear()
        }
        quoteQueue.addLast(draft)
        processQuoteQueue()
    }

    private fun processQuoteQueue() {
        if (currentQuoteAuthority != null) return
        val draft = quoteQueue.pollFirst() ?: return
        startQuote(draft)
    }

    private fun startQuote(draft: CartLineDraft) {
        val current = identity ?: return
        val existing = cart.lines.firstOrNull {
            it.itemCode == draft.itemCode && it.serialNo == draft.serialNo && it.batchNo == draft.batchNo
        }
        if (existing != null) cart = cart.invalidateLine(existing.id)
        val authority = QuoteAuthority(
            cashier = current.cashier,
            sessionName = current.sessionName,
            posProfile = current.posProfile,
            customer = current.customer,
            itemCode = draft.itemCode,
            quantity = requireNotNull(QuantitySyntax.parse(draft.quantity)),
            uom = draft.uom,
            batchNo = draft.batchNo,
            serialNo = draft.serialNo,
            generation = ++quoteGeneration,
            requestId = ++nextRequestId,
        )
        activeQuoteDraft = draft.copy(quantity = authority.quantity)
        currentQuoteAuthority = authority
        quoteLoading = true
        quoteError = null
        publish()
        val cancellation = cancellationFactory()
        quoteCancellation = cancellation
        quoteJob = scope.launch {
            val result = quoteItem(
                CatalogQuoteRequest(
                    posProfile = current.posProfile,
                    customer = current.customer,
                    itemCode = draft.itemCode,
                    quantity = authority.quantity,
                    uom = draft.uom,
                    batchNo = draft.batchNo,
                    warehouse = draft.warehouse,
                    conversionFactor = draft.conversionFactor,
                ),
                cancellation,
            )
            publishQuote(authority, result)
        }
    }

    private fun publishQuote(authority: QuoteAuthority, result: CatalogQuoteResult) {
        synchronized(stateLock) {
            if (identity?.cashier != authority.cashier || identity?.posProfile != authority.posProfile) return

        // If currentQuoteAuthority is active and has a newer requestId for the SAME item/line, drop the stale completion!
        val activeAuth = currentQuoteAuthority
        if (activeAuth != null && activeAuth.itemCode == authority.itemCode && activeAuth.uom == authority.uom && activeAuth.batchNo == authority.batchNo && activeAuth.serialNo == authority.serialNo && activeAuth.requestId > authority.requestId) {
            return
        }

        // If a line already exists in the cart with a NEWER request ID for the same line identity, drop this stale completion!
        val existingLine = cart.lines.firstOrNull { it.itemCode == authority.itemCode && it.uom == authority.uom && it.batchNo == authority.batchNo && it.serialNo == authority.serialNo }
        if (existingLine != null && existingLine.quoteAuthority != null && existingLine.quoteAuthority.requestId > authority.requestId) {
            return
        }

        // Resolve the draft ONLY for the active authority or an existing cart line.
        // An orphaned stale completion (no active draft, no matching cart line) must be dropped,
        // never materialized as a new cart row.
        val draft = (if (currentQuoteAuthority == authority) activeQuoteDraft else null)
            ?: cart.lines.firstOrNull { it.itemCode == authority.itemCode && it.uom == authority.uom && it.batchNo == authority.batchNo && it.serialNo == authority.serialNo }?.let {
                CartLineDraft(
                    itemCode = it.itemCode,
                    itemName = it.itemName,
                    quantity = authority.quantity,
                    uom = it.uom,
                    batchNo = it.batchNo,
                    serialNo = it.serialNo,
                    warehouse = it.warehouse,
                    conversionFactor = it.conversionFactor,
                    scanWarnings = it.scanWarnings,
                )
            }
        if (draft == null) return

        if (currentQuoteAuthority == authority) {
            quoteLoading = false
            currentQuoteAuthority = null
        }

        when (result) {
            is CatalogQuoteResult.Success -> when (val mutation = cart.applyQuote(draft, result.quote, authority)) {
                is CartMutation.Applied -> {
                    cart = mutation.state
                    quoteError = null
                    activeQuoteDraft = null
                    publish()
                    processQuoteQueue()
                }
                else -> {
                    quoteError = mutation.userMessage()
                    currentQuoteAuthority = null
                    publish()
                }
            }
            is CatalogQuoteResult.Failure -> {
                // Keep activeQuoteDraft so Retry can resubmit the same draft.
                quoteError = result.reason.userMessage()
                currentQuoteAuthority = null
                publish()
            }
            }
        }
    }

    private fun resetWorkspace() {
        cancelAll()
        quoteQueue.clear()
        query = ""
        barcode = ""
        products = emptyList()
        cart = CartState()
        selectedCategoryId = "all"
        catalogLoading = false
        catalogHasMore = false
        catalogError = null
        scanLoading = false
        scanError = null
        quoteLoading = false
        quoteError = null
        lastScanValue = null
        invalidQuantityForLine = null
        nextCatalogStart = null
        catalogGeneration++
        scanGeneration++
        quoteGeneration++
    }

    private fun cancelAll() {
        cancelCatalog()
        cancelScan()
        cancelQuote()
        quoteQueue.clear()
    }

    private fun cancelCatalog() {
        catalogCancellation?.cancel()
        catalogCancellation = null
        catalogJob?.cancel()
        catalogJob = null
        activeCatalogAuthority = null
    }

    private fun cancelScan() {
        scanCancellation?.cancel()
        scanCancellation = null
        scanJob?.cancel()
        scanJob = null
        activeScanAuthority = null
        scanLoading = false
    }

    private fun cancelQuote() {
        quoteCancellation?.cancel()
        quoteCancellation = null
        quoteJob?.cancel()
        quoteJob = null
        currentQuoteAuthority = null
        activeQuoteDraft = null
        quoteLoading = false
    }

    private fun publish() {
        val current = identity ?: run {
            _state.value = CashierUiState.Unavailable
            return
        }
        _state.value = CashierUiState.Active(
            CashierContent(
                query = query,
                barcode = barcode,
                categories = listOf(CashierCategory("all", "All")),
                selectedCategoryId = selectedCategoryId,
                products = products.map { it.toUi(current.warehouse) },
                cart = cart.snapshot(),
                checkoutState = CheckoutUiState.Unavailable,
                demoData = false,
                catalogLoading = catalogLoading,
                catalogHasMore = catalogHasMore,
                catalogError = catalogError,
                scanLoading = scanLoading,
                scanError = scanError,
                quoteLoading = quoteLoading,
                quoteError = quoteError,
                invalidQuantityForLine = invalidQuantityForLine,
            ),
        )
    }

    private fun sameWorkspace(left: CashierIdentity, right: CashierIdentity): Boolean =
        left.cashier == right.cashier &&
            left.sessionName == right.sessionName &&
            left.posProfile == right.posProfile

    private fun CatalogFailure.userMessage(): String = when (this) {
        CatalogFailure.AuthenticationRequired -> "Session expired. Please sign in again."
        CatalogFailure.AuthorizationDenied -> "You do not have permission to use the catalog."
        CatalogFailure.Unavailable -> "Catalog is unavailable. Check your connection."
        is CatalogFailure.Stable -> "Catalog request could not be completed."
        is CatalogFailure.Protocol -> "Catalog returned an unexpected response."
    }

    private fun CartMutation.userMessage(): String = when (this) {
        CartMutation.DuplicateSerial -> "This serial is already in the cart."
        CartMutation.InvalidQuantity -> "Quantity is not valid."
        CartMutation.InvalidSerialQuantity -> "Serialized items require quantity 1."
        CartMutation.QuoteMismatch -> "The server quote did not match this cart row."
        CartMutation.RowLimit -> "Cart limit reached. Remove a row before adding another."
        is CartMutation.Applied -> ""
    }

    private fun CatalogProduct.toUi(warehouse: String) = CashierProduct(
        itemCode = itemCode,
        itemName = itemName,
        categoryId = "all",
        price = priceListRate,
        currency = currency,
        priceList = "Server price",
        availableQuantity = availableQuantity,
        uom = uom,
        warehouse = warehouse,
    )

    private fun CartEntry.toDraft(quantity: String) = CartLineDraft(
        itemCode = itemCode,
        itemName = itemName,
        quantity = quantity,
        uom = uom,
        batchNo = batchNo,
        serialNo = serialNo,
        warehouse = warehouse,
        conversionFactor = conversionFactor,
        scanWarnings = scanWarnings,
    )
}
