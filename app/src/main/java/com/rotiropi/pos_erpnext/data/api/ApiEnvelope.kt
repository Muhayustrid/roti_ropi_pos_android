package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonElement

@Serializable
data class FrappeResponse(val message: ApiEnvelope)

@Serializable
data class ApiEnvelope(
    val ok: Boolean,
    val data: JsonElement? = null,
    val meta: ApiMeta,
    val error: ApiErrorData? = null
) {
    fun isCompatibleVersion(expectedMajorVersion: String): Boolean = meta.api_version == expectedMajorVersion
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
    val details: Map<String, JsonElement>,
    val retryable: Boolean
)

data class MobilePosRequest(
    val endpoint: MobilePosEndpoint,
    val query: Map<String, String>,
    val bodyBytes: ByteArray?,
    val bearerToken: String,
    val idempotencyKey: String?
) {
    companion object {
        private val LOWERCASE_UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

        fun get(endpoint: MobilePosEndpoint, query: Map<String, String>, bearerToken: String): MobilePosRequest {
            require(endpoint.method == HttpMethod.GET)
            validateFields(endpoint, query.keys)
            return MobilePosRequest(endpoint, query, null, requireToken(bearerToken), null)
        }

        fun <T> post(
            endpoint: MobilePosEndpoint,
            body: T,
            serializer: SerializationStrategy<T>,
            json: kotlinx.serialization.json.Json,
            bearerToken: String,
            idempotencyKey: String? = null
        ): MobilePosRequest {
            require(endpoint.method == HttpMethod.POST)
            val element = json.encodeToJsonElement(serializer, body)
            val fields = (element as? kotlinx.serialization.json.JsonObject)?.keys
                ?: throw IllegalArgumentException("JSON request body must be an object")
            validateFields(endpoint, fields)
            validateIdempotency(endpoint, idempotencyKey)
            return MobilePosRequest(
                endpoint = endpoint,
                query = emptyMap(),
                bodyBytes = json.encodeToString(serializer, body).encodeToByteArray(),
                bearerToken = requireToken(bearerToken),
                idempotencyKey = idempotencyKey
            )
        }

        private fun validateFields(endpoint: MobilePosEndpoint, fields: Set<String>) {
            require(fields.containsAll(endpoint.requiredRequestFields))
            require(fields.all { it in endpoint.requiredRequestFields || it in endpoint.optionalRequestFields })
        }

        private fun validateIdempotency(endpoint: MobilePosEndpoint, key: String?) {
            if (endpoint.requiresIdempotency) {
                require(key != null && LOWERCASE_UUID.matches(key))
            } else {
                require(key == null)
            }
        }

        private fun requireToken(token: String): String = token.also { require(it.isNotBlank()) }
    }
}

enum class TransportFailureKind {
    AUTHENTICATION_REQUIRED,
    ROUTE_FORBIDDEN,
    ROUTE_NOT_FOUND,
    RATE_LIMITED,
    SERVER_UNAVAILABLE,
    NETWORK_FAILURE,
    TIMEOUT,
    CANCELLED
}

@JvmInline
value class RetryAfter private constructor(val raw: String) {
    companion object {
        fun parse(value: String?): RetryAfter? {
            val candidate = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (candidate.all(Char::isDigit)) return RetryAfter(candidate)
            return if (HTTP_DATE.matches(candidate)) RetryAfter(candidate) else null
        }

        private val HTTP_DATE = Regex("^[A-Z][a-z]{2}, [0-9]{2} [A-Z][a-z]{2} [0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2} GMT$")
    }
}

sealed class ApiResult<out T> {
    data class Success<out T>(val data: T, val meta: ApiMeta) : ApiResult<T>()
    data class ExpectedFailure(val error: ApiErrorData, val meta: ApiMeta) : ApiResult<Nothing>()
    data class TransportFailure(
        val kind: TransportFailureKind,
        val statusCode: Int? = null,
        val retryAfter: RetryAfter? = null
    ) : ApiResult<Nothing>()
    data class ProtocolFailure(val reason: String) : ApiResult<Nothing>()
}

class ApiCallCancellation {
    @Volatile private var call: okhttp3.Call? = null
    @Volatile private var cancelled = false

    fun cancel() {
        cancelled = true
        call?.cancel()
    }

    internal fun attach(call: okhttp3.Call) {
        this.call = call
        if (cancelled) call.cancel()
    }

    internal fun wasCancelled(): Boolean = cancelled
}

data class ApiExecution<T>(
    val request: MobilePosRequest,
    val deserializer: DeserializationStrategy<T>
)
