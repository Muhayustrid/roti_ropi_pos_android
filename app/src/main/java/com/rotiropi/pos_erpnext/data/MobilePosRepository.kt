package com.rotiropi.pos_erpnext.data

import com.rotiropi.pos_erpnext.data.api.ApiResult
import com.rotiropi.pos_erpnext.data.api.AuthenticatedMobilePosApiClient
import com.rotiropi.pos_erpnext.data.api.BootstrapResponseDto
import com.rotiropi.pos_erpnext.data.api.CatalogScanRequestDto
import com.rotiropi.pos_erpnext.data.api.CatalogScanResponseDto
import com.rotiropi.pos_erpnext.data.api.CatalogSearchResponseDto
import com.rotiropi.pos_erpnext.data.api.CustomerSearchResponseDto
import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import com.rotiropi.pos_erpnext.data.api.MobilePosRequest
import com.rotiropi.pos_erpnext.data.api.ApiCallCancellation
import com.rotiropi.pos_erpnext.data.api.OpenSessionRequestDto
import com.rotiropi.pos_erpnext.data.api.OpenSessionResponseDto
import com.rotiropi.pos_erpnext.data.api.QuoteItemRequestDto
import com.rotiropi.pos_erpnext.data.api.QuoteItemResponseDto
import com.rotiropi.pos_erpnext.data.api.QuoteCartRequestDto
import com.rotiropi.pos_erpnext.data.api.QuoteCartResponseDto
import com.rotiropi.pos_erpnext.data.api.SubmitSaleRequestDto
import com.rotiropi.pos_erpnext.data.api.SubmitSaleResponseDto
import com.rotiropi.pos_erpnext.data.api.SaleDetailDto
import com.rotiropi.pos_erpnext.data.api.SaleDetailResponseDto
import com.rotiropi.pos_erpnext.data.api.SaleSummaryDto
import com.rotiropi.pos_erpnext.data.api.SaleListResponseDto
import com.rotiropi.pos_erpnext.data.api.QuoteReturnRequestDto
import com.rotiropi.pos_erpnext.data.api.QuoteReturnResponseDto
import com.rotiropi.pos_erpnext.data.api.ReturnQuoteDto
import com.rotiropi.pos_erpnext.data.api.CreateReturnRequestDto
import com.rotiropi.pos_erpnext.data.api.CreateReturnResponseDto
import com.rotiropi.pos_erpnext.data.api.ClosingCountedAmountPolicyDto
import com.rotiropi.pos_erpnext.data.api.ClosingDto
import com.rotiropi.pos_erpnext.data.api.ClosingPreviewResponseDto
import com.rotiropi.pos_erpnext.data.api.ClosingStatusResponseDto
import com.rotiropi.pos_erpnext.data.api.ExpectedPaymentDto
import com.rotiropi.pos_erpnext.data.api.SubmitClosingRequestDto
import com.rotiropi.pos_erpnext.data.api.SubmitClosingResponseDto
import com.rotiropi.pos_erpnext.data.api.SessionCurrentResponseDto
import com.rotiropi.pos_erpnext.data.api.TransportFailureKind
import com.rotiropi.pos_erpnext.recovery.RecoveryExecution
import com.rotiropi.pos_erpnext.recovery.RecoverySpec
import com.rotiropi.pos_erpnext.ui.payment.CheckoutQuote
import com.rotiropi.pos_erpnext.ui.payment.toDomain
import java.util.concurrent.CountDownLatch
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.JsonElement

/**
 * Domain model for a POS profile, kept separate from the wire DTOs.
 */
data class PosProfile(
    val name: String,
    val company: String,
    val warehouse: String,
    val currency: String,
    val sellingPriceList: String,
    val customer: String,
    val allowPartialPayment: Boolean,
    val invoiceMode: String,
    val openingPaymentModes: List<OpeningPaymentMode>,
    val openingAmountPolicy: OpeningAmountPolicy?
)

data class OpeningPaymentMode(
    val modeOfPayment: String,
    val suggestedOpeningAmount: String,
    val amountEditable: Boolean
)

data class OpeningAmountPolicy(
    val currency: String,
    val decimalPlaces: Int,
    val minimum: String,
    val apiSyntax: String,
    val rounding: String,
    val policyVersion: String
)

/**
 * Domain opening status, independent of the transport serializer enum.
 */
enum class OpeningStatus {
    OPEN,
    CLOSED,
    CANCELLED,
    UNSUPPORTED;

    companion object {
        fun from(dto: com.rotiropi.pos_erpnext.data.api.OpeningStatus): OpeningStatus = when (dto) {
            com.rotiropi.pos_erpnext.data.api.OpeningStatus.OPEN -> OPEN
            com.rotiropi.pos_erpnext.data.api.OpeningStatus.CLOSED -> CLOSED
            com.rotiropi.pos_erpnext.data.api.OpeningStatus.CANCELLED -> CANCELLED
            com.rotiropi.pos_erpnext.data.api.OpeningStatus.UNSUPPORTED -> UNSUPPORTED
        }
    }
}

data class OpeningWarning(
    val code: String,
    val message: String,
    val details: Map<String, JsonElement>
)

data class OpeningBalance(val modeOfPayment: String, val openingAmount: String)

data class OpeningSession(
    val name: String,
    val posProfile: String,
    val company: String,
    val user: String,
    val status: OpeningStatus,
    val postingDate: String,
    val periodStartDate: String,
    val openingBalances: List<OpeningBalance>,
    val warnings: List<OpeningWarning>
)

data class PosCapabilities(
    val openSession: Boolean,
    val submitSale: Boolean,
    val createReturn: Boolean,
    val cancelSale: Boolean,
    val closeSession: Boolean
) {
    val any: Boolean
        get() = openSession || submitSale || createReturn || cancelSale || closeSession

    val hasEnabled: Boolean
        get() = any

    companion object {
        val DISABLED = PosCapabilities(
            openSession = false,
            submitSale = false,
            createReturn = false,
            cancelSale = false,
            closeSession = false
        )
    }
}

data class BootstrapUser(val name: String, val fullName: String)

data class ClosingProjection(
    val name: String?,
    val status: ClosingProjectionState,
    val phase: String?,
    val statusEndpoint: String?,
    val failureCode: String?,
    val failureMessage: String?,
)

enum class ClosingProjectionState {
    PROCESSING,
    DRAFT,
    QUEUED,
    SUBMITTED,
    FAILED,
    CANCELLED,
    UNSUPPORTED;

    companion object {
        fun from(dto: com.rotiropi.pos_erpnext.data.api.ClosingProjectionStatus): ClosingProjectionState =
            valueOf(dto.name)
    }
}

data class CustomerSearchPage(
    val customers: List<Customer>,
    val start: Int,
    val limit: Int,
    val hasMore: Boolean,
)

data class Customer(
    val id: String,
    val displayLabel: String,
    val mobile: String?,
    val isDefaultWalkIn: Boolean,
)

sealed interface CustomerSearchResult {
    data class Success(val page: CustomerSearchPage) : CustomerSearchResult
    data class Failure(val reason: CustomerSearchFailure) : CustomerSearchResult
}

sealed interface CustomerSearchFailure {
    data object AuthenticationRequired : CustomerSearchFailure
    data object AuthorizationDenied : CustomerSearchFailure
    data object Unavailable : CustomerSearchFailure
    data class Stable(val code: String) : CustomerSearchFailure
    data class Protocol(val reason: String) : CustomerSearchFailure
}

/**
 * Immutable, render-equivalent snapshot mapped from the versioned bootstrap DTO.
 */
data class BootstrapData(
    val user: BootstrapUser,
    val profiles: List<PosProfile>,
    val selectedProfile: PosProfile?,
    val opening: OpeningSession?,
    val capabilities: PosCapabilities,
    val posMode: String,
    val closing: ClosingProjection? = null,
)

sealed interface BootstrapFailure {
    data object AuthRequired : BootstrapFailure
    data object Unavailable : BootstrapFailure
    data class Protocol(val reason: String) : BootstrapFailure
}

/**
 * Snapshot published by [MobilePosRepository]. Reads are side-effect free and never
 * trigger a refresh; the repository owns all in-memory capability state.
 */
internal fun com.rotiropi.pos_erpnext.data.api.ProfileDto.toDomain(): PosProfile = PosProfile(
    name = name,
    company = company,
    warehouse = warehouse,
    currency = currency,
    sellingPriceList = selling_price_list,
    customer = customer,
    allowPartialPayment = allow_partial_payment,
    invoiceMode = invoice_mode,
    openingPaymentModes = opening_payment_modes.map {
        OpeningPaymentMode(
            modeOfPayment = it.mode_of_payment,
            suggestedOpeningAmount = it.suggested_opening_amount,
            amountEditable = it.amount_editable
        )
    },
    openingAmountPolicy = opening_amount_policy?.let {
        OpeningAmountPolicy(
            currency = it.currency,
            decimalPlaces = it.decimal_places,
            minimum = it.minimum,
            apiSyntax = it.api_syntax,
            rounding = it.rounding,
            policyVersion = it.policy_version
        )
    }
)

data class RepositoryState(
    val bootstrap: BootstrapData? = null,
    val bootstrapFailure: BootstrapFailure? = null,
) {
    val selectedProfile: PosProfile?
        get() = bootstrap?.selectedProfile

    val opening: OpeningSession?
        get() = bootstrap?.opening

    val closing: ClosingProjection?
        get() = bootstrap?.closing

    val capabilities: PosCapabilities
        get() = bootstrap?.capabilities ?: PosCapabilities.DISABLED

    val profiles: List<PosProfile>
        get() = bootstrap?.profiles ?: emptyList()

    val hasSelection: Boolean
        get() = bootstrap?.selectedProfile != null
}

/**
 * Explicit refresh trigger. Only the authoritative events listed in the API
 * integration contract may request a refresh; [RETRY] re-enables mutations after
 * a failed required refresh.
 */
enum class BootstrapRefreshTrigger {
    APP_OPEN,
    AUTH_SUCCESS,
    PROFILE_SELECTED,
    PROFILE_CHANGED,
    OPENING_COMPLETED,
    CLOSING_COMPLETED,
    RETURN_COMPLETED,
    RETRY
}

sealed interface CurrentSessionResult {
    data class Success(val opening: OpeningSession?) : CurrentSessionResult
    data class Failure(val reason: BootstrapFailure) : CurrentSessionResult
    data object Discarded : CurrentSessionResult
}

sealed interface RepositoryResult {
    data class Success(val state: RepositoryState) : RepositoryResult
    data class Failure(val reason: BootstrapFailure) : RepositoryResult
    data object Discarded : RepositoryResult
}

internal fun openingRecoverySpec(
    request: OpenSessionRequestDto,
    json: Json,
) = RecoverySpec(
    endpoint = MobilePosEndpoint.SESSIONS_OPEN,
    body = request,
    bodySerializer = OpenSessionRequestDto.serializer(),
    responseDeserializer = OpenSessionResponseDto.serializer(),
    json = json,
)

internal fun saleRecoverySpec(request: SubmitSaleRequestDto, json: Json) = RecoverySpec(
    endpoint = MobilePosEndpoint.SALES_SUBMIT,
    body = request,
    bodySerializer = SubmitSaleRequestDto.serializer(),
    responseDeserializer = SubmitSaleResponseDto.serializer(),
    json = json,
)

internal fun returnRecoverySpec(request: CreateReturnRequestDto, json: Json) = RecoverySpec(
    endpoint = MobilePosEndpoint.SALES_CREATE_RETURN,
    body = request,
    bodySerializer = CreateReturnRequestDto.serializer(),
    responseDeserializer = CreateReturnResponseDto.serializer(),
    json = json,
)

internal fun closingRecoverySpec(request: SubmitClosingRequestDto, json: Json) = RecoverySpec(
    endpoint = MobilePosEndpoint.CLOSING_SUBMIT,
    body = request,
    bodySerializer = SubmitClosingRequestDto.serializer(),
    responseDeserializer = SubmitClosingResponseDto.serializer(),
    json = json,
)

data class ClosingPreviewBinding(
    val openingEntry: String,
    val posProfile: String,
    val cashier: String,
    val invoiceCount: Int,
    val paymentModes: List<String>,
)

data class ExpectedClosingPayment(
    val modeOfPayment: String,
    val openingAmount: String,
    val expectedAmount: String,
)

data class ClosingCountedAmountPolicy(
    val currency: String,
    val decimalPlaces: Int,
    val maxScale: Int,
    val apiSyntax: String,
    val minimum: String,
    val maximum: String,
    val rounding: String,
    val policyVersion: String,
)

data class ClosingPreview(
    val opening: OpeningSession,
    val previewId: String,
    val previewVersion: String,
    val binding: ClosingPreviewBinding,
    val invoiceCount: Int,
    val grandTotal: String,
    val netTotal: String,
    val totalQuantity: String,
    val totalTaxesAndCharges: String,
    val expectedPayments: List<ExpectedClosingPayment>,
    val countedAmountPolicy: ClosingCountedAmountPolicy,
)

data class ClosingPayment(
    val modeOfPayment: String,
    val openingAmount: String,
    val expectedAmount: String,
    val countedAmount: String,
    val difference: String,
)

data class ClosingReconciliation(
    val expectedTotal: String,
    val countedTotal: String,
    val differenceTotal: String,
)

data class ClosingReceipt(
    val name: String,
    val openingEntry: String,
    val posProfile: String,
    val status: com.rotiropi.pos_erpnext.data.api.ClosingStatus,
    val invoiceCount: Int,
    val grandTotal: String,
    val netTotal: String,
    val totalQuantity: String,
    val totalTaxesAndCharges: String,
    val payments: List<ClosingPayment>,
    val reconciliation: ClosingReconciliation,
    val failureCode: String?,
    val failureMessage: String?,
)

sealed interface ClosingReadResult<out T> {
    data class Success<T>(val data: T, val rawResponse: ByteArray? = null) : ClosingReadResult<T>
    data class Failure(val code: String) : ClosingReadResult<Nothing>
}

internal fun ClosingDto.toClosingReceipt() = ClosingReceipt(
    name = name,
    openingEntry = opening_entry,
    posProfile = pos_profile,
    status = status,
    invoiceCount = invoice_count,
    grandTotal = grand_total,
    netTotal = net_total,
    totalQuantity = total_quantity,
    totalTaxesAndCharges = total_taxes_and_charges,
    payments = payments.map {
        ClosingPayment(
            modeOfPayment = it.mode_of_payment,
            openingAmount = it.opening_amount,
            expectedAmount = it.expected_amount,
            countedAmount = it.counted_amount,
            difference = it.difference,
        )
    },
    reconciliation = ClosingReconciliation(
        expectedTotal = reconciliation.expected_total,
        countedTotal = reconciliation.counted_total,
        differenceTotal = reconciliation.difference_total,
    ),
    failureCode = failure?.code,
    failureMessage = failure?.message,
)

data class SaleHistoryPage(val sales: List<SaleSummaryDto>, val page: com.rotiropi.pos_erpnext.data.api.PageDto)

sealed interface SaleReadResult<out T> {
    data class Success<T>(val data: T) : SaleReadResult<T>
    data class Failure(val code: String) : SaleReadResult<Nothing>
}

sealed interface CheckoutQuoteResult {
    data class Success(val quote: CheckoutQuote) : CheckoutQuoteResult
    data class Failure(val reason: CatalogFailure) : CheckoutQuoteResult
}

/**
 * Sole in-memory owner of bootstrap, profile, opening, and capability state.
 *
 * - `bootstrap(profileName)` calls the versioned bootstrap endpoint and maps the
 *   DTOs to immutable domain models.
 * - One profile is auto-selected even when the server reports no selection;
 *   multiple profiles without a selection require explicit selection and force
 *   every mutation capability off.
 * - Capabilities are server-derived only while a selection exists.
 * - `refreshCapabilities(trigger)` coalesces concurrent callers into exactly one
 *   in-flight bootstrap request; every caller observes the same result.
 * - Bootstrap completion, state observation, render-equivalent reads, and refresh
 *   failure never recursively trigger a refresh.
 */
class MobilePosRepository(
    private val client: AuthenticatedMobilePosApiClient,
    private val openSession: (OpenSessionRequestDto) -> RecoveryExecution = {
        error("Opening recovery is not configured.")
    },
    private val submitSale: (SubmitSaleRequestDto) -> RecoveryExecution = {
        error("Sale recovery is not configured.")
    },
    private val createReturn: (CreateReturnRequestDto) -> RecoveryExecution = {
        error("Return recovery is not configured.")
    },
    private val submitClosing: (SubmitClosingRequestDto) -> RecoveryExecution = {
        error("Closing recovery is not configured.")
    },
) {
    private val lock = Any()
    @Volatile
    private var currentState: RepositoryState = RepositoryState()
    private var epoch = 0L
    private var inFlightRefresh: PendingRefresh? = null

    val state: RepositoryState
        get() = currentState

    val authorityGeneration: Long
        get() = synchronized(lock) { epoch }

    /**
     * Initial bootstrap. [profileName] is the caller-requested profile, forwarded
     * as the optional `pos_profile` query field.
     */
    fun bootstrap(profileName: String?): RepositoryResult {
        val query = if (profileName != null) mapOf("pos_profile" to profileName) else emptyMap()
        val requestEpoch = synchronized(lock) { epoch }
        return executeBootstrap(query, requestEpoch)
    }

    fun openSession(request: OpenSessionRequestDto): RecoveryExecution = openSession.invoke(request)
    fun submitSale(request: SubmitSaleRequestDto): RecoveryExecution = submitSale.invoke(request)
    fun createReturn(request: CreateReturnRequestDto): RecoveryExecution = createReturn.invoke(request)
    fun submitClosing(request: SubmitClosingRequestDto): RecoveryExecution = submitClosing.invoke(request)

    fun previewClosing(
        posProfile: String,
        cancellation: ApiCallCancellation = ApiCallCancellation(),
    ): ClosingReadResult<ClosingPreview> = when (val result = client.execute(
        MobilePosRequest.get(MobilePosEndpoint.CLOSING_PREVIEW, mapOf("pos_profile" to posProfile)),
        ClosingPreviewResponseDto.serializer(),
        cancellation,
    )) {
        is ApiResult.Success -> ClosingReadResult.Success(result.data.toDomain(), result.rawResponse)
        is ApiResult.ExpectedFailure -> ClosingReadResult.Failure(result.error.code)
        is ApiResult.ProtocolFailure -> ClosingReadResult.Failure("PROTOCOL_ERROR")
        is ApiResult.TransportFailure -> ClosingReadResult.Failure(
            if (result.kind == TransportFailureKind.AUTHENTICATION_REQUIRED) "AUTH_REQUIRED" else "UNAVAILABLE",
        )
    }

    fun closingStatus(
        name: String,
        cancellation: ApiCallCancellation = ApiCallCancellation(),
    ): ClosingReadResult<ClosingReceipt> = when (val result = client.execute(
        MobilePosRequest.get(MobilePosEndpoint.CLOSING_STATUS, mapOf("name" to name)),
        ClosingStatusResponseDto.serializer(),
        cancellation,
    )) {
        is ApiResult.Success -> ClosingReadResult.Success(result.data.closing.toDomain(), result.rawResponse)
        is ApiResult.ExpectedFailure -> ClosingReadResult.Failure(result.error.code)
        is ApiResult.ProtocolFailure -> ClosingReadResult.Failure("PROTOCOL_ERROR")
        is ApiResult.TransportFailure -> ClosingReadResult.Failure(
            if (result.kind == TransportFailureKind.AUTHENTICATION_REQUIRED) "AUTH_REQUIRED" else "UNAVAILABLE",
        )
    }

    fun listSales(
        posProfile: String,
        status: String = "all",
        query: String = "",
        start: Int = 0,
        limit: Int = 20,
        cancellation: ApiCallCancellation = ApiCallCancellation(),
    ): SaleReadResult<SaleHistoryPage> {
        require(start >= 0)
        require(limit in 1..50)
        val fields = linkedMapOf("pos_profile" to posProfile, "status" to status, "start" to start.toString(), "limit" to limit.toString())
        if (query.isNotBlank()) fields["q"] = query
        return when (val result = client.execute(MobilePosRequest.get(MobilePosEndpoint.SALES_LIST, fields), SaleListResponseDto.serializer(), cancellation)) {
            is ApiResult.Success -> SaleReadResult.Success(SaleHistoryPage(result.data.sales, result.data.page))
            is ApiResult.ExpectedFailure -> SaleReadResult.Failure(result.error.code)
            is ApiResult.ProtocolFailure -> SaleReadResult.Failure("PROTOCOL_ERROR")
            is ApiResult.TransportFailure -> SaleReadResult.Failure(if (result.kind == TransportFailureKind.AUTHENTICATION_REQUIRED) "AUTH_REQUIRED" else "UNAVAILABLE")
        }
    }

    fun quoteReturn(
        request: QuoteReturnRequestDto,
        cancellation: ApiCallCancellation = ApiCallCancellation(),
    ): SaleReadResult<ReturnQuoteDto> = when (val result = client.execute(
        MobilePosRequest.post(MobilePosEndpoint.SALES_QUOTE_RETURN, request, QuoteReturnRequestDto.serializer(), Json),
        QuoteReturnResponseDto.serializer(),
        cancellation,
    )) {
        is ApiResult.Success -> SaleReadResult.Success(result.data.return_quote)
        is ApiResult.ExpectedFailure -> SaleReadResult.Failure(result.error.code)
        is ApiResult.ProtocolFailure -> SaleReadResult.Failure("PROTOCOL_ERROR")
        is ApiResult.TransportFailure -> SaleReadResult.Failure(if (result.kind == TransportFailureKind.AUTHENTICATION_REQUIRED) "AUTH_REQUIRED" else "UNAVAILABLE")
    }

    fun quoteCart(request: QuoteCartRequestDto, cancellation: ApiCallCancellation): CheckoutQuoteResult {
        val transport = MobilePosRequest.post(MobilePosEndpoint.SALES_QUOTE_CART, request, QuoteCartRequestDto.serializer(), Json)
        return when (val result = client.execute(transport, QuoteCartResponseDto.serializer(), cancellation)) {
            is ApiResult.Success -> CheckoutQuoteResult.Success(
                CheckoutQuote(result.data.grand_total, result.data.payable, result.data.currency, result.data.payment_modes.map { it.toDomain() }, result.data.payment_amount_policy.toDomain(), result.data.items, result.data.taxes, 0),
            )
            else -> CheckoutQuoteResult.Failure(result.catalogFailure())
        }
    }

    fun getSale(name: String, cancellation: ApiCallCancellation = ApiCallCancellation()): SaleDetailDto? {
        return (getSaleResult(name, cancellation) as? SaleReadResult.Success)?.data
    }

    fun getSaleResult(name: String, cancellation: ApiCallCancellation = ApiCallCancellation()): SaleReadResult<SaleDetailDto> {
        val request = MobilePosRequest.get(MobilePosEndpoint.SALES_GET, mapOf("name" to name))
        return when (val result = client.execute(request, SaleDetailResponseDto.serializer(), cancellation)) {
            is ApiResult.Success -> SaleReadResult.Success(result.data.sale)
            is ApiResult.ExpectedFailure -> SaleReadResult.Failure(result.error.code)
            is ApiResult.ProtocolFailure -> SaleReadResult.Failure("PROTOCOL_ERROR")
            is ApiResult.TransportFailure -> SaleReadResult.Failure(if (result.kind == TransportFailureKind.AUTHENTICATION_REQUIRED) "AUTH_REQUIRED" else "UNAVAILABLE")
        }
    }

    fun searchCustomers(
        query: String,
        posProfile: String,
        start: Int,
        limit: Int,
        cancellation: ApiCallCancellation,
    ): CustomerSearchResult {
        require(start >= 0)
        require(limit in 1..20)
        val request = MobilePosRequest.get(
            MobilePosEndpoint.CUSTOMERS_SEARCH,
            mapOf("q" to query, "pos_profile" to posProfile, "start" to start.toString(), "limit" to limit.toString()),
        )
        return when (val result = client.execute(request, CustomerSearchResponseDto.serializer(), cancellation)) {
            is ApiResult.Success -> CustomerSearchResult.Success(
                CustomerSearchPage(
                    result.data.customers.map { Customer(it.name, it.customer_name, it.mobile_no, it.is_default_walk_in) },
                    result.data.page.start,
                    result.data.page.limit,
                    result.data.page.has_more,
                ),
            )
            is ApiResult.ExpectedFailure -> CustomerSearchResult.Failure(CustomerSearchFailure.Stable(result.error.code))
            is ApiResult.ProtocolFailure -> CustomerSearchResult.Failure(CustomerSearchFailure.Protocol(result.reason))
            is ApiResult.TransportFailure -> CustomerSearchResult.Failure(
                when (result.kind) {
                    TransportFailureKind.AUTHENTICATION_REQUIRED -> CustomerSearchFailure.AuthenticationRequired
                    TransportFailureKind.ROUTE_FORBIDDEN -> CustomerSearchFailure.AuthorizationDenied
                    else -> CustomerSearchFailure.Unavailable
                },
            )
        }
    }

    fun searchCatalog(
        query: String,
        posProfile: String,
        start: Int,
        limit: Int,
        cancellation: ApiCallCancellation,
    ): CatalogSearchResult {
        require(start >= 0)
        require(limit in 1..100)
        val request = MobilePosRequest.get(
            MobilePosEndpoint.CATALOG_SEARCH,
            mapOf(
                "q" to query,
                "pos_profile" to posProfile,
                "start" to start.toString(),
                "limit" to limit.toString(),
            ),
        )
        return when (val result = client.execute(request, CatalogSearchResponseDto.serializer(), cancellation)) {
            is ApiResult.Success -> CatalogSearchResult.Success(
                CatalogPage(
                    items = result.data.items.map {
                        CatalogProduct(
                            itemCode = it.item_code,
                            itemName = it.item_name,
                            description = it.description,
                            image = it.image,
                            uom = it.uom,
                            priceListRate = it.price_list_rate,
                            currency = it.currency,
                            availableQuantity = it.available_qty,
                        )
                    },
                    start = result.data.page.start,
                    limit = result.data.page.limit,
                    hasMore = result.data.page.has_more,
                ),
            )
            else -> CatalogSearchResult.Failure(result.catalogFailure())
        }
    }

    fun scanCatalog(
        posProfile: String,
        value: String,
        cancellation: ApiCallCancellation,
    ): CatalogScanResult {
        val request = MobilePosRequest.post(
            MobilePosEndpoint.CATALOG_SCAN,
            CatalogScanRequestDto(pos_profile = posProfile, value = value),
            CatalogScanRequestDto.serializer(),
            Json,
        )
        return when (val result = client.execute(request, CatalogScanResponseDto.serializer(), cancellation)) {
            is ApiResult.Success -> CatalogScanResult.Success(
                scan = CatalogScan(
                    itemCode = result.data.scan.item_code,
                    barcode = result.data.scan.barcode,
                    batchNo = result.data.scan.batch_no,
                    serialNo = result.data.scan.serial_no,
                    uom = result.data.scan.uom,
                    conversionFactor = result.data.scan.conversion_factor,
                    warehouse = result.data.scan.warehouse,
                ),
                warnings = result.data.warnings.map { CatalogWarning(it.code, it.message) },
            )
            else -> CatalogScanResult.Failure(result.catalogFailure())
        }
    }

    fun quoteItem(
        request: CatalogQuoteRequest,
        cancellation: ApiCallCancellation,
    ): CatalogQuoteResult {
        val wireRequest = QuoteItemRequestDto(
            pos_profile = request.posProfile,
            customer = request.customer,
            item_code = request.itemCode,
            qty = request.quantity,
            uom = request.uom,
            batch_no = request.batchNo,
        )
        val transportRequest = MobilePosRequest.post(
            MobilePosEndpoint.CATALOG_QUOTE_ITEM,
            wireRequest,
            QuoteItemRequestDto.serializer(),
            Json,
        )
        return when (val result = client.execute(transportRequest, QuoteItemResponseDto.serializer(), cancellation)) {
            is ApiResult.Success -> CatalogQuoteResult.Success(
                CatalogQuote(
                    itemCode = result.data.item.item_code,
                    quantity = result.data.item.qty,
                    uom = result.data.item.uom,
                    conversionFactor = result.data.item.conversion_factor,
                    warehouse = result.data.item.warehouse,
                    availableQuantity = result.data.item.available_qty,
                    priceListRate = result.data.item.price_list_rate,
                    discountPercentage = result.data.item.discount_percentage,
                    rate = result.data.item.rate,
                    itemTaxTemplate = result.data.item.item_tax_template,
                    warnings = result.data.warnings.map { CatalogWarning(it.code, it.message) },
                ),
            )
            else -> CatalogQuoteResult.Failure(result.catalogFailure())
        }
    }

    fun currentSession(profileName: String): CurrentSessionResult {
        val requestEpoch = synchronized(lock) { epoch }
        val request = MobilePosRequest.get(
            MobilePosEndpoint.SESSIONS_CURRENT,
            mapOf("pos_profile" to profileName),
        )
        return when (val result = client.execute(request, SessionCurrentResponseDto.serializer())) {
            is ApiResult.Success -> synchronized(lock) {
                if (epoch != requestEpoch ||
                    currentState.selectedProfile?.name?.let { it != profileName } == true
                ) return@synchronized CurrentSessionResult.Discarded
                val opening = result.data.opening_session?.toDomain()
                currentState = currentState.copy(
                    bootstrap = currentState.bootstrap?.copy(
                        opening = opening,
                        closing = result.data.closing?.toDomain(),
                    ),
                )
                CurrentSessionResult.Success(opening)
            }
            is ApiResult.TransportFailure -> CurrentSessionResult.Failure(
                if (result.kind == TransportFailureKind.AUTHENTICATION_REQUIRED) {
                    BootstrapFailure.AuthRequired
                } else {
                    BootstrapFailure.Unavailable
                }
            )
            is ApiResult.ExpectedFailure -> CurrentSessionResult.Failure(BootstrapFailure.Unavailable)
            is ApiResult.ProtocolFailure -> CurrentSessionResult.Failure(BootstrapFailure.Protocol(result.reason))
        }
    }

    /**
     * Refreshes capabilities after an authoritative trigger. Concurrent callers
     * share one in-flight bootstrap network request and all observe its result.
     */
    fun refreshCapabilities(trigger: BootstrapRefreshTrigger): RepositoryResult {
        val pending = synchronized(lock) {
            inFlightRefresh ?: PendingRefresh(epoch, refreshQueryLocked()).also { inFlightRefresh = it }
        }
        if (pending.owner.compareAndSet(false, true)) {
            try {
                pending.result.set(executeBootstrap(pending.query, pending.epoch))
            } finally {
                pending.done.countDown()
                synchronized(lock) {
                    if (inFlightRefresh === pending) inFlightRefresh = null
                }
            }
        } else {
            pending.done.await()
        }
        return pending.result.get()
    }

    /**
     * Locally selects [profileName] from the current bootstrap profiles without any
     * network call. Returns false and changes nothing when [profileName] is unknown
     * or when no bootstrap state exists yet.
     *
     * A valid local selection immediately disables every mutation capability so the
     * profile-selection UI never enables actions on unverified state; only a later
     * authoritative [refreshCapabilities] with `PROFILE_SELECTED` or
     * `PROFILE_CHANGED` restores server-derived capabilities. The profile list, user,
     * and opening are retained so the selection UI keeps rendering.
     */
    fun selectProfile(profileName: String): Boolean {
        synchronized(lock) {
            val bootstrap = currentState.bootstrap ?: return false
            val profile = bootstrap.profiles.firstOrNull { it.name == profileName } ?: return false
            epoch++
            currentState = currentState.copy(
                bootstrap = bootstrap.copy(
                    selectedProfile = profile,
                    capabilities = PosCapabilities.DISABLED
                ),
            )
            return true
        }
    }

    /**
     * Resets bootstrap, profile, opening, and capability state.
     */
    fun clear() {
        synchronized(lock) {
            epoch++
            currentState = RepositoryState()
            inFlightRefresh = null
        }
    }

    /**
     * Refresh preserves the currently selected profile so a multi-profile server
     * does not lose the selection during a capability refresh.
     */
    private fun refreshQueryLocked(): Map<String, String> =
        currentState.bootstrap?.selectedProfile?.name
            ?.let { mapOf("pos_profile" to it) }
            ?: emptyMap()

    private fun executeBootstrap(query: Map<String, String>, requestEpoch: Long): RepositoryResult {
        val request = MobilePosRequest.get(MobilePosEndpoint.BOOTSTRAP_GET, query)
        return when (val result = client.execute(request, BootstrapResponseDto.serializer())) {
            is ApiResult.Success -> publishSuccess(result.data.toDomain(), requestEpoch)
            is ApiResult.TransportFailure -> {
                val failure = when (result.kind) {
                    TransportFailureKind.AUTHENTICATION_REQUIRED -> BootstrapFailure.AuthRequired
                    else -> BootstrapFailure.Unavailable
                }
                publishFailure(failure, requestEpoch)
            }
            is ApiResult.ExpectedFailure -> publishFailure(BootstrapFailure.Unavailable, requestEpoch)
            is ApiResult.ProtocolFailure -> publishFailure(
                BootstrapFailure.Protocol(result.reason),
                requestEpoch
            )
        }
    }

    private fun publishSuccess(mapped: BootstrapData, requestEpoch: Long): RepositoryResult =
        synchronized(lock) {
            if (epoch != requestEpoch) return@synchronized RepositoryResult.Discarded
            currentState = RepositoryState(bootstrap = mapped)
            RepositoryResult.Success(currentState)
        }

    /**
     * Records a bootstrap/refresh failure and disables every mutation capability so
     * stale enabled capabilities are never observed after a failed required refresh.
     */
    private fun publishFailure(failure: BootstrapFailure, requestEpoch: Long): RepositoryResult =
        synchronized(lock) {
            if (epoch != requestEpoch) return@synchronized RepositoryResult.Discarded
            val previous = currentState.bootstrap
            currentState = if (previous != null) {
                currentState.copy(
                    bootstrap = previous.copy(capabilities = PosCapabilities.DISABLED),
                    bootstrapFailure = failure
                )
            } else {
                currentState.copy(bootstrapFailure = failure)
            }
            RepositoryResult.Failure(failure)
        }

    private fun BootstrapResponseDto.toDomain(): BootstrapData {
        val profiles = profiles.map { it.toDomain() }
        val selected = when {
            selected_profile != null -> selected_profile!!.toDomain()
            profiles.size == 1 -> profiles.single()
            else -> null
        }
        val capabilities = if (selected != null) {
            PosCapabilities(
                openSession = capabilities.open_session,
                submitSale = capabilities.submit_sale,
                createReturn = capabilities.create_return,
                cancelSale = capabilities.cancel_sale,
                closeSession = capabilities.close_session
            )
        } else {
            PosCapabilities.DISABLED
        }
        return BootstrapData(
            user = BootstrapUser(name = user.name, fullName = user.full_name),
            profiles = profiles,
            selectedProfile = selected,
            opening = opening_session?.toDomain(),
            capabilities = capabilities,
            posMode = pos_mode,
            closing = closing?.toDomain(),
        )
    }

    private fun ClosingPreviewResponseDto.toDomain(): ClosingPreview = ClosingPreview(
        opening = opening_session.toDomain(),
        previewId = preview_id,
        previewVersion = preview_version,
        binding = ClosingPreviewBinding(
            openingEntry = preview_binding.opening_entry,
            posProfile = preview_binding.pos_profile,
            cashier = preview_binding.cashier,
            invoiceCount = preview_binding.invoice_count,
            paymentModes = preview_binding.payment_modes,
        ),
        invoiceCount = invoice_count,
        grandTotal = grand_total,
        netTotal = net_total,
        totalQuantity = total_quantity,
        totalTaxesAndCharges = total_taxes_and_charges,
        expectedPayments = expected_payments.map { it.toDomain() },
        countedAmountPolicy = counted_amount_policy.toDomain(),
    )

    private fun ExpectedPaymentDto.toDomain() = ExpectedClosingPayment(
        modeOfPayment = mode_of_payment,
        openingAmount = opening_amount,
        expectedAmount = expected_amount,
    )

    private fun ClosingCountedAmountPolicyDto.toDomain() = ClosingCountedAmountPolicy(
        currency = currency,
        decimalPlaces = decimal_places,
        maxScale = max_scale,
        apiSyntax = api_syntax,
        minimum = minimum,
        maximum = maximum,
        rounding = rounding,
        policyVersion = policy_version,
    )

    private fun ClosingDto.toDomain() = toClosingReceipt()

    private fun ApiResult<*>.catalogFailure(): CatalogFailure = when (this) {
        is ApiResult.ExpectedFailure -> CatalogFailure.Stable(error.code)
        is ApiResult.ProtocolFailure -> CatalogFailure.Protocol(reason)
        is ApiResult.TransportFailure -> when (kind) {
            TransportFailureKind.AUTHENTICATION_REQUIRED -> CatalogFailure.AuthenticationRequired
            TransportFailureKind.ROUTE_FORBIDDEN -> CatalogFailure.AuthorizationDenied
            else -> CatalogFailure.Unavailable
        }
        is ApiResult.Success -> error("Success cannot be mapped to catalog failure")
    }

    private fun com.rotiropi.pos_erpnext.data.api.ClosingProjectionDto.toDomain() = ClosingProjection(
        name = name,
        status = ClosingProjectionState.from(status),
        phase = phase,
        statusEndpoint = status_endpoint,
        failureCode = failure?.code,
        failureMessage = failure?.message,
    )

    private fun com.rotiropi.pos_erpnext.data.api.OpeningSessionDto.toDomain(): OpeningSession = OpeningSession(
        name = name,
        posProfile = pos_profile,
        company = company,
        user = user,
        status = OpeningStatus.from(status),
        postingDate = posting_date,
        periodStartDate = period_start_date,
        openingBalances = opening_balances.map {
            OpeningBalance(modeOfPayment = it.mode_of_payment, openingAmount = it.opening_amount)
        },
        warnings = warnings.map {
            OpeningWarning(code = it.code, message = it.message, details = it.details)
        }
    )

    private class PendingRefresh(
        val epoch: Long,
        val query: Map<String, String>
    ) {
        val owner = AtomicBoolean(false)
        val done = CountDownLatch(1)
        val result = AtomicReference<RepositoryResult>()
    }
}
