package com.rotiropi.pos_erpnext.data

import com.rotiropi.pos_erpnext.auth.OAuthTokens
import com.rotiropi.pos_erpnext.data.api.ApiCallCancellation
import com.rotiropi.pos_erpnext.data.api.AuthTokenProvider
import com.rotiropi.pos_erpnext.data.api.AuthenticatedMobilePosApiClient
import com.rotiropi.pos_erpnext.data.api.CanonicalBackendOrigin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CatalogRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: MobilePosRepository

    @Before
    fun setUp() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        server = MockWebServer().also {
            it.useHttps(serverCertificates.sslSocketFactory(), false)
            it.start()
        }
        val origin = CanonicalBackendOrigin.parse(server.url("/").toString())
        repository = MobilePosRepository(
            AuthenticatedMobilePosApiClient(
                origin,
                object : AuthTokenProvider {
                    override fun currentAccessToken() = "token"
                    override fun refreshAccessTokenIfCurrent(observedAccessToken: String) = null
                    override fun refreshAccessToken() = null
                    override fun currentTokens() = OAuthTokens("token", null, Long.MAX_VALUE)
                },
                OkHttpClient.Builder()
                    .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                    .build(),
                Json { ignoreUnknownKeys = true },
            ),
        )
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `catalog search uses bounded GET query and maps page`() {
        server.enqueue(success(fixture("catalog-page.json")))

        val result = repository.searchCatalog("croissant", "OUTLET-01", 20, 20, ApiCallCancellation())

        val page = (result as CatalogSearchResult.Success).page
        assertEquals(listOf("CROISSANT-PACK"), page.items.map(CatalogProduct::itemCode))
        assertEquals(20, page.start)
        assertEquals(20, page.limit)
        assertTrue(page.hasMore)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/method/roti_ropi_pos.api.v1.catalog.search", request.requestUrl!!.encodedPath)
        assertEquals("croissant", request.requestUrl!!.queryParameter("q"))
        assertEquals("OUTLET-01", request.requestUrl!!.queryParameter("pos_profile"))
        assertEquals("20", request.requestUrl!!.queryParameter("start"))
        assertEquals("20", request.requestUrl!!.queryParameter("limit"))
        assertEquals("Bearer token", request.getHeader("Authorization"))
    }

    @Test
    fun `catalog scan sends exact value and preserves server identity`() {
        server.enqueue(success(fixture("catalog-scan.json")))

        val result = repository.scanCatalog("OUTLET-01", "  BATCH-QR-0001  ", ApiCallCancellation())

        val scan = (result as CatalogScanResult.Success).scan
        assertEquals("  BATCH-QR-0001  ", server.takeRequest().body.readUtf8().jsonObject().getValue("value")!!.toString().trim('"'))
        assertEquals("CROISSANT-PACK", scan.itemCode)
        assertEquals("BATCH-QR-0001", scan.batchNo)
        assertEquals("Pack", scan.uom)
        assertEquals("6", scan.conversionFactor)
        assertEquals("Outlet 01 - RR", scan.warehouse)
    }

    @Test
    fun `catalog scan preserves null conversion factor without fabricating a value`() {
        server.enqueue(
            success(
                """
                {
                  "scan": {
                    "item_code": "SCALE",
                    "barcode": "SER-1",
                    "batch_no": null,
                    "serial_no": "SER-1",
                    "uom": "Nos",
                    "conversion_factor": null,
                    "warehouse": "Outlet 01 - RR"
                  },
                  "warnings": [
                    {
                      "code": "MISSING_UOM_CONVERSION",
                      "message": "The selected UOM has no conversion factor."
                    }
                  ]
                }
                """,
            ),
        )

        val result = repository.scanCatalog("OUTLET-01", "SER-1", ApiCallCancellation())

        val scan = (result as CatalogScanResult.Success).scan
        assertEquals(null, scan.conversionFactor)
        assertEquals("MISSING_UOM_CONVERSION", result.warnings.single().code)
    }

    @Test
    fun `catalog quote sends only approved fields and maps warnings`() {
        server.enqueue(success(fixture("catalog-quote.json")))

        val request = CatalogQuoteRequest(
            posProfile = "OUTLET-01",
            customer = "WALK-IN-01",
            itemCode = "CROISSANT-PACK",
            quantity = "2.00",
            uom = "Pack",
            batchNo = "BATCH-QR-0001",
        )
        val result = repository.quoteItem(request, ApiCallCancellation())
        val quote = (result as CatalogQuoteResult.Success).quote
        assertEquals("2.00", quote.quantity)
        assertEquals("25000", quote.rate)
        assertEquals("MISSING_UOM_CONVERSION", quote.warnings.single().code)

        val body = server.takeRequest().body.readUtf8().jsonObject()
        assertEquals(
            setOf("pos_profile", "customer", "item_code", "qty", "uom", "batch_no"),
            body.keys,
        )
        assertFalse(body.keys.any { it.contains("serial", ignoreCase = true) })
        assertEquals("2.00", body.getValue("qty")!!.toString().trim('"'))
    }

    @Test
    fun `catalog native and stable errors fail closed`() {
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(
            CatalogFailure.AuthorizationDenied,
            (repository.searchCatalog("", "OUTLET", 0, 20, ApiCallCancellation()) as CatalogSearchResult.Failure).reason,
        )
        server.enqueue(stableError("PROFILE_SCOPE_MISMATCH"))
        assertEquals(
            CatalogFailure.Stable("PROFILE_SCOPE_MISMATCH"),
            (repository.scanCatalog("OUTLET", "VALUE", ApiCallCancellation()) as CatalogScanResult.Failure).reason,
        )
    }

    private fun success(data: String) = MockResponse().setBody(
        """{"message":{"ok":true,"data":$data,"meta":{"api_version":"v1","request_id":"r","server_time":"now"}}}""",
    )

    private fun stableError(code: String) = MockResponse()
        .setResponseCode(403)
        .setBody(
            """{"message":{"ok":false,"data":null,"error":{"code":"$code","message":"x","details":{},"retryable":false},"meta":{"api_version":"v1","request_id":"r","server_time":"now"}}}""",
        )

    private fun fixture(name: String) = java.io.File("src/test/resources/api/v1/$name").readText()

    private fun String.jsonObject() = Json.parseToJsonElement(this).jsonObject
}
