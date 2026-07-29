package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.Serializable

@Serializable
data class ClosingPreviewResponseDto(
    val opening_session: OpeningSessionDto,
    val invoice_count: Int,
    val grand_total: String,
    val expected_payments: List<ExpectedPaymentDto>
)

@Serializable
data class ExpectedPaymentDto(
    val mode_of_payment: String,
    val opening_amount: String,
    val expected_amount: String
)

@Serializable
data class SubmitClosingRequestDto(
    val pos_profile: String,
    val closing_balances: List<ClosingBalanceInputDto>
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
    val failure: ClosingFailureDto?
)

@Serializable(with = ClosingStatusSerializer::class)
enum class ClosingStatus { DRAFT, QUEUED, SUBMITTED, FAILED, CANCELLED, UNSUPPORTED }

object ClosingStatusSerializer : UnsupportedEnumSerializer<ClosingStatus>(
    values = mapOf(
        "draft" to ClosingStatus.DRAFT,
        "queued" to ClosingStatus.QUEUED,
        "submitted" to ClosingStatus.SUBMITTED,
        "failed" to ClosingStatus.FAILED,
        "cancelled" to ClosingStatus.CANCELLED
    ),
    unsupported = ClosingStatus.UNSUPPORTED
)

@Serializable
data class ClosingFailureDto(val code: String, val message: String)
