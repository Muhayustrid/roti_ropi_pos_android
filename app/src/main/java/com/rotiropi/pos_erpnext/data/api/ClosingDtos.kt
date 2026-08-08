package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.Serializable

@Serializable
data class ClosingPreviewResponseDto(
    val opening_session: OpeningSessionDto,
    val preview_id: String,
    val preview_version: String,
    val preview_binding: ClosingPreviewBindingDto,
    val invoice_count: Int,
    val grand_total: String,
    val net_total: String,
    val total_quantity: String,
    val total_taxes_and_charges: String,
    val expected_payments: List<ExpectedPaymentDto>,
    val counted_amount_policy: ClosingCountedAmountPolicyDto,
)

@Serializable
data class ClosingPreviewBindingDto(
    val opening_entry: String,
    val pos_profile: String,
    val cashier: String,
    val invoice_count: Int,
    val payment_modes: List<String>,
)

@Serializable
data class ExpectedPaymentDto(
    val mode_of_payment: String,
    val opening_amount: String,
    val expected_amount: String,
)

@Serializable
data class ClosingCountedAmountPolicyDto(
    val currency: String,
    val decimal_places: Int,
    val max_scale: Int,
    val api_syntax: String,
    val minimum: String,
    val maximum: String,
    val rounding: String,
    val policy_version: String,
)

@Serializable
data class SubmitClosingRequestDto(
    val pos_profile: String,
    val preview_id: String,
    val closing_balances: List<ClosingBalanceInputDto>,
)

@Serializable
data class ClosingBalanceInputDto(val mode_of_payment: String, val closing_amount: String)

@Serializable
data class SubmitClosingResponseDto(val closing: ClosingDto)

@Serializable
data class ClosingStatusResponseDto(val closing: ClosingDto)

@Serializable
data class ClosingDto(
    val name: String,
    val opening_entry: String,
    val pos_profile: String,
    val status: ClosingStatus,
    val invoice_count: Int,
    val grand_total: String,
    val net_total: String,
    val total_quantity: String,
    val total_taxes_and_charges: String,
    val payments: List<ClosingPaymentDto>,
    val reconciliation: ClosingReconciliationDto,
    val failure: ClosingFailureDto?,
)

@Serializable
data class ClosingPaymentDto(
    val mode_of_payment: String,
    val opening_amount: String,
    val expected_amount: String,
    val counted_amount: String,
    val difference: String,
)

@Serializable
data class ClosingReconciliationDto(
    val expected_total: String,
    val counted_total: String,
    val difference_total: String,
)

@Serializable
data class ClosingProjectionDto(
    val name: String? = null,
    val status: ClosingProjectionStatus,
    val phase: String? = null,
    val status_endpoint: String? = null,
    val failure: ClosingFailureDto? = null,
)

@Serializable(with = ClosingProjectionStatusSerializer::class)
enum class ClosingProjectionStatus { PROCESSING, DRAFT, QUEUED, SUBMITTED, FAILED, CANCELLED, UNSUPPORTED }

object ClosingProjectionStatusSerializer : UnsupportedEnumSerializer<ClosingProjectionStatus>(
    values = mapOf(
        "processing" to ClosingProjectionStatus.PROCESSING,
        "draft" to ClosingProjectionStatus.DRAFT,
        "queued" to ClosingProjectionStatus.QUEUED,
        "submitted" to ClosingProjectionStatus.SUBMITTED,
        "failed" to ClosingProjectionStatus.FAILED,
        "cancelled" to ClosingProjectionStatus.CANCELLED,
    ),
    unsupported = ClosingProjectionStatus.UNSUPPORTED,
)

@Serializable(with = ClosingStatusSerializer::class)
enum class ClosingStatus { DRAFT, QUEUED, SUBMITTED, FAILED, CANCELLED, UNSUPPORTED }

object ClosingStatusSerializer : UnsupportedEnumSerializer<ClosingStatus>(
    values = mapOf(
        "draft" to ClosingStatus.DRAFT,
        "queued" to ClosingStatus.QUEUED,
        "submitted" to ClosingStatus.SUBMITTED,
        "failed" to ClosingStatus.FAILED,
        "cancelled" to ClosingStatus.CANCELLED,
    ),
    unsupported = ClosingStatus.UNSUPPORTED,
)

@Serializable
data class ClosingFailureDto(val code: String, val message: String)
