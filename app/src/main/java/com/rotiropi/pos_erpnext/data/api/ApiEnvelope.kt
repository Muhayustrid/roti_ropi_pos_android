package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class FrappeResponse<T>(
    val message: ApiEnvelope<T>? = null
)

@Serializable
data class ApiEnvelope<T>(
    val ok: Boolean,
    val data: T? = null,
    val meta: ApiMeta? = null,
    val error: ApiErrorData? = null
) {
    fun isCompatibleVersion(expectedMajorVersion: String): Boolean {
        val version = meta?.api_version ?: return false
        return version.startsWith(expectedMajorVersion)
    }
}

@Serializable
data class ApiMeta(
    val api_version: String,
    val request_id: String,
    val server_time: String,
    val replayed: Boolean = false
)

@Serializable
data class ApiErrorData(
    val code: String,
    val message: String,
    val details: Map<String, JsonElement>? = null,
    val retry_after_seconds: Int? = null
)

@Serializable
data class SampleData(
    val user: String? = null,
    val status: String? = null
)

sealed class ApiResult<out T> {
    data class Success<out T>(val data: T, val meta: ApiMeta) : ApiResult<T>()
    data class ExpectedFailure(val error: ApiErrorData, val meta: ApiMeta?) : ApiResult<Nothing>()
    data class TransportFailure(val statusCode: Int, val message: String, val retryAfterSeconds: Int? = null) : ApiResult<Nothing>()
    data class ProtocolFailure(val message: String) : ApiResult<Nothing>()
}
