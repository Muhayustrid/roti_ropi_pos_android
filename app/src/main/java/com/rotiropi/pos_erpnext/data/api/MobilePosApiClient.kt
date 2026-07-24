package com.rotiropi.pos_erpnext.data.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MobilePosApiClient(
    private val baseUrl: String,
    private val client: OkHttpClient = defaultClient()
) {
    companion object {
        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }

    fun executeGet(path: String, bearerToken: String?): ApiResult<String> {
        val url = if (baseUrl.endsWith("/")) "$baseUrl${path.removePrefix("/")}" else "$baseUrl$path"
        val requestBuilder = Request.Builder()
            .url(url)
            .get()

        if (!bearerToken.isNullOrEmpty()) {
            requestBuilder.header("Authorization", "Bearer $bearerToken")
        }

        return executeRequest(requestBuilder.build())
    }

    fun executePost(
        path: String,
        bearerToken: String?,
        idempotencyKey: String? = null,
        jsonBody: String = "{}"
    ): ApiResult<String> {
        val url = if (baseUrl.endsWith("/")) "$baseUrl${path.removePrefix("/")}" else "$baseUrl$path"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBuilder = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(mediaType))
            .header("Content-Type", "application/json")

        if (!bearerToken.isNullOrEmpty()) {
            requestBuilder.header("Authorization", "Bearer $bearerToken")
        }
        if (!idempotencyKey.isNullOrEmpty()) {
            requestBuilder.header("X-Idempotency-Key", idempotencyKey)
        }

        return executeRequest(requestBuilder.build())
    }

    private fun executeRequest(request: Request): ApiResult<String> {
        return try {
            val response = client.newCall(request).execute()
            val code = response.code
            val retryAfterHeader = response.header("Retry-After")?.toIntOrNull()
            val responseBody = response.body?.string().orEmpty()

            if (code == 401 || code == 403 || code == 404 || code == 429 || code >= 500) {
                ApiResult.TransportFailure(
                    statusCode = code,
                    message = "HTTP Error $code",
                    retryAfterSeconds = retryAfterHeader
                )
            } else {
                ApiResult.Success(responseBody, ApiMeta("v1", "req-0", ""))
            }
        } catch (e: Exception) {
            ApiResult.ProtocolFailure(e.message ?: "Network transport exception")
        }
    }
}
