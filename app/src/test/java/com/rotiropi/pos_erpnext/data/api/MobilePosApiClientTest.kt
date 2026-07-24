package com.rotiropi.pos_erpnext.data.api

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MobilePosApiClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiClient: MobilePosApiClient

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val originUrl = mockWebServer.url("").toString().removeSuffix("/")
        val origin = CanonicalBackendOrigin.parse(originUrl.replace("http://", "https://"))
        apiClient = MobilePosApiClient(mockWebServer.url("/").toString().removeSuffix("/"))
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun maps_native_401_to_auth_required() {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("""{"exception":"AuthenticationError"}"""))
        val result = apiClient.executeGet("/api/method/roti_ropi_pos.api.v1.bootstrap.get", "token123")
        assertTrue(result is ApiResult.TransportFailure)
        assertEquals(401, (result as ApiResult.TransportFailure).statusCode)
    }

    @Test
    fun maps_native_429_and_preserves_retry_after() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "30")
                .setBody("""{"exception":"Rate Limit Exceeded"}""")
        )
        val result = apiClient.executeGet("/api/method/roti_ropi_pos.api.v1.catalog.search", "token123")
        assertTrue(result is ApiResult.TransportFailure)
        val failure = result as ApiResult.TransportFailure
        assertEquals(429, failure.statusCode)
        assertEquals(30, failure.retryAfterSeconds)
    }

    @Test
    fun attaches_idempotency_key_header_on_mutation() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"message":{"ok":true,"data":{}}}"""))
        apiClient.executePost(
            path = "/api/method/roti_ropi_pos.api.v1.sales.submit",
            bearerToken = "token123",
            idempotencyKey = "uuid-1234-5678",
            jsonBody = "{}"
        )
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("uuid-1234-5678", recordedRequest.getHeader("X-Idempotency-Key"))
        assertEquals("Bearer token123", recordedRequest.getHeader("Authorization"))
    }
}
