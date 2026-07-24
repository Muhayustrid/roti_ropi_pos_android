package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.Serializable

@Serializable
data class EndpointContractTable(
    val api_version: String,
    val endpoints: List<EndpointContract>
)

@Serializable
data class EndpointContract(
    val module: String,
    val method_name: String,
    val path: String,
    val http_method: String,
    val requires_bearer: Boolean,
    val requires_idempotency: Boolean,
    val retry_class: String
)
