package com.rotiropi.pos_erpnext.data

import com.rotiropi.pos_erpnext.data.api.AuthTokenProvider
import com.rotiropi.pos_erpnext.data.api.AuthenticatedMobilePosApiClient
import com.rotiropi.pos_erpnext.data.api.CanonicalBackendOrigin
import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import java.util.concurrent.TimeUnit
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

class OpeningRepositoryTest {
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
        val client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        repository = MobilePosRepository(
            AuthenticatedMobilePosApiClient(
                CanonicalBackendOrigin.parse(server.url("/").toString()),
                object : AuthTokenProvider {
                    override fun currentAccessToken() = "token"
                    override fun refreshAccessToken(): String? = null
                    override fun currentTokens(): com.rotiropi.pos_erpnext.auth.OAuthTokens? = null
                },
                client,
                Json { ignoreUnknownKeys = true },
            )
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `current session sends selected profile and maps server opening`() {
        server.enqueue(success(fixture("bootstrap-one-profile.json")))
        assertTrue(repository.bootstrap("OUTLET-01") is RepositoryResult.Success)
        server.takeRequest()
        server.enqueue(success(fixture("session-current.json")))

        val result = repository.currentSession("OUTLET-01")

        assertTrue(result is CurrentSessionResult.Success)
        val opening = (result as CurrentSessionResult.Success).opening
        assertEquals("OPENING-EXAMPLE-0001", opening?.name)
        assertEquals(listOf("Cash", "Bank"), opening?.openingBalances?.map { it.modeOfPayment })
        assertEquals(opening, repository.state.opening)
        val request = server.takeRequest()
        assertEquals(MobilePosEndpoint.SESSIONS_CURRENT.path, request.requestUrl!!.encodedPath)
        assertEquals("OUTLET-01", request.requestUrl!!.queryParameter("pos_profile"))
    }

    @Test
    fun `no current session publishes null without inventing opening`() {
        server.enqueue(success("""{"opening_session":null}"""))

        val result = repository.currentSession("PROFILE-EXAMPLE")

        assertEquals(CurrentSessionResult.Success(null), result)
        assertNull(repository.state.opening)
    }

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/api/v1/$name")!!.bufferedReader().readText()

    private fun success(data: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """{"message":{"ok":true,"data":$data,"meta":{"api_version":"v1","request_id":"request","server_time":"2026-08-03T00:00:00+00:00","replayed":false}}}"""
        )
}
