package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.Serializable

@Serializable
data class SubmitSaleRequestDto(
    val customer: String,
    val items: List<SaleItemInputDto>,
    val payments: List<SalePaymentInputDto>
)

@Serializable
data class SaleItemInputDto(
    val item_code: String,
    val qty: String,
    val rate: String
)

@Serializable
data class SalePaymentInputDto(
    val mode_of_payment: String,
    val amount: String
)

@Serializable
data class SubmitSaleResponseDto(
    val name: String,
    val grand_total: String,
    val status: String
)

@Serializable
data class SaleDto(
    val name: String,
    val customer: String,
    val grand_total: String,
    val posting_date: String
)

@Serializable
data class SaleListResponseDto(
    val sales: List<SaleDto> = emptyList(),
    val has_more: Boolean = false
)

@Serializable
data class CreateReturnRequestDto(
    val invoice_name: String,
    val items: List<SaleItemInputDto>
)

@Serializable
data class CreateReturnResponseDto(
    val return_invoice_name: String
)
