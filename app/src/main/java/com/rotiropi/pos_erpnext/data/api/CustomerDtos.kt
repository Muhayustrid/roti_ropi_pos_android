package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.Serializable

@Serializable
data class CustomerSearchResponseDto(
    val customers: List<CustomerDto> = emptyList(),
    val has_more: Boolean = false
)

@Serializable
data class CustomerDto(
    val name: String,
    val customer_name: String,
    val mobile_no: String? = null,
    val email_id: String? = null
)
