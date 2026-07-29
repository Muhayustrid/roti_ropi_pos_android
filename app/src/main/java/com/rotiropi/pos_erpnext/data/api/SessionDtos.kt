package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.Serializable

@Serializable
data class SessionCurrentResponseDto(val opening_session: OpeningSessionDto?)

@Serializable
data class OpeningBalanceInputDto(val mode_of_payment: String, val amount: String)

@Serializable
data class OpenSessionRequestDto(
    val pos_profile: String,
    val opening_balances: List<OpeningBalanceInputDto>
)

@Serializable
data class OpenSessionResponseDto(val opening_session: OpeningSessionDto)
