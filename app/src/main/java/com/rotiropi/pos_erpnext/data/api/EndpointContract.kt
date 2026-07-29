package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.Serializable

@Serializable
data class EndpointContractTable(
    val api_version: String,
    val endpoints: List<EndpointContract>
)

@Serializable
data class EndpointContract(
    val module: String,
    val method_name: String,
    val path: String,
    val http_method: String,
    val requires_bearer: Boolean,
    val requires_idempotency: Boolean,
    val retry_class: String,
    val request_location: String,
    val required_request_fields: Set<String>,
    val optional_request_fields: Set<String> = emptySet(),
    val forbidden_request_fields: Set<String> = emptySet(),
    val serializer_identity: String,
    val required_response_fields: Set<String>,
    val optional_response_fields: Set<String> = emptySet(),
    val decimal_string_paths: Set<String> = emptySet()
)

enum class HttpMethod { GET, POST }

enum class RequestLocation { QUERY, JSON }

enum class RetryClass { ONE_READ_RETRY, RECOVERY_ONLY }

private const val MOBILE_POS_V1_PREFIX = "/api/method/roti_ropi_pos.api.v1"
private fun endpointPath(module: String, method: String) = "$MOBILE_POS_V1_PREFIX.$module.$method"

enum class MobilePosEndpoint(
    val method: HttpMethod,
    val path: String,
    val requestLocation: RequestLocation,
    val requiredRequestFields: Set<String>,
    val optionalRequestFields: Set<String> = emptySet(),
    val requiresIdempotency: Boolean = false,
    val retryClass: RetryClass,
    val serializerIdentity: String
) {
    BOOTSTRAP_GET(HttpMethod.GET, endpointPath("bootstrap", "get"), RequestLocation.QUERY, emptySet(), setOf("pos_profile"), retryClass = RetryClass.ONE_READ_RETRY, serializerIdentity = "BootstrapResponseDto"),
    SESSIONS_CURRENT(HttpMethod.GET, endpointPath("sessions", "current"), RequestLocation.QUERY, setOf("pos_profile"), retryClass = RetryClass.ONE_READ_RETRY, serializerIdentity = "SessionCurrentResponseDto"),
    SESSIONS_OPEN(HttpMethod.POST, endpointPath("sessions", "open"), RequestLocation.JSON, setOf("pos_profile", "opening_balances"), requiresIdempotency = true, retryClass = RetryClass.RECOVERY_ONLY, serializerIdentity = "OpenSessionResponseDto"),
    CUSTOMERS_SEARCH(HttpMethod.GET, endpointPath("customers", "search"), RequestLocation.QUERY, setOf("pos_profile"), setOf("q", "start", "limit"), retryClass = RetryClass.ONE_READ_RETRY, serializerIdentity = "CustomerSearchResponseDto"),
    CATALOG_SEARCH(HttpMethod.GET, endpointPath("catalog", "search"), RequestLocation.QUERY, setOf("pos_profile"), setOf("q", "item_group", "start", "limit"), retryClass = RetryClass.ONE_READ_RETRY, serializerIdentity = "CatalogSearchResponseDto"),
    CATALOG_SCAN(HttpMethod.POST, endpointPath("catalog", "scan"), RequestLocation.JSON, setOf("pos_profile", "value"), retryClass = RetryClass.ONE_READ_RETRY, serializerIdentity = "CatalogScanResponseDto"),
    CATALOG_QUOTE_ITEM(HttpMethod.POST, endpointPath("catalog", "quote_item"), RequestLocation.JSON, setOf("pos_profile", "item_code", "qty"), setOf("customer", "uom", "batch_no"), retryClass = RetryClass.ONE_READ_RETRY, serializerIdentity = "QuoteItemResponseDto"),
    SALES_SUBMIT(HttpMethod.POST, endpointPath("sales", "submit"), RequestLocation.JSON, setOf("pos_profile", "client_accepted_grand_total", "items", "payments"), setOf("customer", "walk_in_customer_name"), requiresIdempotency = true, retryClass = RetryClass.RECOVERY_ONLY, serializerIdentity = "SubmitSaleResponseDto"),
    SALES_GET(HttpMethod.GET, endpointPath("sales", "get"), RequestLocation.QUERY, setOf("name"), retryClass = RetryClass.ONE_READ_RETRY, serializerIdentity = "SaleDetailResponseDto"),
    SALES_LIST(HttpMethod.GET, endpointPath("sales", "list"), RequestLocation.QUERY, setOf("pos_profile", "status"), setOf("q", "start", "limit"), retryClass = RetryClass.ONE_READ_RETRY, serializerIdentity = "SaleListResponseDto"),
    SALES_CREATE_RETURN(HttpMethod.POST, endpointPath("sales", "create_return"), RequestLocation.JSON, setOf("source_name", "items", "payments", "reason"), requiresIdempotency = true, retryClass = RetryClass.RECOVERY_ONLY, serializerIdentity = "CreateReturnResponseDto"),
    CLOSING_PREVIEW(HttpMethod.GET, endpointPath("closing", "preview"), RequestLocation.QUERY, setOf("pos_profile"), retryClass = RetryClass.ONE_READ_RETRY, serializerIdentity = "ClosingPreviewResponseDto"),
    CLOSING_SUBMIT(HttpMethod.POST, endpointPath("closing", "submit"), RequestLocation.JSON, setOf("pos_profile", "closing_balances"), requiresIdempotency = true, retryClass = RetryClass.RECOVERY_ONLY, serializerIdentity = "SubmitClosingResponseDto"),
    CLOSING_STATUS(HttpMethod.GET, endpointPath("closing", "status"), RequestLocation.QUERY, setOf("name"), retryClass = RetryClass.ONE_READ_RETRY, serializerIdentity = "ClosingStatusResponseDto");

    companion object {
        fun fromPath(path: String): MobilePosEndpoint? = entries.singleOrNull { it.path == path }
    }
}
