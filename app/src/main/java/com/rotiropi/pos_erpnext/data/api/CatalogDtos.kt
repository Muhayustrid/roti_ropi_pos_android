package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.Serializable

@Serializable
data class CatalogSearchResponseDto(
    val items: List<CatalogSearchItemDto>,
    val page: PageDto
)

@Serializable
data class CatalogSearchItemDto(
    val item_code: String,
    val item_name: String,
    val description: String,
    val image: String?,
    val uom: String,
    val price_list_rate: String,
    val currency: String,
    val available_qty: String
)

@Serializable
data class CatalogScanRequestDto(val pos_profile: String, val value: String)

@Serializable
data class CatalogScanResponseDto(val scan: CatalogScanDto, val warnings: List<ApiWarningDto>)

@Serializable
data class CatalogScanDto(
    val item_code: String,
    val barcode: String?,
    val batch_no: String?,
    val serial_no: String?,
    val uom: String,
    val conversion_factor: String,
    val warehouse: String
)

@Serializable
data class QuoteItemRequestDto(
    val pos_profile: String,
    val customer: String? = null,
    val item_code: String,
    val qty: String,
    val uom: String? = null,
    val batch_no: String? = null
)

@Serializable
data class QuoteItemResponseDto(val item: QuoteItemDto, val warnings: List<ApiWarningDto>)

@Serializable
data class QuoteItemDto(
    val item_code: String,
    val qty: String,
    val uom: String,
    val conversion_factor: String,
    val warehouse: String,
    val available_qty: String,
    val price_list_rate: String,
    val discount_percentage: String,
    val rate: String,
    val item_tax_template: String?
)
