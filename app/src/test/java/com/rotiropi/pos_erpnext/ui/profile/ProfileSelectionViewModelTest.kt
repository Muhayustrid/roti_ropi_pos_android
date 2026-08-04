package com.rotiropi.pos_erpnext.ui.profile

import com.rotiropi.pos_erpnext.auth.OAuthTokens
import com.rotiropi.pos_erpnext.data.MobilePosRepository
import com.rotiropi.pos_erpnext.data.api.AuthTokenProvider
import com.rotiropi.pos_erpnext.data.api.AuthenticatedMobilePosApiClient
import com.rotiropi.pos_erpnext.data.api.CanonicalBackendOrigin
import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Task 4 profile selection ViewModel behavior:
 * - immutable [ProfileSelectionUiState] exposes the profile list, selected name,
 *   `selectionRequired`, `refreshing`, and an error/retry signal
 * - constructing the ViewModel and reading state never start a network refresh
 * - one profile auto-selected by the repository: selectionRequired false, no refresh
 * - `selectProfile(name)` derives the trigger from the pre-action state
 *   (PROFILE_SELECTED when no previous selection, PROFILE_CHANGED when the
 *   selection changes), issues exactly one repository.refreshCapabilities(trigger),
 *   and re-selecting the same profile causes no refresh
 * - an unknown profile exposes an invalid-selection error and no refresh
 * - a failed refresh keeps capabilities disabled via the repository and exposes
 *   retry without any automatic retry or loop
 * - `retry()` issues exactly one RETRY refresh when an error exists, otherwise no-op
 */
class ProfileSelectionViewModelTest {

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
    fun `construction never triggers a refresh`() {
        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        val repo = repository()
        repo.bootstrap("OUTLET-01")

        val viewModel = ProfileSelectionViewModel(repo)

        assertEquals(1, server.requestCount)
        assertNotNull(viewModel.uiState)
    }

    @Test
    fun `reading state never triggers a refresh`() {
        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        val repo = repository()
        repo.bootstrap("OUTLET-01")

        val viewModel = ProfileSelectionViewModel(repo)
        repeat(3) {
            viewModel.uiState.profiles
            viewModel.uiState.selectedProfileName
            viewModel.uiState.selectionRequired
            viewModel.uiState.refreshing
        }

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `single profile auto selected by repository requires no selection and no refresh`() {
        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        val repo = repository()
        repo.bootstrap("OUTLET-01")
        val viewModel = ProfileSelectionViewModel(repo)

        val state = viewModel.uiState

        assertEquals(listOf("OUTLET-01"), state.profiles.map { it.name })
        assertEquals("OUTLET-01", state.selectedProfileName)
        assertFalse(state.selectionRequired)
        assertFalse(state.refreshing)
        assertNull(state.error)
        assertFalse(state.retryRequired)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `multiple profiles without selection require selection and disable actions`() {
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val repo = repository()
        repo.bootstrap(null)
        val viewModel = ProfileSelectionViewModel(repo)

        val state = viewModel.uiState

        assertEquals(listOf("OUTLET-01", "OUTLET-02"), state.profiles.map { it.name })
        assertNull(state.selectedProfileName)
        assertTrue(state.selectionRequired)
        assertFalse(state.refreshing)
        assertFalse(state.anyActionEnabled)
        assertFalse(repo.state.capabilities.any)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `selecting a profile without previous selection issues one PROFILE_SELECTED refresh`() {
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val repo = repository()
        repo.bootstrap(null)
        val viewModel = ProfileSelectionViewModel(repo)
        assertEquals(1, server.requestCount)

        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        viewModel.selectProfile("OUTLET-01")

        val state = viewModel.uiState
        assertEquals("OUTLET-01", state.selectedProfileName)
        assertFalse(state.selectionRequired)
        assertFalse(state.refreshing)
        assertNull(state.error)
        assertFalse(state.retryRequired)
        assertTrue(state.anyActionEnabled)
        // Initial bootstrap + exactly one authoritative refresh.
        assertEquals(2, server.requestCount)
        server.takeRequest() // initial bootstrap
        val refreshUrl = server.takeRequest().requestUrl
        assertEquals(MobilePosEndpoint.BOOTSTRAP_GET.path, refreshUrl!!.encodedPath)
        assertEquals("OUTLET-01", refreshUrl.queryParameter("pos_profile"))
    }

    @Test
    fun `profile change callback runs before its refresh`() {
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val repo = repository()
        repo.bootstrap(null)
        var selectedDuringCallback: String? = null
        var requestsDuringCallback = -1
        val viewModel = ProfileSelectionViewModel(repo) {
            selectedDuringCallback = repo.state.selectedProfile?.name
            requestsDuringCallback = server.requestCount
        }

        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        viewModel.selectProfile("OUTLET-01")

        assertEquals("OUTLET-01", selectedDuringCallback)
        assertEquals(1, requestsDuringCallback)
    }

    @Test
    fun `changing an existing selection issues one PROFILE_CHANGED refresh`() {
        // Multi-profile bootstrap with OUTLET-01 already selected by the server.
        val body = fixture("bootstrap-multiple-profiles.json")
            .replace(
                "\"selected_profile\": null,",
                "\"selected_profile\": {\"name\": \"OUTLET-01\",\"company\": \"Roti Ropi\",\"warehouse\": \"Outlet 01 - RR\",\"currency\": \"IDR\",\"selling_price_list\": \"PG-Outlet 01\",\"customer\": \"Walk In Customer\",\"allow_partial_payment\": false,\"invoice_mode\": \"POS Invoice\"},"
            )
        server.enqueue(response(body))
        val repo = repository()
        repo.bootstrap("OUTLET-01")
        val viewModel = ProfileSelectionViewModel(repo)
        assertEquals("OUTLET-01", viewModel.uiState.selectedProfileName)
        assertEquals(1, server.requestCount)

        // The refresh response echoes the requested pos_profile=OUTLET-02 selection.
        val refreshBody = fixture("bootstrap-multiple-profiles.json")
            .replace(
                "\"selected_profile\": null,",
                "\"selected_profile\": {\"name\": \"OUTLET-02\",\"company\": \"Roti Ropi\",\"warehouse\": \"Outlet 02 - RR\",\"currency\": \"IDR\",\"selling_price_list\": \"PG-Outlet 02\",\"customer\": \"Walk In Customer\",\"allow_partial_payment\": false,\"invoice_mode\": \"POS Invoice\"},"
            )
        server.enqueue(response(refreshBody))
        viewModel.selectProfile("OUTLET-02")

        assertEquals("OUTLET-02", viewModel.uiState.selectedProfileName)
        assertFalse(viewModel.uiState.selectionRequired)
        assertFalse(viewModel.uiState.refreshing)
        assertNull(viewModel.uiState.error)
        assertEquals(2, server.requestCount)
        server.takeRequest() // initial bootstrap
        val refreshUrl = server.takeRequest().requestUrl
        assertEquals(MobilePosEndpoint.BOOTSTRAP_GET.path, refreshUrl!!.encodedPath)
        assertEquals("OUTLET-02", refreshUrl.queryParameter("pos_profile"))
    }

    @Test
    fun `re selecting the same profile causes no refresh`() {
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val repo = repository()
        repo.bootstrap(null)
        val viewModel = ProfileSelectionViewModel(repo)
        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        viewModel.selectProfile("OUTLET-01")
        assertEquals(2, server.requestCount)
        assertFalse(viewModel.uiState.refreshing)

        viewModel.selectProfile("OUTLET-01")

        assertEquals("OUTLET-01", viewModel.uiState.selectedProfileName)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `re selecting the same profile after a failed refresh stays a no-op that keeps the retry signal`() {
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val repo = repository()
        repo.bootstrap(null)
        val viewModel = ProfileSelectionViewModel(repo)
        server.enqueue(MockResponse().setResponseCode(503))
        viewModel.selectProfile("OUTLET-01")
        assertTrue(viewModel.uiState.retryRequired)
        assertEquals(2, server.requestCount)

        viewModel.selectProfile("OUTLET-01")

        // The failed refresh remains surfaced; re-selection is a strict no-op.
        assertTrue(viewModel.uiState.retryRequired)
        assertFalse(viewModel.uiState.refreshing)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `unknown profile exposes invalid selection error and no refresh`() {
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val repo = repository()
        repo.bootstrap(null)
        val viewModel = ProfileSelectionViewModel(repo)
        assertEquals(1, server.requestCount)

        viewModel.selectProfile("UNKNOWN-PROFILE")

        val state = viewModel.uiState
        // The repository rejected the unknown profile, so no selection is published.
        assertNull(state.selectedProfileName)
        assertTrue(state.selectionRequired)
        assertFalse(state.refreshing)
        assertNotNull(state.error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `failed refresh keeps capabilities disabled and exposes retry without automatic retry`() {
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val repo = repository()
        repo.bootstrap(null)
        val viewModel = ProfileSelectionViewModel(repo)
        server.enqueue(MockResponse().setResponseCode(503))

        viewModel.selectProfile("OUTLET-01")

        val state = viewModel.uiState
        assertEquals("OUTLET-01", state.selectedProfileName)
        assertTrue(state.retryRequired)
        assertFalse(state.refreshing)
        assertFalse(state.anyActionEnabled)
        assertFalse(state.selectionRequired)
        assertFalse(repo.state.capabilities.any)
        // No automatic retry fired after the failed refresh.
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `retry after a failed refresh issues exactly one RETRY refresh and restores capabilities`() {
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val repo = repository()
        repo.bootstrap(null)
        val viewModel = ProfileSelectionViewModel(repo)
        server.enqueue(MockResponse().setResponseCode(503))
        viewModel.selectProfile("OUTLET-01")
        assertTrue(viewModel.uiState.retryRequired)
        assertEquals(2, server.requestCount)

        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        viewModel.retry()

        val state = viewModel.uiState
        assertEquals("OUTLET-01", state.selectedProfileName)
        assertFalse(state.retryRequired)
        assertNull(state.error)
        assertFalse(state.refreshing)
        assertTrue(state.anyActionEnabled)
        assertTrue(repo.state.capabilities.hasEnabled)
        // Initial bootstrap + failed refresh + exactly one retry refresh.
        assertEquals(3, server.requestCount)
        server.takeRequest() // initial bootstrap
        server.takeRequest() // failed selection refresh
        val retryUrl = server.takeRequest().requestUrl
        assertEquals(MobilePosEndpoint.BOOTSTRAP_GET.path, retryUrl!!.encodedPath)
        assertEquals("OUTLET-01", retryUrl.queryParameter("pos_profile"))
    }

    @Test
    fun `concurrent profile selections accept first action and ignore second`() {
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val repo = repository()
        repo.bootstrap(null)
        val viewModel = ProfileSelectionViewModel(repo)
        server.enqueue(
            response(fixture("bootstrap-one-profile.json"))
                .setBodyDelay(700, TimeUnit.MILLISECONDS)
        )
        val firstStarted = CountDownLatch(1)
        val firstDone = CountDownLatch(1)
        val first = Thread {
            firstStarted.countDown()
            viewModel.selectProfile("OUTLET-01")
            firstDone.countDown()
        }
        first.start()
        firstStarted.await()
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertTrue(viewModel.uiState.refreshing)

        viewModel.selectProfile("OUTLET-02")
        assertTrue(firstDone.await(5, TimeUnit.SECONDS))

        assertEquals("OUTLET-01", viewModel.uiState.selectedProfileName)
        assertEquals(2, server.requestCount)
        assertFalse(viewModel.uiState.refreshing)
    }

    @Test
    fun `clear removes stale profile error and retry state`() {
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val repo = repository()
        repo.bootstrap(null)
        val viewModel = ProfileSelectionViewModel(repo)
        server.enqueue(MockResponse().setResponseCode(503))
        viewModel.selectProfile("OUTLET-01")
        assertTrue(viewModel.uiState.retryRequired)

        repo.clear()
        viewModel.clear()

        assertNull(viewModel.uiState.error)
        assertFalse(viewModel.uiState.retryRequired)
        assertFalse(viewModel.uiState.refreshing)
        assertTrue(viewModel.uiState.profiles.isEmpty())
    }

    @Test
    fun `failed selection then successful retry publishes durable success state`() {
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val repo = repository()
        repo.bootstrap(null)
        val viewModel = ProfileSelectionViewModel(repo)
        val emissions = mutableListOf<ProfileSelectionUiState>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch { viewModel.state.collect { emissions += it } }
        try {
            server.enqueue(MockResponse().setResponseCode(503))
            viewModel.selectProfile("OUTLET-01")
            assertTrue(viewModel.state.value.retryRequired)

            server.enqueue(response(fixture("bootstrap-one-profile.json")))
            viewModel.retry()

            assertFalse(viewModel.state.value.retryRequired)
            assertNull(viewModel.state.value.error)
            assertEquals("OUTLET-01", viewModel.state.value.selectedProfileName)
            assertTrue(viewModel.state.value.anyActionEnabled)
            assertTrue(emissions.any { it.retryRequired })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `new observer after selection completion sees durable repository result`() {
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val repo = repository()
        repo.bootstrap(null)
        val first = ProfileSelectionViewModel(repo)
        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        first.selectProfile("OUTLET-01")

        val recreated = ProfileSelectionViewModel(repo)

        assertEquals("OUTLET-01", recreated.state.value.selectedProfileName)
        assertFalse(recreated.state.value.selectionRequired)
        assertTrue(recreated.state.value.anyActionEnabled)
    }

    @Test
    fun `retry without an existing error is a no-op`() {
        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        val repo = repository()
        repo.bootstrap("OUTLET-01")
        val viewModel = ProfileSelectionViewModel(repo)
        assertEquals(1, server.requestCount)

        viewModel.retry()

        assertFalse(viewModel.uiState.retryRequired)
        assertEquals(1, server.requestCount)
    }

    // --- helpers (mirror BootstrapRepositoryTest) ---

    private fun repository(): MobilePosRepository {
        val client = AuthenticatedMobilePosApiClient(
            origin,
            StaticTokenProvider,
            okHttp,
            json
        )
        return MobilePosRepository(client)
    }

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/api/v1/$name")!!.bufferedReader().readText()

    private fun response(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(envelope(body))

    private fun envelope(data: String): String =
        """{"message":{"ok":true,"data":$data,"meta":{"api_version":"v1","request_id":"req-1","server_time":"2026-07-31T00:00:00Z"}}}"""

    private object StaticTokenProvider : AuthTokenProvider {
        override fun currentAccessToken(): String = "token-secret"
        override fun refreshAccessToken(): String? = null
        override fun currentTokens() = OAuthTokens("token-secret", null, Long.MAX_VALUE)
    }
}
