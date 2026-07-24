package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.Serializable

@Serializable
data class CatalogSearchResponseDto(
    val items: List<CatalogItemDto> = emptyList(),
    val has_more: Boolean = false
)

@Serializable
data class CatalogScanRequestDto(
    val barcode: String
)

@Serializable
data class CatalogScanResponseDto(
    val item: CatalogItemDto? = null
)

@Serializable
data class CatalogItemDto(
    val item_code: String,
    val item_name: String,
    val rate: String,
    val stock_uom: String,
    val has_batch_no: Int = 0,
    val has_serial_no: Int = 0
)

@Serializable
data class QuoteItemRequestDto(
    val item_code: String,
    val qty: String
)

@Serializable
data class QuoteItemResponseDto(
    val amount: String
)
