package com.rotiropi.pos_erpnext.data.api

import java.io.InterruptedIOException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class MobilePosApiClient(
    private val origin: CanonicalBackendOrigin,
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val logger: ApiTransportLogger = ApiTransportLogger.NONE
) {
    init {
        require(origin.isValid)
    }

    fun <T> execute(
        request: MobilePosRequest,
        deserializer: DeserializationStrategy<T>,
        cancellation: ApiCallCancellation = ApiCallCancellation()
    ): ApiResult<T> {
        val okHttpRequest = buildRequest(request)
        val call = client.newCall(okHttpRequest)
        cancellation.attach(call)

        return try {
            call.execute().use { response ->
                val body = response.body?.string().orEmpty()
                val result = parseResponse(response.code, response.header("Retry-After"), body, deserializer)
                logger.log(
                    ApiTransportEvent(
                        endpoint = request.endpoint,
                        statusCode = response.code,
                        outcome = result::class.simpleName ?: "Unknown",
                        requestId = when (result) {
                            is ApiResult.Success -> result.meta.request_id
                            is ApiResult.ExpectedFailure -> result.meta.request_id
                            else -> null
                        }
                    )
                )
                result
            }
        } catch (_: SocketTimeoutException) {
            failure(request.endpoint, TransportFailureKind.TIMEOUT)
        } catch (_: InterruptedIOException) {
            failure(
                request.endpoint,
                if (cancellation.wasCancelled() || call.isCanceled()) TransportFailureKind.CANCELLED else TransportFailureKind.TIMEOUT
            )
        } catch (_: IOException) {
            failure(
                request.endpoint,
                if (cancellation.wasCancelled() || call.isCanceled()) TransportFailureKind.CANCELLED else TransportFailureKind.NETWORK_FAILURE
            )
        }
    }

    private fun buildRequest(request: MobilePosRequest): Request {
        val url = origin.serialized.toHttpUrlBuilder()
            .addEncodedPathSegments(request.endpoint.path.removePrefix("/"))
            .apply { request.query.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()
        require(url.scheme == "https" && url.host == origin.serialized.toHttpUrlBuilder().build().host)

        val bearerToken = requireNotNull(request.bearerToken)
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $bearerToken")
            .apply {
                when (request.endpoint.method) {
                    HttpMethod.GET -> get()
                    HttpMethod.POST -> {
                        header("Content-Type", "application/json")
                        post(requireNotNull(request.bodyBytes).toRequestBody(JSON_MEDIA_TYPE))
                    }
                }
                request.idempotencyKey?.let { header("X-Idempotency-Key", it) }
            }
            .build()
    }

    private fun <T> parseResponse(
        statusCode: Int,
        retryAfter: String?,
        body: String,
        deserializer: DeserializationStrategy<T>
    ): ApiResult<T> {
        val stable = try {
            json.decodeFromString<FrappeResponse>(body)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

        if (stable != null) {
            val envelope = stable.message
            if (!envelope.isCompatibleVersion("v1")) {
                return ApiResult.ProtocolFailure("Incompatible API version")
            }
            if (envelope.ok) {
                val dataElement = envelope.data ?: return ApiResult.ProtocolFailure("Success response missing data")
                if (envelope.error != null) return ApiResult.ProtocolFailure("Success response contains error")
                if (statusCode !in 200..299) return ApiResult.ProtocolFailure("Success envelope has unexpected HTTP status")
                val data = try {
                    json.decodeFromJsonElement(deserializer, dataElement)
                } catch (_: SerializationException) {
                    return ApiResult.ProtocolFailure("Response data does not match contract")
                } catch (_: IllegalArgumentException) {
                    return ApiResult.ProtocolFailure("Response data does not match contract")
                }
                return ApiResult.Success(data, envelope.meta)
            }
            val error = envelope.error ?: return ApiResult.ProtocolFailure("Failure response missing error")
            if (envelope.data != null) return ApiResult.ProtocolFailure("Failure response contains data")
            return ApiResult.ExpectedFailure(error, envelope.meta)
        }

        return when (statusCode) {
            401 -> ApiResult.TransportFailure(TransportFailureKind.AUTHENTICATION_REQUIRED, statusCode)
            403 -> ApiResult.TransportFailure(TransportFailureKind.ROUTE_FORBIDDEN, statusCode)
            404 -> ApiResult.TransportFailure(TransportFailureKind.ROUTE_NOT_FOUND, statusCode)
            429 -> ApiResult.TransportFailure(TransportFailureKind.RATE_LIMITED, statusCode, RetryAfter.parse(retryAfter))
            500, 503 -> ApiResult.TransportFailure(TransportFailureKind.SERVER_UNAVAILABLE, statusCode, RetryAfter.parse(retryAfter))
            in 300..399 -> ApiResult.ProtocolFailure("Redirect response rejected")
            else -> ApiResult.ProtocolFailure("Malformed or unsupported response")
        }
    }

    private fun <T> failure(endpoint: MobilePosEndpoint, kind: TransportFailureKind): ApiResult<T> {
        logger.log(ApiTransportEvent(endpoint, null, kind.name, null))
        return ApiResult.TransportFailure(kind)
    }

    private fun String.toHttpUrlBuilder() = toHttpUrl().newBuilder()

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
