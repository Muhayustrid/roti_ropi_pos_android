package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

/**
 * Authenticated wrapper over [MobilePosApiClient].
 *
 * - Bearer credentials are attached only for the configured canonical origin.
 * - Redirects stay disabled; a 3xx is surfaced as a protocol failure by the
 *   underlying client and never followed.
 * - An eligible read (retry class ONE_READ_RETRY) may refresh once and retry once.
 * - Concurrent read 401s collapse into a single serialized refresh.
 * - A mutation 401 returns a typed authentication-required result with no second
 *   network dispatch and no refresh, so Task 5 can map it durably to `auth_required`.
 * - Authorization-code exchange and refresh are permitted only at the fixed Frappe
 *   token path on the canonical origin.
 */
class AuthenticatedMobilePosApiClient(
    private val origin: CanonicalBackendOrigin,
    internal val tokenProvider: AuthTokenProvider,
    okHttpClient: OkHttpClient,
    json: Json = Json { ignoreUnknownKeys = true },
    logger: ApiTransportLogger = ApiTransportLogger.NONE
) {
    init {
        require(origin.isValid)
    }

    private val delegate = MobilePosApiClient(
        origin,
        okHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build(),
        json,
        logger
    )

    fun <T> execute(
        request: MobilePosRequest,
        deserializer: DeserializationStrategy<T>,
        cancellation: ApiCallCancellation = ApiCallCancellation()
    ): ApiResult<T> {
        val accessToken = tokenProvider.currentAccessToken()
            ?: return ApiResult.TransportFailure(TransportFailureKind.AUTHENTICATION_REQUIRED)

        val first = delegate.execute(MobilePosRequest.withBearer(request, accessToken), deserializer, cancellation)
        if (!first.isAuthenticationRequired()) return first

        // Mutations never replay automatically.
        if (!request.endpoint.isEligibleRead()) return first

        val refreshed = tokenProvider.refreshAccessTokenIfCurrent(accessToken)
            ?: return ApiResult.TransportFailure(TransportFailureKind.AUTHENTICATION_REQUIRED)

        return delegate.execute(MobilePosRequest.withBearer(request, refreshed), deserializer, cancellation)
    }

    private fun ApiResult<*>.isAuthenticationRequired(): Boolean =
        this is ApiResult.TransportFailure &&
            kind == TransportFailureKind.AUTHENTICATION_REQUIRED

    private fun MobilePosEndpoint.isEligibleRead(): Boolean =
        retryClass == RetryClass.ONE_READ_RETRY && !isMutation

    companion object {
        const val TOKEN_PATH = "/api/method/frappe.integrations.oauth2.get_token"
        const val AUTHORIZE_PATH = "/api/method/frappe.integrations.oauth2.authorize"
        const val REQUIRED_SCOPE = "all"

        fun tokenEndpointFor(origin: CanonicalBackendOrigin): String =
            origin.serialized.trimEnd('/') + TOKEN_PATH

        fun authorizationEndpointFor(origin: CanonicalBackendOrigin): String =
            origin.serialized.trimEnd('/') + AUTHORIZE_PATH

        fun isAllowedTokenEndpoint(origin: CanonicalBackendOrigin, candidate: String): Boolean =
            matchesFixedEndpoint(origin, candidate, TOKEN_PATH)

        fun isAllowedAuthorizationEndpoint(origin: CanonicalBackendOrigin, candidate: String): Boolean =
            matchesFixedEndpoint(origin, candidate, AUTHORIZE_PATH)

        fun isAllowedScope(scope: String): Boolean = scope == REQUIRED_SCOPE

        private fun matchesFixedEndpoint(
            origin: CanonicalBackendOrigin,
            candidate: String,
            fixedPath: String
        ): Boolean {
            if (!origin.isValid) return false
            val expected = origin.serialized.toHttpUrlOrNull() ?: return false
            val actual = candidate.toHttpUrlOrNull() ?: return false
            if (actual.scheme != "https") return false
            if (actual.host != expected.host) return false
            if (actual.port != expected.port) return false
            if (actual.encodedPath != fixedPath) return false
            if (actual.querySize != 0 || actual.fragment != null) return false
            if (actual.username.isNotEmpty() || actual.password.isNotEmpty()) return false
            return true
        }
    }
}
