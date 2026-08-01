package com.rotiropi.pos_erpnext.data.api

import com.rotiropi.pos_erpnext.auth.AuthenticationOwner
import com.rotiropi.pos_erpnext.auth.OAuthConfiguration
import com.rotiropi.pos_erpnext.auth.OAuthCoordinator
import com.rotiropi.pos_erpnext.auth.OAuthTokens
import com.rotiropi.pos_erpnext.auth.RecordingAttemptStore
import com.rotiropi.pos_erpnext.auth.RecordingLauncher
import com.rotiropi.pos_erpnext.auth.RecordingTokenStore
import com.rotiropi.pos_erpnext.auth.TokenExchanger
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class AuthenticatedClientLogoutRaceTest {

    @Serializable
    private data class ResponseData(val value: String)

    private lateinit var server: MockWebServer
    private lateinit var origin: CanonicalBackendOrigin
    private lateinit var okHttp: OkHttpClient

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
        origin = CanonicalBackendOrigin.parse(server.url("/").toString())
        okHttp = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `concurrent 401 reads finishing after logout cannot restore authentication`() {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(401)) }
        val tokenStore = RecordingTokenStore(RuntimeEnvironment.application).apply {
            stored = boundTokens("expired")
        }
        val exchanger = LatchingRefreshExchanger()
        val coordinator = coordinator(tokenStore, exchanger)
        val owner = AuthenticationOwner(coordinator)
        val client = AuthenticatedMobilePosApiClient(
            origin,
            CoordinatorAuthTokenProvider(coordinator),
            okHttp
        )
        val results = Collections.synchronizedList(mutableListOf<ApiResult<ResponseData>>())
        val reads = List(4) {
            Thread { results += client.execute(request(), ResponseData.serializer()) }
        }

        reads.forEach(Thread::start)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (server.requestCount < 4 && System.nanoTime() < deadline) Thread.yield()
        assertEquals(4, server.requestCount)
        assertTrue(exchanger.started.await(5, TimeUnit.SECONDS))
        owner.logout()
        exchanger.release.countDown()
        reads.forEach { it.join(5_000) }

        assertEquals(4, results.size)
        assertTrue(results.all {
            it is ApiResult.TransportFailure &&
                it.kind == TransportFailureKind.AUTHENTICATION_REQUIRED
        })
        assertEquals(4, server.requestCount)
        assertNull(tokenStore.stored)
        assertFalse(owner.isAuthenticated)
        assertFalse(AuthenticationOwner(coordinator).isAuthenticated)
    }

    @Test
    fun `production provider rejects mismatched identity before Authorization header`() {
        val tokenStore = RecordingTokenStore(RuntimeEnvironment.application).apply {
            stored = boundTokens("wrong-origin").copy(canonicalOrigin = "https://evil.example.com")
        }
        val coordinator = coordinator(tokenStore, LatchingRefreshExchanger())
        val client = AuthenticatedMobilePosApiClient(
            origin,
            CoordinatorAuthTokenProvider(coordinator),
            okHttp
        )

        val result = client.execute(request(), ResponseData.serializer())

        assertEquals(
            TransportFailureKind.AUTHENTICATION_REQUIRED,
            (result as ApiResult.TransportFailure).kind
        )
        assertEquals(0, server.requestCount)
        assertNull(tokenStore.stored)
    }

    private fun coordinator(
        tokenStore: RecordingTokenStore,
        exchanger: TokenExchanger
    ): OAuthCoordinator = OAuthCoordinator(
        RuntimeEnvironment.application,
        OAuthConfiguration.create(
            origin.serialized,
            CLIENT_ID,
            "${origin.serialized}${OAuthConfiguration.REDIRECT_PATH}",
            OAuthConfiguration.AUTHORIZE_PATH,
            OAuthConfiguration.TOKEN_PATH,
            OAuthConfiguration.SCOPE,
            OAuthConfiguration.ATTEMPT_LIFETIME_MINUTES
        ),
        RecordingAttemptStore(RuntimeEnvironment.application),
        tokenStore,
        RecordingLauncher(),
        exchanger
    )

    private fun request() = MobilePosRequest.get(
        MobilePosEndpoint.CATALOG_SEARCH,
        mapOf("pos_profile" to "Outlet 01")
    )

    private fun boundTokens(accessToken: String) = OAuthTokens(
        accessToken,
        "refresh",
        Long.MAX_VALUE,
        canonicalOrigin = origin.serialized,
        clientId = CLIENT_ID
    )

    private class LatchingRefreshExchanger : TokenExchanger {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun exchangeAuthorizationCode(
            config: OAuthConfiguration,
            code: String,
            codeVerifier: String
        ): OAuthTokens? = null

        override fun refreshAccessToken(
            config: OAuthConfiguration,
            refreshToken: String
        ): OAuthTokens {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            return OAuthTokens("stale", "refresh", Long.MAX_VALUE)
        }
    }

    private companion object {
        const val CLIENT_ID = "test-client"
    }
}
