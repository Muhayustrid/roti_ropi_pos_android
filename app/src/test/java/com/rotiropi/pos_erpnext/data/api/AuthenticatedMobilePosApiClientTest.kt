package com.rotiropi.pos_erpnext.data.api

import com.rotiropi.pos_erpnext.auth.OAuthTokens
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Authenticated transport requirements for Task 3:
 * - Bearer attached only for the configured canonical origin.
 * - Redirects never followed; a 3xx is a protocol failure.
 * - An eligible read refreshes once and retries once.
 * - Concurrent read 401s trigger exactly one serialized refresh.
 * - A mutation 401 returns a typed authentication-required result with no replay.
 * - Exchange/refresh only at the fixed Frappe token path on the canonical origin.
 */
class AuthenticatedMobilePosApiClientTest {

    @Serializable
    private data class ResponseData(val value: String)

    @Serializable
    private data class SaleBody(
        val pos_profile: String,
        val client_accepted_grand_total: String,
        val items: List<String>,
        val payments: List<String>
    )

    private lateinit var server: MockWebServer
    private lateinit var okHttp: OkHttpClient
    private lateinit var origin: CanonicalBackendOrigin
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        val certificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()

        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()

        okHttp = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()

        origin = CanonicalBackendOrigin.parse(server.url("/").toString())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun attaches_bearer_from_token_provider_for_canonical_origin() {
        server.enqueue(stableSuccess("ok"))
        val provider = FakeTokenProvider(accessToken = "access-1")
        val client = AuthenticatedMobilePosApiClient(origin, provider, okHttp, json)

        val result = client.execute(readRequest(), ResponseData.serializer())

        assertTrue(result is ApiResult.Success)
        val recorded = server.takeRequest()
        assertEquals("Bearer access-1", recorded.getHeader("Authorization"))
        assertEquals(MobilePosEndpoint.CATALOG_SEARCH.path, recorded.requestUrl!!.encodedPath)
    }

    @Test
    fun mismatched_identity_token_never_reaches_authorization_header_or_refresh() {
        val provider = IdentityBoundTokenProvider(
            OAuthTokens(
                "wrong-origin-token",
                "wrong-refresh",
                Long.MAX_VALUE,
                canonicalOrigin = "https://evil.example.com",
                clientId = "client"
            ),
            origin.serialized,
            "client"
        )
        val client = AuthenticatedMobilePosApiClient(origin, provider, okHttp, json)

        val result = client.execute(readRequest(), ResponseData.serializer())

        assertEquals(
            TransportFailureKind.AUTHENTICATION_REQUIRED,
            (result as ApiResult.TransportFailure).kind
        )
        assertEquals(0, server.requestCount)
        assertEquals(0, provider.refreshCount.get())
        assertNull(provider.stored)
    }

    @Test
    fun missing_access_token_returns_authentication_required_without_network() {
        val provider = FakeTokenProvider(accessToken = null)
        val client = AuthenticatedMobilePosApiClient(origin, provider, okHttp, json)

        val result = client.execute(readRequest(), ResponseData.serializer())

        assertEquals(
            TransportFailureKind.AUTHENTICATION_REQUIRED,
            (result as ApiResult.TransportFailure).kind
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun redirect_response_rejected_and_not_followed() {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "https://evil.example.com/api/method/x")
        )
        val provider = FakeTokenProvider(accessToken = "access-1")
        val client = AuthenticatedMobilePosApiClient(origin, provider, okHttp, json)

        val result = client.execute(readRequest(), ResponseData.serializer())

        assertTrue(result is ApiResult.ProtocolFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun eligible_read_refreshes_once_and_retries_once() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(stableSuccess("after-refresh"))
        val provider = FakeTokenProvider(accessToken = "expired", refreshedToken = "fresh")
        val client = AuthenticatedMobilePosApiClient(origin, provider, okHttp, json)

        val result = client.execute(readRequest(), ResponseData.serializer())

        assertTrue(result is ApiResult.Success)
        assertEquals(1, provider.refreshCount.get())
        assertEquals(2, server.requestCount)
        server.takeRequest()
        assertEquals("Bearer fresh", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `approved read-only POST endpoints refresh once and retry once`() {
        listOf(
            readOnlyPostRequest(MobilePosEndpoint.CATALOG_SCAN),
            readOnlyPostRequest(MobilePosEndpoint.CATALOG_QUOTE_ITEM)
        ).forEach { request ->
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(stableSuccess("after-refresh"))
            val provider = FakeTokenProvider(accessToken = "expired", refreshedToken = "fresh")
            val client = AuthenticatedMobilePosApiClient(origin, provider, okHttp, json)

            val result = client.execute(request, ResponseData.serializer())

            assertTrue(result is ApiResult.Success)
            assertEquals(1, provider.refreshCount.get())
            assertEquals("POST", server.takeRequest().method)
            assertEquals("Bearer fresh", server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test
    fun `successful read-only POST does not refresh`() {
        server.enqueue(stableSuccess("scan"))
        val provider = FakeTokenProvider(accessToken = "access")
        val client = AuthenticatedMobilePosApiClient(origin, provider, okHttp, json)

        val result = client.execute(readOnlyPostRequest(MobilePosEndpoint.CATALOG_SCAN), ResponseData.serializer())

        assertTrue(result is ApiResult.Success)
        assertEquals(0, provider.refreshCount.get())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `concurrent read-only POST 401s share one refresh`() {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(401)) }
        repeat(2) { server.enqueue(stableSuccess("after-refresh")) }
        val provider = FakeTokenProvider(accessToken = "expired", refreshedToken = "fresh")
        val client = AuthenticatedMobilePosApiClient(origin, provider, okHttp, json)

        val calls = List(2) {
            Thread { client.execute(readOnlyPostRequest(MobilePosEndpoint.CATALOG_SCAN), ResponseData.serializer()) }
        }
        calls.forEach(Thread::start)
        calls.forEach { it.join(5_000) }

        assertEquals(1, provider.refreshCount.get())
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `failed read-only POST refresh returns typed authentication required`() {
        server.enqueue(MockResponse().setResponseCode(401))
        val provider = FakeTokenProvider(accessToken = "expired", refreshedToken = null)
        val client = AuthenticatedMobilePosApiClient(origin, provider, okHttp, json)

        val result = client.execute(readOnlyPostRequest(MobilePosEndpoint.CATALOG_QUOTE_ITEM), ResponseData.serializer())

        assertEquals(
            TransportFailureKind.AUTHENTICATION_REQUIRED,
            (result as ApiResult.TransportFailure).kind
        )
        assertEquals(1, provider.refreshCount.get())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun read_retries_at_most_once_on_repeated_401() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        val provider = FakeTokenProvider(accessToken = "expired", refreshedToken = "fresh")
        val client = AuthenticatedMobilePosApiClient(origin, provider, okHttp, json)

        val result = client.execute(readRequest(), ResponseData.serializer())

        assertEquals(
            TransportFailureKind.AUTHENTICATION_REQUIRED,
            (result as ApiResult.TransportFailure).kind
        )
        assertEquals(1, provider.refreshCount.get())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun concurrent_read_401s_trigger_exactly_one_refresh() {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(401)) }
        repeat(2) { server.enqueue(stableSuccess("ok")) }
        val provider = FakeTokenProvider(accessToken = "expired", refreshedToken = "fresh")
        val client = AuthenticatedMobilePosApiClient(origin, provider, okHttp, json)

        val threads = (1..2).map {
            Thread { client.execute(readRequest(), ResponseData.serializer()) }
        }
        threads.forEach(Thread::start)
        threads.forEach { it.join(5_000) }

        assertEquals(1, provider.refreshCount.get())
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `all approved mutations return typed auth required without refresh or replay`() {
        listOf(
            MobilePosEndpoint.SESSIONS_OPEN,
            MobilePosEndpoint.SALES_SUBMIT,
            MobilePosEndpoint.SALES_CREATE_RETURN,
            MobilePosEndpoint.CLOSING_SUBMIT
        ).forEach { endpoint ->
            server.enqueue(MockResponse().setResponseCode(401))
            val provider = FakeTokenProvider(accessToken = "expired", refreshedToken = "fresh")
            val client = AuthenticatedMobilePosApiClient(origin, provider, okHttp, json)

            val result = client.execute(mutationRequest(endpoint), ResponseData.serializer())

            assertEquals(
                TransportFailureKind.AUTHENTICATION_REQUIRED,
                (result as ApiResult.TransportFailure).kind
            )
            assertEquals(0, provider.refreshCount.get())
        }
        assertEquals(4, server.requestCount)
    }

    @Test
    fun token_endpoint_accepts_only_fixed_frappe_path_on_canonical_origin() {
        val canonical = origin.serialized
        assertEquals(
            "$canonical/api/method/frappe.integrations.oauth2.get_token",
            AuthenticatedMobilePosApiClient.tokenEndpointFor(origin)
        )

        listOf(
            "https://evil.example.com/api/method/frappe.integrations.oauth2.get_token",
            "$canonical/api/method/frappe.integrations.oauth2.get_token/extra",
            "$canonical/api/method/frappe.integrations.oauth2.authorize",
            "http://insecure.example.com/api/method/frappe.integrations.oauth2.get_token"
        ).forEach { candidate ->
            assertTrue(
                "expected rejection for $candidate",
                !AuthenticatedMobilePosApiClient.isAllowedTokenEndpoint(origin, candidate)
            )
        }

        assertTrue(
            AuthenticatedMobilePosApiClient.isAllowedTokenEndpoint(
                origin,
                AuthenticatedMobilePosApiClient.tokenEndpointFor(origin)
            )
        )
    }

    @Test
    fun authorization_endpoint_accepts_only_fixed_path_with_scope_all() {
        val canonical = origin.serialized
        assertEquals(
            "$canonical/api/method/frappe.integrations.oauth2.authorize",
            AuthenticatedMobilePosApiClient.authorizationEndpointFor(origin)
        )

        listOf(
            "$canonical/.well-known/openid-configuration",
            "$canonical/api/method/frappe.integrations.oauth2.authorize/nested",
            "https://evil.example.com/api/method/frappe.integrations.oauth2.authorize"
        ).forEach { candidate ->
            assertTrue(
                "expected rejection for $candidate",
                !AuthenticatedMobilePosApiClient.isAllowedAuthorizationEndpoint(origin, candidate)
            )
        }

        assertTrue(AuthenticatedMobilePosApiClient.isAllowedScope("all"))
        assertTrue(!AuthenticatedMobilePosApiClient.isAllowedScope("openid"))
    }

    private fun readRequest() = MobilePosRequest.get(
        MobilePosEndpoint.CATALOG_SEARCH,
        mapOf("pos_profile" to "Outlet 01")
    )

    private fun readOnlyPostRequest(endpoint: MobilePosEndpoint): MobilePosRequest = when (endpoint) {
        MobilePosEndpoint.CATALOG_SCAN -> MobilePosRequest.post(
            endpoint,
            ScanBody("Outlet 01", "barcode"),
            ScanBody.serializer(),
            json
        )
        MobilePosEndpoint.CATALOG_QUOTE_ITEM -> MobilePosRequest.post(
            endpoint,
            QuoteBody("Outlet 01", "ITEM-01", "1"),
            QuoteBody.serializer(),
            json
        )
        else -> error("Read-only POST endpoint required")
    }

    private fun mutationRequest(endpoint: MobilePosEndpoint): MobilePosRequest = when (endpoint) {
        MobilePosEndpoint.SESSIONS_OPEN -> MobilePosRequest.post(
            endpoint,
            OpenSessionBody("OUTLET-01", emptyList()),
            OpenSessionBody.serializer(),
            json,
            "123e4567-e89b-12d3-a456-426614174000"
        )
        MobilePosEndpoint.SALES_SUBMIT -> MobilePosRequest.post(
            endpoint,
            SaleBody("OUTLET-01", "55000.00", emptyList(), emptyList()),
            SaleBody.serializer(),
            json,
            "123e4567-e89b-12d3-a456-426614174000"
        )
        MobilePosEndpoint.SALES_CREATE_RETURN -> MobilePosRequest.post(
            endpoint,
            ReturnBody("SALE-01", emptyList(), emptyList(), "reason"),
            ReturnBody.serializer(),
            json,
            "123e4567-e89b-12d3-a456-426614174000"
        )
        MobilePosEndpoint.CLOSING_SUBMIT -> MobilePosRequest.post(
            endpoint,
            ClosingBody("OUTLET-01", emptyList()),
            ClosingBody.serializer(),
            json,
            "123e4567-e89b-12d3-a456-426614174000"
        )
        else -> error("Mutation endpoint required")
    }

    @Serializable
    private data class ScanBody(val pos_profile: String, val value: String)

    @Serializable
    private data class QuoteBody(val pos_profile: String, val item_code: String, val qty: String)

    @Serializable
    private data class OpenSessionBody(val pos_profile: String, val opening_balances: List<String>)

    @Serializable
    private data class ReturnBody(
        val source_name: String,
        val items: List<String>,
        val payments: List<String>,
        val reason: String
    )

    @Serializable
    private data class ClosingBody(val pos_profile: String, val closing_balances: List<String>)

    private fun stableSuccess(value: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """{"message":{"ok":true,"data":{"value":"$value"},"meta":{"api_version":"v1","request_id":"req-1","server_time":"2026-07-31T00:00:00Z"}}}"""
        )

    private class IdentityBoundTokenProvider(
        var stored: OAuthTokens?,
        private val origin: String,
        private val clientId: String
    ) : AuthTokenProvider {
        val refreshCount = AtomicInteger()

        private fun valid(): OAuthTokens? {
            val current = stored ?: return null
            if (current.canonicalOrigin != origin || current.clientId != clientId) {
                stored = null
                return null
            }
            return current
        }

        override fun currentAccessToken(): String? = valid()?.accessToken

        override fun refreshAccessToken(): String? {
            refreshCount.incrementAndGet()
            return valid()?.accessToken
        }

        override fun currentTokens(): OAuthTokens? = valid()
    }

    private class FakeTokenProvider(
        private var accessToken: String?,
        private val refreshedToken: String? = null
    ) : AuthTokenProvider {
        val refreshCount = AtomicInteger(0)

        override fun currentAccessToken(): String? = accessToken

        override fun refreshAccessToken(): String? {
            refreshCount.incrementAndGet()
            accessToken = refreshedToken
            return refreshedToken
        }

        @Synchronized
        override fun refreshAccessTokenIfCurrent(observedAccessToken: String): String? {
            if (accessToken != observedAccessToken) return accessToken
            return refreshAccessToken()
        }

        override fun currentTokens(): OAuthTokens? = accessToken?.let {
            OAuthTokens(it, refreshedToken, Long.MAX_VALUE)
        }
    }

    private companion object {
        const val PLACEHOLDER_TOKEN = "placeholder"
    }
}
