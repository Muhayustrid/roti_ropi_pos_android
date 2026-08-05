package com.rotiropi.pos_erpnext.data

data class CatalogProduct(
    val itemCode: String,
    val itemName: String,
    val description: String,
    val image: String?,
    val uom: String,
    val priceListRate: String,
    val currency: String,
    val availableQuantity: String,
)

data class CatalogPage(
    val items: List<CatalogProduct>,
    val start: Int,
    val limit: Int,
    val hasMore: Boolean,
)

data class CatalogScan(
    val itemCode: String,
    val barcode: String?,
    val batchNo: String?,
    val serialNo: String?,
    val uom: String,
    val conversionFactor: String?,
    val warehouse: String,
)

data class CatalogQuoteRequest(
    val posProfile: String,
    val customer: String?,
    val itemCode: String,
    val quantity: String,
    val uom: String,
    val batchNo: String?,
    val warehouse: String? = null,
    val conversionFactor: String? = null,
)

data class CatalogWarning(
    val code: String,
    val message: String,
)

data class CatalogQuote(
    val itemCode: String,
    val quantity: String,
    val uom: String,
    val conversionFactor: String,
    val warehouse: String,
    val availableQuantity: String,
    val priceListRate: String,
    val discountPercentage: String,
    val rate: String,
    val itemTaxTemplate: String?,
    val warnings: List<CatalogWarning>,
)

sealed interface CatalogSearchResult {
    data class Success(val page: CatalogPage) : CatalogSearchResult
    data class Failure(val reason: CatalogFailure) : CatalogSearchResult
}

sealed interface CatalogScanResult {
    data class Success(val scan: CatalogScan, val warnings: List<CatalogWarning>) : CatalogScanResult
    data class Failure(val reason: CatalogFailure) : CatalogScanResult
}

sealed interface CatalogQuoteResult {
    data class Success(val quote: CatalogQuote) : CatalogQuoteResult
    data class Failure(val reason: CatalogFailure) : CatalogQuoteResult
}

sealed interface CatalogFailure {
    data object AuthenticationRequired : CatalogFailure
    data object AuthorizationDenied : CatalogFailure
    data object Unavailable : CatalogFailure
    data class Stable(val code: String) : CatalogFailure
    data class Protocol(val reason: String) : CatalogFailure
}
