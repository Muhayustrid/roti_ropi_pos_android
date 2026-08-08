package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class UserSummaryDto(val name: String, val full_name: String)

@Serializable
data class BootstrapResponseDto(
    val user: UserSummaryDto,
    val profiles: List<ProfileDto>,
    val selected_profile: ProfileDto?,
    val opening_session: OpeningSessionDto?,
    val closing: ClosingProjectionDto? = null,
    val capabilities: CapabilitiesDto,
    val pos_mode: String
)

@Serializable
data class ProfileDto(
    val name: String,
    val company: String,
    val warehouse: String,
    val currency: String,
    val selling_price_list: String,
    val customer: String,
    val allow_partial_payment: Boolean,
    val invoice_mode: String,
    val opening_payment_modes: List<OpeningPaymentModeDto> = emptyList(),
    val opening_amount_policy: OpeningAmountPolicyDto? = null
)

@Serializable
data class OpeningPaymentModeDto(
    val mode_of_payment: String,
    val suggested_opening_amount: String,
    val amount_editable: Boolean
)

@Serializable
data class OpeningAmountPolicyDto(
    val currency: String,
    val decimal_places: Int,
    val minimum: String,
    val api_syntax: String,
    val rounding: String,
    val policy_version: String
)

@Serializable
data class OpeningBalanceDto(val mode_of_payment: String, val opening_amount: String)

@Serializable
data class ApiWarningDto(
    val code: String,
    val message: String,
    val details: Map<String, JsonElement> = emptyMap()
)

@Serializable
data class OpeningSessionDto(
    val name: String,
    val pos_profile: String,
    val company: String,
    val user: String,
    val status: OpeningStatus,
    val posting_date: String,
    val period_start_date: String,
    val opening_balances: List<OpeningBalanceDto>,
    val warnings: List<ApiWarningDto>,
    val lifecycle_state: String = "active",
    val closing: ClosingProjectionDto? = null,
)

@Serializable(with = OpeningStatusSerializer::class)
enum class OpeningStatus { OPEN, CLOSED, CANCELLED, UNSUPPORTED }

object OpeningStatusSerializer : UnsupportedEnumSerializer<OpeningStatus>(
    values = mapOf("open" to OpeningStatus.OPEN, "closed" to OpeningStatus.CLOSED, "cancelled" to OpeningStatus.CANCELLED),
    unsupported = OpeningStatus.UNSUPPORTED
)

@Serializable
data class CapabilitiesDto(
    val open_session: Boolean,
    val submit_sale: Boolean,
    val create_return: Boolean,
    val cancel_sale: Boolean,
    val close_session: Boolean
)

@Serializable
data class PageDto(val start: Int, val limit: Int, val has_more: Boolean)
