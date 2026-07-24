package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.Serializable

@Serializable
data class ClosingPreviewResponseDto(
    val grand_total: String,
    val total_invoices: Int
)

@Serializable
data class SubmitClosingRequestDto(
    val pos_opening_entry: String
)

@Serializable
data class SubmitClosingResponseDto(
    val name: String,
    val status: String
)

@Serializable
data class ClosingStatusResponseDto(
    val name: String,
    val status: String
)
