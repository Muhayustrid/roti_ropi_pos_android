package com.rotiropi.pos_erpnext.data

import com.rotiropi.pos_erpnext.data.api.ApiCallCancellation
import com.rotiropi.pos_erpnext.data.api.AuthTokenProvider
import com.rotiropi.pos_erpnext.data.api.AuthenticatedMobilePosApiClient
import com.rotiropi.pos_erpnext.data.api.CanonicalBackendOrigin
import com.rotiropi.pos_erpnext.auth.OAuthTokens
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.io.File

class CustomerRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: MobilePosRepository

    @Before fun setUp() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer(); server.useHttps(serverCertificates.sslSocketFactory(), false); server.start()
        val origin = CanonicalBackendOrigin.parse(server.url("/").toString())
        repository = MobilePosRepository(AuthenticatedMobilePosApiClient(origin, object : AuthTokenProvider {
            override fun currentAccessToken() = "token"
            override fun refreshAccessTokenIfCurrent(observedAccessToken: String) = null
            override fun refreshAccessToken() = null
            override fun currentTokens() = OAuthTokens("token", null, Long.MAX_VALUE)
        }, OkHttpClient.Builder().sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager).build(), Json { ignoreUnknownKeys = true }))
    }

    @After fun tearDown() { runCatching { server.shutdown() } }

    @Test fun `customer search uses exact GET query contract`() {
        server.enqueue(success("""{"customers":[{"name":"CUST-1","customer_name":"Ayu Bakery","mobile_no":null,"is_default_walk_in":false}],"page":{"start":0,"limit":20,"has_more":false}}"""))
        repository.searchCustomers("Ayu & Co", "OUTLET-01", 0, 20, ApiCallCancellation())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/method/roti_ropi_pos.api.v1.customers.search", request.requestUrl!!.encodedPath)
        assertEquals("Ayu & Co", request.requestUrl!!.queryParameter("q"))
        assertEquals("OUTLET-01", request.requestUrl!!.queryParameter("pos_profile"))
        assertEquals("0", request.requestUrl!!.queryParameter("start"))
        assertEquals("20", request.requestUrl!!.queryParameter("limit"))
        assertEquals(null, request.requestUrl!!.queryParameter("query"))
        assertEquals(null, request.requestUrl!!.queryParameter("page"))
        assertEquals(null, request.requestUrl!!.queryParameter("page_length"))
        assertEquals("Bearer token", request.getHeader("Authorization"))
    }

    @Test fun `customer page maps ordering metadata and additive fields`() {
        server.enqueue(success("""{"customers":[{"name":"B","customer_name":"B","mobile_no":"1","is_default_walk_in":false,"future":true},{"name":"A","customer_name":"A","mobile_no":null,"is_default_walk_in":true}],"page":{"start":7,"limit":13,"has_more":true},"future":true}"""))
        val result = repository.searchCustomers("", "OUTLET", 0, 20, ApiCallCancellation()) as CustomerSearchResult.Success
        assertEquals(listOf("B", "A"), result.page.customers.map(Customer::id))
        assertEquals(7, result.page.start); assertEquals(13, result.page.limit); assertTrue(result.page.hasMore)
        assertEquals(null, result.page.customers.last().mobile); assertTrue(result.page.customers.last().isDefaultWalkIn)
    }

    @Test fun `reviewed customer fixture maps through production DTO boundary`() {
        server.enqueue(success(fixture("customer-page.json")))

        val result = repository.searchCustomers("", "OUTLET", 0, 20, ApiCallCancellation()) as CustomerSearchResult.Success

        assertEquals(listOf("CUST-0001", "WALK-IN-01"), result.page.customers.map(Customer::id))
        assertEquals(0, result.page.start)
        assertEquals(20, result.page.limit)
        assertTrue(result.page.hasMore)
    }

    @Test fun `native and stable errors are mapped without fallback`() {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(CustomerSearchFailure.AuthenticationRequired, (repository.searchCustomers("", "OUTLET", 0, 20, ApiCallCancellation()) as CustomerSearchResult.Failure).reason)
        server.enqueue(stableError("PERMISSION_DENIED"))
        assertEquals(CustomerSearchFailure.Stable("PERMISSION_DENIED"), (repository.searchCustomers("", "OUTLET", 0, 20, ApiCallCancellation()) as CustomerSearchResult.Failure).reason)
    }

    @Test fun `empty malformed and profile errors fail closed`() {
        server.enqueue(success("""{"customers":[],"page":{"start":0,"limit":20,"has_more":false}}"""))
        val empty = repository.searchCustomers("", "OUTLET", 0, 20, ApiCallCancellation()) as CustomerSearchResult.Success
        assertTrue(empty.page.customers.isEmpty())
        server.enqueue(MockResponse().setBody("not-json"))
        assertTrue((repository.searchCustomers("", "OUTLET", 0, 20, ApiCallCancellation()) as CustomerSearchResult.Failure).reason is CustomerSearchFailure.Protocol)
        server.enqueue(stableError("PROFILE_SCOPE_MISMATCH"))
        assertEquals(CustomerSearchFailure.Stable("PROFILE_SCOPE_MISMATCH"), (repository.searchCustomers("", "OUTLET", 0, 20, ApiCallCancellation()) as CustomerSearchResult.Failure).reason)
    }

    @Test fun `active HTTP call is cancelled and returns unavailable`() {
        server.enqueue(success("""{"customers":[],"page":{"start":0,"limit":20,"has_more":false}}""").setBodyDelay(10, TimeUnit.SECONDS))
        val cancellation = ApiCallCancellation()
        var result: CustomerSearchResult? = null
        val thread = Thread { result = repository.searchCustomers("", "OUTLET", 0, 20, cancellation) }
        thread.start()
        server.takeRequest(2, TimeUnit.SECONDS)
        cancellation.cancel()
        thread.join(2_000)
        assertFalse(thread.isAlive)
        assertEquals(CustomerSearchFailure.Unavailable, (result as CustomerSearchResult.Failure).reason)
    }

    @Test fun `missing DTO native forbidden and invalid request map safely`() {
        server.enqueue(success("""{"customers":[{"name":"C","mobile_no":null,"is_default_walk_in":false}],"page":{"start":0,"limit":20,"has_more":false}}"""))
        assertTrue((repository.searchCustomers("", "OUTLET", 0, 20, ApiCallCancellation()) as CustomerSearchResult.Failure).reason is CustomerSearchFailure.Protocol)
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(CustomerSearchFailure.AuthorizationDenied, (repository.searchCustomers("", "OUTLET", 0, 20, ApiCallCancellation()) as CustomerSearchResult.Failure).reason)
        server.enqueue(stableError("INVALID_REQUEST"))
        assertEquals(CustomerSearchFailure.Stable("INVALID_REQUEST"), (repository.searchCustomers("", "OUTLET", 0, 20, ApiCallCancellation()) as CustomerSearchResult.Failure).reason)
    }

    private fun success(data: String) = MockResponse().setBody("""{"message":{"ok":true,"data":$data,"meta":{"api_version":"v1","request_id":"r","server_time":"now"}}}""")
    private fun stableError(code: String) = MockResponse().setResponseCode(403).setBody("""{"message":{"ok":false,"data":null,"error":{"code":"$code","message":"x","details":{},"retryable":false},"meta":{"api_version":"v1","request_id":"r","server_time":"now"}}}""")
    private fun fixture(name: String) = File("src/test/resources/api/v1/$name").readText()
}
