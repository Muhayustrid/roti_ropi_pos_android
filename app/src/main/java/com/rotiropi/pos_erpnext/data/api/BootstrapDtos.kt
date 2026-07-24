package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.Serializable

@Serializable
data class BootstrapResponseDto(
    val user: String? = null,
    val profiles: List<ProfileDto> = emptyList(),
    val active_opening: OpeningDto? = null,
    val capabilities: CapabilitiesDto? = null
)

@Serializable
data class ProfileDto(
    val name: String,
    val company: String,
    val pos_profile_name: String,
    val warehouse: String
)

@Serializable
data class OpeningDto(
    val name: String,
    val status: String,
    val creation: String
)

@Serializable
data class CapabilitiesDto(
    val open_session: Boolean = false,
    val submit_sale: Boolean = false,
    val create_return: Boolean = false,
    val close_session: Boolean = false,
    val cancel_sale: Boolean = false
)
