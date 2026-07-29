package com.rotiropi.pos_erpnext.data.api

import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MobilePosApiClientTest {

    @Serializable
    private data class ResponseData(val value: String)

    @Serializable
    private data class ScanBody(val pos_profile: String, val value: String)

    @Serializable
    private data class SaleBody(
        val pos_profile: String,
        val client_accepted_grand_total: String,
        val items: List<String>,
        val payments: List<String>
    )

    private lateinit var server: MockWebServer
    private lateinit var client: MobilePosApiClient
    private lateinit var events: MutableList<ApiTransportEvent>
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()
        events = mutableListOf()
        val origin = CanonicalBackendOrigin.parse(server.url("/").toString())
        client = MobilePosApiClient(
            origin,
            OkHttpClient.Builder()
                .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .readTimeout(100, TimeUnit.MILLISECONDS)
                .build(),
            logger = ApiTransportLogger(events::add)
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun get_builds_exact_allowlisted_request_and_parses_success() {
        server.enqueue(stableSuccess("precise-123.4500"))
        val request = MobilePosRequest.get(
            MobilePosEndpoint.CATALOG_SEARCH,
            mapOf("pos_profile" to "Outlet 01", "q" to "croissant & coffee"),
            "token-secret"
        )

        val result = client.execute(request, ResponseData.serializer())
        assertTrue(result is ApiResult.Success)
        result as ApiResult.Success
        assertEquals("precise-123.4500", result.data.value)
        assertEquals("req-123", result.meta.request_id)
        assertTrue(result.meta.replayed)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals(MobilePosEndpoint.CATALOG_SEARCH.path, recorded.requestUrl!!.encodedPath)
        assertEquals("Outlet 01", recorded.requestUrl!!.queryParameter("pos_profile"))
        assertEquals("croissant & coffee", recorded.requestUrl!!.queryParameter("q"))
        assertEquals("Bearer token-secret", recorded.getHeader("Authorization"))
        assertNull(recorded.getHeader("X-Idempotency-Key"))
    }

    @Test
    fun every_catalog_endpoint_dispatches_exact_method_and_path() {
        MobilePosEndpoint.entries.forEach { endpoint ->
            server.enqueue(stableSuccess("ok"))
            val request = when (endpoint.method) {
                HttpMethod.GET -> MobilePosRequest.get(
                    endpoint,
                    endpoint.requiredRequestFields.associateWith { sampleQueryValue(it) },
                    "token-secret"
                )
                HttpMethod.POST -> samplePostRequest(endpoint)
            }
            client.execute(request, ResponseData.serializer())
            val recorded = server.takeRequest()
            assertEquals(endpoint.method.name, recorded.method)
            assertEquals(endpoint.path, recorded.requestUrl!!.encodedPath)
            assertEquals(endpoint.requiresIdempotency, recorded.getHeader("X-Idempotency-Key") != null)
            assertEquals("Bearer token-secret", recorded.getHeader("Authorization"))
        }
        assertEquals(MobilePosEndpoint.entries.size, server.requestCount)
    }

    @Test
    fun post_sends_exact_body_and_enforces_idempotency_class() {
        server.enqueue(stableSuccess("ok"))
        val body = SaleBody("OUTLET-01", "55000.00", emptyList(), emptyList())
        val request = MobilePosRequest.post(
            MobilePosEndpoint.SALES_SUBMIT,
            body,
            SaleBody.serializer(),
            json,
            "token-secret",
            "123e4567-e89b-12d3-a456-426614174000"
        )
        client.execute(request, ResponseData.serializer())

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("123e4567-e89b-12d3-a456-426614174000", recorded.getHeader("X-Idempotency-Key"))
        assertEquals(json.encodeToString(SaleBody.serializer(), body), recorded.body.readUtf8())
    }

    @Test
    fun read_only_post_rejects_idempotency_key() {
        val body = ScanBody("OUTLET-01", "barcode")
        val failure = runCatching {
            MobilePosRequest.post(
                MobilePosEndpoint.CATALOG_SCAN,
                body,
                ScanBody.serializer(),
                json,
                "token",
                "123e4567-e89b-12d3-a456-426614174000"
            )
        }
        assertTrue(failure.isFailure)
    }

    @Test
    fun maps_stable_error_before_native_http_status() {
        server.enqueue(
            MockResponse().setResponseCode(422).setBody(
                """{"message":{"ok":false,"error":{"code":"NO_OPEN_SESSION","message":"No opening","details":{"pos_profile":"OUTLET-01"},"retryable":false},"meta":{"api_version":"v1","request_id":"req-422","server_time":"2026-07-29T12:00:00Z","replayed":false}}}"""
            )
        )
        val result = client.execute(bootstrapRequest(), ResponseData.serializer())
        assertTrue(result is ApiResult.ExpectedFailure)
        assertEquals("NO_OPEN_SESSION", (result as ApiResult.ExpectedFailure).error.code)
    }

    @Test
    fun maps_native_statuses_without_exposing_response_body() {
        val cases = listOf(
            401 to TransportFailureKind.AUTHENTICATION_REQUIRED,
            403 to TransportFailureKind.ROUTE_FORBIDDEN,
            404 to TransportFailureKind.ROUTE_NOT_FOUND,
            429 to TransportFailureKind.RATE_LIMITED,
            500 to TransportFailureKind.SERVER_UNAVAILABLE,
            503 to TransportFailureKind.SERVER_UNAVAILABLE
        )
        cases.forEach { (code, kind) ->
            server.enqueue(MockResponse().setResponseCode(code).setBody("secret server traceback"))
            val result = client.execute(bootstrapRequest(), ResponseData.serializer())
            assertTrue(result is ApiResult.TransportFailure)
            assertEquals(kind, (result as ApiResult.TransportFailure).kind)
        }
    }

    @Test
    fun preserves_valid_retry_after_delta_and_http_date() {
        listOf("30", "Wed, 21 Oct 2030 07:28:00 GMT").forEach { value ->
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", value))
            val result = client.execute(bootstrapRequest(), ResponseData.serializer()) as ApiResult.TransportFailure
            assertEquals(value, result.retryAfter?.raw)
        }
    }

    @Test
    fun rejects_redirect_and_dispatches_once() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://evil.example/"))
        val result = client.execute(bootstrapRequest(), ResponseData.serializer())
        assertTrue(result is ApiResult.ProtocolFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun timeout_and_cancellation_are_transport_failures() {
        server.enqueue(MockResponse().setBodyDelay(1, TimeUnit.SECONDS).setBody("{}"))
        val timeout = client.execute(bootstrapRequest(), ResponseData.serializer()) as ApiResult.TransportFailure
        assertEquals(TransportFailureKind.TIMEOUT, timeout.kind)

        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val cancellation = ApiCallCancellation().also { it.cancel() }
        val cancelled = client.execute(bootstrapRequest(), ResponseData.serializer(), cancellation) as ApiResult.TransportFailure
        assertEquals(TransportFailureKind.CANCELLED, cancelled.kind)
    }

    @Test
    fun logger_contains_only_allowlisted_event_data() {
        server.enqueue(stableSuccess("ok"))
        client.execute(bootstrapRequest(), ResponseData.serializer())
        assertEquals(1, events.size)
        val text = events.single().toString()
        assertFalse(text.contains("token-secret"))
        assertFalse(text.contains("Authorization"))
        assertFalse(text.contains("message"))
        assertEquals("req-123", events.single().requestId)
    }

    @Test
    fun malformed_missing_message_and_incompatible_version_are_protocol_failures() {
        listOf(
            "not-json",
            """{"invalid":true}""",
            """{"message":{"ok":true,"data":{"value":"x"},"meta":{"api_version":"v2","request_id":"x","server_time":"x"}}}"""
        ).forEach { body ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))
            assertTrue(client.execute(bootstrapRequest(), ResponseData.serializer()) is ApiResult.ProtocolFailure)
        }
    }

    private fun bootstrapRequest() = MobilePosRequest.get(MobilePosEndpoint.BOOTSTRAP_GET, emptyMap(), "token-secret")

    private fun samplePostRequest(endpoint: MobilePosEndpoint): MobilePosRequest {
        val fields = endpoint.requiredRequestFields.associateWith { field ->
            when (field) {
                "items", "payments", "opening_balances", "closing_balances" -> json.parseToJsonElement("[]")
                else -> json.parseToJsonElement("\"${sampleQueryValue(field)}\"")
            }
        }
        val serializer = kotlinx.serialization.json.JsonObject.serializer()
        val key = if (endpoint.requiresIdempotency) "123e4567-e89b-12d3-a456-426614174000" else null
        return MobilePosRequest.post(endpoint, kotlinx.serialization.json.JsonObject(fields), serializer, json, "token-secret", key)
    }

    private fun sampleQueryValue(field: String): String = when (field) {
        "start" -> "0"
        "limit" -> "20"
        "status" -> "all"
        else -> "sample"
    }

    private fun stableSuccess(value: String) = MockResponse().setResponseCode(200).setBody(
        """{"message":{"ok":true,"data":{"value":"$value"},"meta":{"api_version":"v1","request_id":"req-123","server_time":"2026-07-29T12:00:00Z","replayed":true}}}"""
    )
}
