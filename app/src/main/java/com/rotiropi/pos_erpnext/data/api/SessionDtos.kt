package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.Serializable

@Serializable
data class SessionResponseDto(
    val opening: OpeningDto? = null,
    val status: String? = null
)

@Serializable
data class OpenSessionRequestDto(
    val pos_profile: String,
    val opening_amount: String
)
