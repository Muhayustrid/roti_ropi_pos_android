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

data class MobilePosRequest internal constructor(
    val endpoint: MobilePosEndpoint,
    val query: Map<String, String>,
    val bodyBytes: ByteArray?,
    internal val bearerToken: String?,
    val idempotencyKey: String?
) {
    companion object {
        private val LOWERCASE_UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

        fun get(endpoint: MobilePosEndpoint, query: Map<String, String>): MobilePosRequest {
            require(endpoint.method == HttpMethod.GET)
            validateFields(endpoint, query.keys)
            return MobilePosRequest(endpoint, query, null, null, null)
        }

        fun <T> post(
            endpoint: MobilePosEndpoint,
            body: T,
            serializer: SerializationStrategy<T>,
            json: kotlinx.serialization.json.Json,
            idempotencyKey: String? = null
        ): MobilePosRequest {
            require(endpoint.method == HttpMethod.POST)
            val bytes = json.encodeToString(serializer, body).encodeToByteArray()
            if (endpoint.requiresIdempotency) {
                validatePostBytes(endpoint, bytes, requireNotNull(idempotencyKey), json)
            } else {
                val fields = (json.parseToJsonElement(bytes.decodeToString()) as? kotlinx.serialization.json.JsonObject)?.keys
                    ?: throw IllegalArgumentException("JSON request body must be an object")
                validateFields(endpoint, fields)
                validateIdempotency(endpoint, idempotencyKey)
            }
            return MobilePosRequest(
                endpoint = endpoint,
                query = emptyMap(),
                bodyBytes = bytes,
                bearerToken = null,
                idempotencyKey = idempotencyKey
            )
        }

        /** Reuses previously validated, persisted bytes. It never serializes a retry body. */
        fun replayPost(endpoint: MobilePosEndpoint, bodyBytes: ByteArray, idempotencyKey: String): MobilePosRequest {
            require(endpoint.method == HttpMethod.POST && endpoint.requiresIdempotency)
            require(bodyBytes.isNotEmpty())
            validateIdempotency(endpoint, idempotencyKey)
            return MobilePosRequest(endpoint, emptyMap(), bodyBytes, null, idempotencyKey)
        }

        /** Validates serialized JSON once, before it becomes durable mutation evidence. */
        internal fun validatePostBytes(
            endpoint: MobilePosEndpoint,
            bodyBytes: ByteArray,
            idempotencyKey: String,
            json: kotlinx.serialization.json.Json,
        ) {
            validatePostBodyBytes(endpoint, bodyBytes, json)
            validateIdempotency(endpoint, idempotencyKey)
        }

        internal fun validatePostBodyBytes(
            endpoint: MobilePosEndpoint,
            bodyBytes: ByteArray,
            json: kotlinx.serialization.json.Json,
        ) {
            require(endpoint.method == HttpMethod.POST && endpoint.requiresIdempotency)
            val fields = (json.parseToJsonElement(bodyBytes.decodeToString()) as? kotlinx.serialization.json.JsonObject)?.keys
                ?: throw IllegalArgumentException("JSON request body must be an object")
            validateFields(endpoint, fields)
        }

        internal fun withBearer(request: MobilePosRequest, token: String): MobilePosRequest =
            request.copy(bearerToken = token.also { require(it.isNotBlank()) })


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
    data class Success<out T>(
        val data: T,
        val meta: ApiMeta,
        /** Exact UTF-8 response bytes retained only for durable terminal persistence. */
        val rawResponse: ByteArray? = null,
    ) : ApiResult<T>()
    data class ExpectedFailure(
        val error: ApiErrorData,
        val meta: ApiMeta,
        /** Exact UTF-8 response bytes retained only for durable terminal persistence. */
        val rawResponse: ByteArray? = null,
        val retryAfter: RetryAfter? = null,
        val statusCode: Int? = null,
    ) : ApiResult<Nothing>()
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
