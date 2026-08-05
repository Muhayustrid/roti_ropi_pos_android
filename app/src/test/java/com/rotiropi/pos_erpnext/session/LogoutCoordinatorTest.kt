package com.rotiropi.pos_erpnext.session

import android.content.Context
import com.rotiropi.pos_erpnext.auth.AuthenticationOwner
import com.rotiropi.pos_erpnext.auth.AuthenticationState
import com.rotiropi.pos_erpnext.auth.OAuthConfiguration
import com.rotiropi.pos_erpnext.auth.OAuthCoordinator
import com.rotiropi.pos_erpnext.auth.OAuthTokens
import com.rotiropi.pos_erpnext.auth.RecordingAttemptStore
import com.rotiropi.pos_erpnext.auth.RecordingExchanger
import com.rotiropi.pos_erpnext.auth.RecordingLauncher
import com.rotiropi.pos_erpnext.auth.RecordingTokenStore
import com.rotiropi.pos_erpnext.data.MobilePosRepository
import com.rotiropi.pos_erpnext.data.RepositoryResult
import com.rotiropi.pos_erpnext.data.api.AuthTokenProvider
import com.rotiropi.pos_erpnext.data.api.AuthenticatedMobilePosApiClient
import com.rotiropi.pos_erpnext.data.api.CanonicalBackendOrigin
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
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

/**
 * Task 4 logout coordinator behavior:
 * - logout invalidates customer work, then clears customer, repository, profile,
 *   and authentication state
 * - the order guarantees sign-in routing (Unauthenticated) can never observe stale
 *   repository bootstrap/profile/opening/capabilities state
 * - two sequential logout calls remain safe
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class LogoutCoordinatorTest {

    private val context: Context get() = RuntimeEnvironment.application

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
    fun `logout clears repository before logging out authentication`() {
        val repository = populatedRepository()
        val auth = authenticatedOwner()
        val order = mutableListOf<String>()
        var stateWhenRepositoryCleared: AuthenticationState? = null

        // The internal operation constructor accepts the real repository clear and
        // real authentication logout; recording wrappers assert their exact order.
        val coordinator = LogoutCoordinator(
            clearRepository = {
                order += "repository-cleared"
                repository.clear()
                stateWhenRepositoryCleared = auth.owner.state.value
            },
            clearAuthentication = {
                order += "owner-logged-out"
                auth.owner.logout()
            }
        )

        coordinator.logout()

        // Repository memory is cleared before the authentication logout runs.
        assertEquals(listOf("repository-cleared", "owner-logged-out"), order)
        // Unauthenticated has not been published yet while the repository clears.
        assertEquals(AuthenticationState.Authenticated, stateWhenRepositoryCleared)
        assertEquals(AuthenticationState.Unauthenticated, auth.owner.state.value)
        assertRepositoryCleared(repository)
        assertNull(auth.tokenStore.stored)
        assertNull(auth.attemptStore.stored)
    }

    @Test
    fun `logout through the public constructor clears repository and unauthenticates`() {
        val repository = populatedRepository()
        val auth = authenticatedOwner()
        assertTrue(repository.state.hasSelection)
        assertTrue(repository.state.capabilities.any)
        assertTrue(auth.owner.isAuthenticated)

        LogoutCoordinator(
            clearRepository = repository::clear,
            clearProfileUi = com.rotiropi.pos_erpnext.ui.profile.ProfileSelectionViewModel(repository)::clear,
            clearAuthentication = auth.owner::logout,
        ).logout()

        assertRepositoryCleared(repository)
        assertEquals(AuthenticationState.Unauthenticated, auth.owner.state.value)
        assertFalse(auth.owner.isAuthenticated)
        assertNull(auth.tokenStore.stored)
        assertNull(auth.attemptStore.stored)
    }

    @Test
    fun `logout clears profile UI before publishing unauthenticated`() {
        val order = mutableListOf<String>()
        val coordinator = LogoutCoordinator(
            clearRepository = { order += "repository-cleared" },
            clearProfileUi = { order += "profile-ui-cleared" },
            clearAuthentication = { order += "owner-logged-out" }
        )

        coordinator.logout()

        assertEquals(
            listOf("repository-cleared", "profile-ui-cleared", "owner-logged-out"),
            order
        )
    }

    @Test
    fun `logout invalidates cancels and clears customer and cashier before repository profile and authentication`() {
        val order = mutableListOf<String>()
        val coordinator = LogoutCoordinator(
            invalidateCustomerAuthority = { order += "customer-authority-invalidated" },
            cancelCustomerRequest = { order += "customer-request-cancelled" },
            clearCustomerUi = { order += "customer-ui-cleared" },
            clearRepository = { order += "repository-cleared" },
            clearProfileUi = { order += "profile-ui-cleared" },
            clearCashierUi = { order += "cashier-ui-cleared" },
            clearAuthentication = { order += "owner-logged-out" },
        )

        coordinator.logout()

        assertEquals(
            listOf(
                "customer-authority-invalidated",
                "customer-request-cancelled",
                "customer-ui-cleared",
                "repository-cleared",
                "profile-ui-cleared",
                "cashier-ui-cleared",
                "owner-logged-out",
            ),
            order,
        )
    }

    @Test
    fun `two sequential logout calls remain safe`() {
        val repository = populatedRepository()
        val auth = authenticatedOwner()
        val order = mutableListOf<String>()
        val coordinator = LogoutCoordinator(
            clearRepository = {
                order += "repository-cleared"
                repository.clear()
            },
            clearAuthentication = {
                order += "owner-logged-out"
                auth.owner.logout()
            }
        )

        coordinator.logout()
        coordinator.logout()

        assertEquals(
            listOf(
                "repository-cleared", "owner-logged-out",
                "repository-cleared", "owner-logged-out"
            ),
            order
        )
        assertRepositoryCleared(repository)
        assertEquals(AuthenticationState.Unauthenticated, auth.owner.state.value)
    }

    // --- helpers ---

    private fun populatedRepository(): MobilePosRepository {
        server.enqueue(response(fixture("bootstrap-stale-opening.json")))
        val repository = MobilePosRepository(
            AuthenticatedMobilePosApiClient(origin, StaticTokenProvider, okHttp, json)
        )
        val result = repository.bootstrap("OUTLET-01")
        check(result is RepositoryResult.Success) { "bootstrap fixture failed: $result" }
        return repository
    }

    private fun assertRepositoryCleared(repository: MobilePosRepository) {
        assertNull(repository.state.bootstrap)
        assertNull(repository.state.selectedProfile)
        assertNull(repository.state.opening)
        assertTrue(repository.state.profiles.isEmpty())
        assertFalse(repository.state.capabilities.any)
        assertFalse(repository.state.capabilities.hasEnabled)
        assertNull(repository.state.bootstrapFailure)
    }

    private fun authenticatedOwner(): AuthFixture {
        val config = OAuthConfiguration.create(
            canonicalOrigin = ORIGIN,
            clientId = CLIENT_ID,
            redirectUri = REDIRECT_URI,
            authorizePath = OAuthConfiguration.AUTHORIZE_PATH,
            tokenPath = OAuthConfiguration.TOKEN_PATH,
            scope = OAuthConfiguration.SCOPE,
            lifetimeMinutes = OAuthConfiguration.ATTEMPT_LIFETIME_MINUTES
        )
        val attemptStore = RecordingAttemptStore(context)
        val tokenStore = RecordingTokenStore(context)
        val coordinator = OAuthCoordinator(
            context,
            config,
            attemptStore,
            tokenStore,
            RecordingLauncher(),
            RecordingExchanger()
        )
        val owner = AuthenticationOwner(coordinator)
        tokenStore.stored = OAuthTokens(
            accessToken = "access",
            refreshToken = "refresh",
            expiresAt = Long.MAX_VALUE,
            canonicalOrigin = ORIGIN,
            clientId = CLIENT_ID
        )
        owner.restoreAuthenticationState()
        return AuthFixture(owner, attemptStore, tokenStore)
    }

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/api/v1/$name")!!.bufferedReader().readText()

    private fun response(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(envelope(body))

    private fun envelope(data: String): String =
        """{"message":{"ok":true,"data":$data,"meta":{"api_version":"v1","request_id":"req-1","server_time":"2026-07-31T00:00:00Z"}}}"""

    private data class AuthFixture(
        val owner: AuthenticationOwner,
        val attemptStore: RecordingAttemptStore,
        val tokenStore: RecordingTokenStore
    )

    private object StaticTokenProvider : AuthTokenProvider {
        override fun currentAccessToken(): String = "token-secret"
        override fun refreshAccessToken(): String? = null
        override fun currentTokens() = OAuthTokens("token-secret", null, Long.MAX_VALUE)
    }

    private companion object {
        const val ORIGIN = "https://example.com"
        const val CLIENT_ID = "test-client"
        const val REDIRECT_URI = "$ORIGIN/android/oauth2redirect"
    }
}
