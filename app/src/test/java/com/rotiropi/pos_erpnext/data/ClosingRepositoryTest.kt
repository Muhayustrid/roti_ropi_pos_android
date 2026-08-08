package com.rotiropi.pos_erpnext.data

import com.rotiropi.pos_erpnext.auth.OAuthTokens
import com.rotiropi.pos_erpnext.data.api.AuthTokenProvider
import com.rotiropi.pos_erpnext.data.api.AuthenticatedMobilePosApiClient
import com.rotiropi.pos_erpnext.data.api.CanonicalBackendOrigin
import com.rotiropi.pos_erpnext.data.api.ClosingBalanceInputDto
import com.rotiropi.pos_erpnext.data.api.ClosingStatus
import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import com.rotiropi.pos_erpnext.data.api.SubmitClosingRequestDto
import com.rotiropi.pos_erpnext.data.api.SubmitClosingResponseDto
import com.rotiropi.pos_erpnext.recovery.RecoveryExecution
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClosingRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: MobilePosRepository

    @Before
    fun setUp() {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory(), false)
            start()
        }
        repository = MobilePosRepository(
            AuthenticatedMobilePosApiClient(
                CanonicalBackendOrigin.parse(server.url("/").toString()),
                StaticTokenProvider,
                OkHttpClient.Builder()
                    .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                    .retryOnConnectionFailure(false)
                    .readTimeout(2, TimeUnit.SECONDS)
                    .build(),
                Json { ignoreUnknownKeys = true },
            ),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `preview sends profile and maps server-owned fields`() {
        server.enqueue(success(previewJson()))

        val result = repository.previewClosing("OUTLET-01")

        assertTrue(result is ClosingReadResult.Success)
        val preview = (result as ClosingReadResult.Success).data
        assertEquals("preview-1", preview.previewId)
        assertEquals("OPENING-1", preview.binding.openingEntry)
        assertEquals(listOf("Cash"), preview.binding.paymentModes)
        assertEquals("70000.00", preview.expectedPayments.single().expectedAmount)
        assertEquals(2, preview.countedAmountPolicy.maxScale)
        val request = server.takeRequest()
        assertEquals(MobilePosEndpoint.CLOSING_PREVIEW.path, request.requestUrl!!.encodedPath)
        assertEquals("OUTLET-01", request.requestUrl!!.queryParameter("pos_profile"))
    }

    @Test
    fun `status reads complete authoritative receipt by closing name`() {
        server.enqueue(success(receiptJson("submitted")))

        val result = repository.closingStatus("CLOSING-1")

        assertTrue(result is ClosingReadResult.Success)
        val receipt = (result as ClosingReadResult.Success).data
        assertEquals(ClosingStatus.SUBMITTED, receipt.status)
        assertEquals("-1000.00", receipt.reconciliation.differenceTotal)
        val request = server.takeRequest()
        assertEquals(MobilePosEndpoint.CLOSING_STATUS.path, request.requestUrl!!.encodedPath)
        assertEquals("CLOSING-1", request.requestUrl!!.queryParameter("name"))
    }

    @Test
    fun `closing submit uses generic durable recovery boundary`() {
        var captured: SubmitClosingRequestDto? = null
        val recoveryRepository = MobilePosRepository(
            client = repositoryClient(),
            submitClosing = { request ->
                captured = request
                RecoveryExecution.Completed("123e4567-e89b-42d3-a456-426614174000")
            },
        )
        val request = SubmitClosingRequestDto(
            "OUTLET-01",
            "preview-1",
            listOf(ClosingBalanceInputDto("Cash", "69000.00")),
        )

        val result = recoveryRepository.submitClosing(request)

        assertEquals(request, captured)
        assertEquals(RecoveryExecution.Completed("123e4567-e89b-42d3-a456-426614174000"), result)
    }

    @Test
    fun `production closing recovery spec uses closing submit serializers`() {
        val request = SubmitClosingRequestDto("OUTLET-01", "preview-1", emptyList())

        val spec = closingRecoverySpec(request, Json)

        assertEquals(MobilePosEndpoint.CLOSING_SUBMIT, spec.endpoint)
        assertEquals(request, spec.body)
        assertEquals(SubmitClosingRequestDto.serializer(), spec.bodySerializer)
        assertEquals(SubmitClosingResponseDto.serializer(), spec.responseDeserializer)
    }

    private fun repositoryClient() = AuthenticatedMobilePosApiClient(
        CanonicalBackendOrigin.parse("https://example.test"),
        StaticTokenProvider,
        OkHttpClient(),
    )

    private fun success(data: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"message":{"ok":true,"data":$data,"meta":{"api_version":"v1","request_id":"request","server_time":"2026-08-07T00:00:00+00:00","replayed":false}}}""")

    private fun previewJson() = """
        {
          "opening_session":{"name":"OPENING-1","pos_profile":"OUTLET-01","company":"Roti Ropi","user":"cashier@example.com","status":"open","lifecycle_state":"active","closing":null,"posting_date":"2026-08-07","period_start_date":"2026-08-07T08:00:00+00:00","opening_balances":[],"warnings":[]},
          "preview_id":"preview-1","preview_version":"closing-preview/v1",
          "preview_binding":{"opening_entry":"OPENING-1","pos_profile":"OUTLET-01","cashier":"cashier@example.com","invoice_count":10,"payment_modes":["Cash"]},
          "invoice_count":10,"grand_total":"100000.00","net_total":"90909.09","total_quantity":"10.00","total_taxes_and_charges":"9090.91",
          "expected_payments":[{"mode_of_payment":"Cash","opening_amount":"10000.00","expected_amount":"70000.00"}],
          "counted_amount_policy":{"currency":"IDR","decimal_places":2,"max_scale":2,"api_syntax":"ascii_decimal_dot","minimum":"0.00","maximum":"999999999999.99","rounding":"reject","policy_version":"closing-counted-amount/v1"}
        }
    """.trimIndent()

    private fun receiptJson(status: String) = """
        {"closing":{"name":"CLOSING-1","opening_entry":"OPENING-1","pos_profile":"OUTLET-01","status":"$status","invoice_count":10,"grand_total":"100000.00","net_total":"90909.09","total_quantity":"10.00","total_taxes_and_charges":"9090.91","payments":[{"mode_of_payment":"Cash","opening_amount":"10000.00","expected_amount":"70000.00","counted_amount":"69000.00","difference":"-1000.00"}],"reconciliation":{"expected_total":"70000.00","counted_total":"69000.00","difference_total":"-1000.00"},"failure":null}}
    """.trimIndent()

    private object StaticTokenProvider : AuthTokenProvider {
        override fun currentAccessToken() = "token"
        override fun refreshAccessToken(): String? = null
        override fun currentTokens(): OAuthTokens? = null
    }
}
