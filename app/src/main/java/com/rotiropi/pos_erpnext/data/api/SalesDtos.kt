package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.Serializable

@Serializable
data class SubmitSaleRequestDto(
    val pos_profile: String,
    val customer: String? = null,
    val walk_in_customer_name: String? = null,
    val client_accepted_grand_total: String,
    val items: List<SaleItemInputDto>,
    val payments: List<PaymentDto>
)

@Serializable
data class SaleItemInputDto(
    val item_code: String,
    val qty: String,
    val uom: String,
    val batch_no: String? = null,
    val serial_numbers: List<String>
)

@Serializable
data class PaymentDto(
    val mode_of_payment: String,
    val amount: String,
    val reference_no: String? = null
)

@Serializable
data class SubmitSaleResponseDto(val sale: SaleDetailDto)

@Serializable
data class SaleDetailResponseDto(val sale: SaleDetailDto)

@Serializable
data class SaleSummaryDto(
    val doctype: String,
    val name: String,
    val status: SaleStatus,
    val customer: String,
    val walk_in_customer_name: String?,
    val currency: String,
    val grand_total: String,
    val paid_amount: String,
    val change_amount: String,
    val posting_date: String,
    val posting_time: String
)

@Serializable(with = SaleStatusSerializer::class)
enum class SaleStatus { PAID, RETURN, CONSOLIDATED, CANCELLED, UNSUPPORTED }

object SaleStatusSerializer : UnsupportedEnumSerializer<SaleStatus>(
    values = mapOf(
        "paid" to SaleStatus.PAID,
        "return" to SaleStatus.RETURN,
        "consolidated" to SaleStatus.CONSOLIDATED,
        "cancelled" to SaleStatus.CANCELLED
    ),
    unsupported = SaleStatus.UNSUPPORTED
)

@Serializable
data class SaleDetailDto(
    val summary: SaleSummaryDto,
    val items: List<SaleItemDto>,
    val taxes: List<SaleTaxDto>,
    val payments: List<PaymentDto>
)

@Serializable
data class SaleItemDto(
    val row_id: String,
    val item_code: String,
    val item_name: String,
    val qty: String,
    val uom: String,
    val conversion_factor: String,
    val rate: String,
    val amount: String,
    val batch_no: String?,
    val serial_numbers: List<String>
)

@Serializable
data class SaleTaxDto(
    val description: String,
    val rate: String,
    val tax_amount: String,
    val total: String
)

@Serializable
data class SaleListResponseDto(val sales: List<SaleSummaryDto>, val page: PageDto)

@Serializable
data class CreateReturnRequestDto(
    val source_name: String,
    val items: List<ReturnItemInputDto>,
    val payments: List<PaymentDto>,
    val reason: String
)

@Serializable
data class ReturnItemInputDto(val source_item_row: String, val qty: String)

@Serializable
data class CreateReturnResponseDto(val return_sale: SaleDetailDto)
