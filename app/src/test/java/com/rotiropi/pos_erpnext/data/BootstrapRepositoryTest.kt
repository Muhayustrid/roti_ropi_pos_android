package com.rotiropi.pos_erpnext.data

import com.rotiropi.pos_erpnext.auth.OAuthTokens
import com.rotiropi.pos_erpnext.data.api.AuthTokenProvider
import com.rotiropi.pos_erpnext.data.api.AuthenticatedMobilePosApiClient
import com.rotiropi.pos_erpnext.data.api.CanonicalBackendOrigin
import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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
 * Task 4 core bootstrap repository behavior:
 * - maps the versioned bootstrap DTOs to immutable domain models and publishes state
 * - selection rules: one profile auto-selected, multiple require explicit selection
 * - capabilities are server-derived and mutation-disabled whenever selection is absent
 * - `STALE_OPENING` warning surfaced as a typed code
 * - 401 stays a typed transport failure (OAuth behavior not weakened)
 * - failed required refresh leaves mutations disabled until an explicit Retry trigger
 * - concurrent refresh triggers coalesce into exactly one in-flight bootstrap request
 * - bootstrap completion/observation/rendering/refresh failure never recursively refresh
 * - `clear()` resets bootstrap/profile/opening/capabilities state
 */
class BootstrapRepositoryTest {

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

    // --- bootstrap / mapping ---

    @Test
    fun `bootstrap with no profiles leaves no selection and disables all mutations`() {
        server.enqueue(response(fixture("bootstrap-one-profile.json"), profiles = emptyList(), selectedProfile = null))
        val repo = repository()

        val result = repo.bootstrap("OUTLET-01")

        assertTrue(result.isSuccess())
        val state = repo.state
        assertNotNull(state.bootstrap)
        assertNull(state.selectedProfile)
        assertFalse(state.hasSelection)
        assertTrue(state.profiles.isEmpty())
        assertNull(state.opening)
        assertCapabilities(state, open = false, submit = false, returnSale = false, cancel = false, close = false)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `bootstrap maps dto fields to separate immutable domain models`() {
        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        val repo = repository()

        repo.bootstrap("OUTLET-01")

        val state = repo.state
        val profile = requireNotNull(state.selectedProfile)
        assertEquals("OUTLET-01", profile.name)
        assertEquals("Roti Ropi", profile.company)
        assertEquals("Outlet 01 - RR", profile.warehouse)
        assertEquals("IDR", profile.currency)
        assertEquals("PG-Outlet 01", profile.sellingPriceList)
        assertEquals("Walk In Customer", profile.customer)
        assertFalse(profile.allowPartialPayment)
        assertEquals("POS Invoice", profile.invoiceMode)
        assertEquals("cashier@example.com", state.bootstrap?.user?.name)
        assertEquals(1, state.profiles.size)
        assertNotNull(state.bootstrap?.posMode)
    }

    @Test
    fun `one profile is auto-selected even when server selected_profile is null`() {
        server.enqueue(
            response(fixture("bootstrap-one-profile.json"), profiles = listOf(oneProfile), selectedProfile = null)
        )
        val repo = repository()

        val result = repo.bootstrap("OUTLET-01")

        assertTrue(result.isSuccess())
        assertEquals("OUTLET-01", repo.state.selectedProfile?.name)
        assertTrue(repo.state.hasSelection)
        // Capabilities remain server-derived when a selection exists.
        assertCapabilities(repo.state, open = true, submit = false, returnSale = false, cancel = false, close = false)
    }

    @Test
    fun `multiple profiles without selected_profile require explicit selection and force mutations off`() {
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val repo = repository()

        val result = repo.bootstrap(null)

        assertTrue(result.isSuccess())
        assertNull(repo.state.selectedProfile)
        assertFalse(repo.state.hasSelection)
        assertEquals(listOf("OUTLET-01", "OUTLET-02"), repo.state.profiles.map { it.name })
        // Even though the server returned open_session=true, no selection forces all mutations off.
        assertCapabilities(repo.state, open = false, submit = false, returnSale = false, cancel = false, close = false)
    }

    @Test
    fun `multiple profiles with selected_profile retain the server selection`() {
        val body = fixture("bootstrap-multiple-profiles.json")
            .replace("\"selected_profile\": null,", "\"selected_profile\": {\"name\": \"OUTLET-02\",\"company\": \"Roti Ropi\",\"warehouse\": \"Outlet 02 - RR\",\"currency\": \"IDR\",\"selling_price_list\": \"PG-Outlet 02\",\"customer\": \"Walk In Customer\",\"allow_partial_payment\": false,\"invoice_mode\": \"POS Invoice\"},")
        server.enqueue(response(body))
        val repo = repository()

        val result = repo.bootstrap("OUTLET-02")

        assertTrue(result.isSuccess())
        assertEquals("OUTLET-02", repo.state.selectedProfile?.name)
        assertTrue(repo.state.hasSelection)
        assertCapabilities(repo.state, open = false, submit = false, returnSale = false, cancel = false, close = false)
    }

    @Test
    fun `stale opening surfaces warning code exactly STALE_OPENING`() {
        server.enqueue(response(fixture("bootstrap-stale-opening.json")))
        val repo = repository()

        repo.bootstrap("OUTLET-01")

        val opening = requireNotNull(repo.state.opening)
        assertEquals("POS-OPE-2026-00001", opening.name)
        assertEquals(OpeningStatus.OPEN, opening.status)
        val warnings = opening.warnings
        assertEquals(1, warnings.size)
        assertEquals("STALE_OPENING", warnings.single().code)
    }

    @Test
    fun `additive unknown response fields parse successfully`() {
        val body = fixture("bootstrap-one-profile.json")
            .replace("\"pos_mode\": \"POS Invoice\"", "\"pos_mode\": \"POS Invoice\",\"future_field\": \"future\",\"nested\": {\"a\": 1}")
        server.enqueue(response(body))
        val repo = repository()

        val result = repo.bootstrap("OUTLET-01")

        assertTrue(result.isSuccess())
        assertEquals("OUTLET-01", repo.state.selectedProfile?.name)
    }

    // --- failures ---

    @Test
    fun `HTTP 401 yields existing typed failure and does not weaken oauth behavior`() {
        server.enqueue(MockResponse().setResponseCode(401))
        val repo = repository()

        val result = repo.bootstrap("OUTLET-01")

        assertTrue(result.isFailure())
        assertTrue(repo.state.bootstrapFailure is BootstrapFailure.AuthRequired)
        assertFalse(repo.state.capabilities.any)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `known offline startup begins with mutations disabled and unavailable state`() {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        val repo = repository()

        val result = repo.bootstrap("OUTLET-01")

        assertTrue(result.isFailure())
        assertFalse(repo.state.capabilities.any)
        assertFalse(repo.state.capabilities.hasEnabled)
        assertTrue(repo.state.bootstrapFailure is BootstrapFailure.Unavailable)
        assertNull(repo.state.selectedProfile)
    }

    @Test
    fun `failed required refresh leaves mutations disabled until explicit retry`() {
        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        val repo = repository()
        repo.bootstrap("OUTLET-01")
        assertTrue(repo.state.capabilities.hasEnabled)

        server.enqueue(MockResponse().setResponseCode(503))
        val failed = repo.refreshCapabilities(BootstrapRefreshTrigger.APP_OPEN)
        assertTrue(failed.isFailure())
        assertTrue(repo.state.bootstrapFailure is BootstrapFailure.Unavailable)
        // A failed required refresh disables mutations even when the previous
        // bootstrap had enabled capabilities.
        assertFalse(repo.state.capabilities.hasEnabled)
        assertFalse(repo.state.capabilities.any)

        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        val retried = repo.refreshCapabilities(BootstrapRefreshTrigger.RETRY)

        assertTrue(retried.isSuccess())
        assertTrue(repo.state.capabilities.hasEnabled)
        assertEquals(3, server.requestCount)
    }

    // --- refresh coalescing / recursion ---

    @Test
    fun `bootstrap completion never recursively triggers refresh`() {
        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        val repo = repository()

        repo.bootstrap("OUTLET-01")

        // Exactly one bootstrap request, no second refresh request was fired.
        assertEquals(1, server.requestCount)
        assertTrue(repo.state.capabilities.hasEnabled)
    }

    @Test
    fun `state observation never recursively triggers refresh`() {
        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        val repo = repository()
        repo.bootstrap("OUTLET-01")

        repeat(3) {
            repo.state
            repo.state.capabilities
            repo.state.selectedProfile
        }

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `concurrent refresh triggers coalesce into exactly one in flight bootstrap request`() {
        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        val repo = repository()
        repo.bootstrap("OUTLET-01")
        assertEquals(1, server.requestCount)

        // Slow refresh response keeps the in-flight window wide so both callers
        // deterministically share one network call.
        server.enqueue(response(fixture("bootstrap-one-profile.json")).setBodyDelay(700, TimeUnit.MILLISECONDS))

        val start = CountDownLatch(1)
        val completed = CountDownLatch(2)
        val results = java.util.concurrent.ConcurrentLinkedQueue<RepositoryResult>()
        val threads = (1..2).map {
            Thread {
                start.await()
                results.add(repo.refreshCapabilities(BootstrapRefreshTrigger.APP_OPEN))
                completed.countDown()
            }
        }
        threads.forEach(Thread::start)
        start.countDown()
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        threads.forEach { it.join(5_000) }

        // Bootstrap (1) plus exactly one coalesced refresh request (1): a second
        // caller without coalescing would have added a third request.
        assertEquals(2, server.requestCount)
        results.forEach { assertTrue(it.isSuccess()) }

        // The refresh request preserves the currently selected profile query.
        server.takeRequest()
        val refreshUrl = server.takeRequest().requestUrl
        assertEquals("OUTLET-01", refreshUrl!!.queryParameter("pos_profile"))
        assertEquals(MobilePosEndpoint.BOOTSTRAP_GET.path, refreshUrl.encodedPath)
    }

    @Test
    fun `refresh failure does not recursively trigger refresh`() {
        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        server.enqueue(MockResponse().setResponseCode(503))
        val repo = repository()
        repo.bootstrap("OUTLET-01")

        val result = repo.refreshCapabilities(BootstrapRefreshTrigger.APP_OPEN)

        assertTrue(result.isFailure())
        assertEquals(2, server.requestCount)
    }

    // --- profile selection ---

    @Test
    fun `selectProfile with valid profile sets selection locally disables capabilities and makes no network call`() {
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val repo = repository()
        repo.bootstrap(null)
        assertEquals(1, server.requestCount)
        assertFalse(repo.state.hasSelection)

        val selected = repo.selectProfile("OUTLET-02")

        assertTrue(selected)
        assertEquals("OUTLET-02", repo.state.selectedProfile?.name)
        assertTrue(repo.state.hasSelection)
        // Selection alone disables mutations until the authoritative refresh succeeds.
        assertCapabilities(repo.state, open = false, submit = false, returnSale = false, cancel = false, close = false)
        // Profile list, user, and opening are retained for the selection UI.
        assertEquals(listOf("OUTLET-01", "OUTLET-02"), repo.state.profiles.map { it.name })
        assertEquals("cashier@example.com", repo.state.bootstrap?.user?.name)
        // selectProfile is local-only: no network call fired.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `selectProfile with unknown profile returns false and changes nothing`() {
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val repo = repository()
        repo.bootstrap(null)
        assertFalse(repo.state.hasSelection)

        val selected = repo.selectProfile("UNKNOWN-PROFILE")

        assertFalse(selected)
        assertFalse(repo.state.hasSelection)
        assertNull(repo.state.selectedProfile)
        assertEquals(listOf("OUTLET-01", "OUTLET-02"), repo.state.profiles.map { it.name })
        assertCapabilities(repo.state, open = false, submit = false, returnSale = false, cancel = false, close = false)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `selectProfile with no bootstrap state returns false`() {
        val repo = repository()

        val selected = repo.selectProfile("OUTLET-01")

        assertFalse(selected)
        assertFalse(repo.state.hasSelection)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `refreshCapabilities after selectProfile sends exactly one request with chosen profile query`() {
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val repo = repository()
        repo.bootstrap(null)
        assertTrue(repo.selectProfile("OUTLET-01"))
        assertEquals(1, server.requestCount)

        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        val result = repo.refreshCapabilities(BootstrapRefreshTrigger.PROFILE_SELECTED)

        assertTrue(result.isSuccess())
        // Exactly one additional bootstrap request for the refresh.
        assertEquals(2, server.requestCount)
        server.takeRequest() // initial bootstrap
        val refreshUrl = server.takeRequest().requestUrl
        assertEquals("OUTLET-01", refreshUrl!!.queryParameter("pos_profile"))
        assertEquals(MobilePosEndpoint.BOOTSTRAP_GET.path, refreshUrl.encodedPath)
        // Server-authoritative capabilities are published after the refresh.
        assertEquals("OUTLET-01", repo.state.selectedProfile?.name)
        assertCapabilities(repo.state, open = true, submit = false, returnSale = false, cancel = false, close = false)
    }

    @Test
    fun `selectProfile itself never triggers a refresh`() {
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val repo = repository()
        repo.bootstrap(null)

        repo.selectProfile("OUTLET-01")
        repo.state
        repo.state.capabilities
        repo.state.selectedProfile
        repo.selectProfile("OUTLET-02")

        assertEquals(1, server.requestCount)
    }

    // --- clear ---

    @Test
    fun `clear discards an in flight refresh and a new cashier starts a separate request`() {
        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        val repo = repository()
        repo.bootstrap("OUTLET-01")
        server.takeRequest()

        server.enqueue(
            response(fixture("bootstrap-one-profile.json"))
                .setBodyDelay(700, TimeUnit.MILLISECONDS)
        )
        val staleResult = AtomicReference<RepositoryResult>()
        val staleThread = Thread {
            staleResult.set(repo.refreshCapabilities(BootstrapRefreshTrigger.APP_OPEN))
        }
        staleThread.start()
        val staleRequest = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(staleRequest)

        repo.clear()
        server.enqueue(response(fixture("bootstrap-multiple-profiles.json")))
        val freshResult = AtomicReference<RepositoryResult>()
        val freshThread = Thread {
            freshResult.set(repo.refreshCapabilities(BootstrapRefreshTrigger.AUTH_SUCCESS))
        }
        freshThread.start()
        val freshRequest = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(freshRequest)
        assertNull(freshRequest!!.requestUrl!!.queryParameter("pos_profile"))

        staleThread.join(5_000)
        freshThread.join(5_000)

        assertTrue(staleResult.get() is RepositoryResult.Discarded)
        assertTrue(freshResult.get() is RepositoryResult.Success)
        assertEquals(listOf("OUTLET-01", "OUTLET-02"), repo.state.profiles.map { it.name })
    }

    @Test
    fun `clear discards an in flight failure without publishing it`() {
        val repo = repository()
        server.enqueue(MockResponse().setResponseCode(503).setBodyDelay(700, TimeUnit.MILLISECONDS))
        val result = AtomicReference<RepositoryResult>()
        val thread = Thread {
            result.set(repo.refreshCapabilities(BootstrapRefreshTrigger.APP_OPEN))
        }
        thread.start()
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))

        repo.clear()
        thread.join(5_000)

        assertTrue(result.get() is RepositoryResult.Discarded)
        assertEquals(RepositoryState(), repo.state)
    }

    @Test
    fun `clear resets bootstrap profile opening and capabilities`() {
        server.enqueue(response(fixture("bootstrap-one-profile.json")))
        val repo = repository()
        repo.bootstrap("OUTLET-01")
        assertNotNull(repo.state.bootstrap)
        assertTrue(repo.state.capabilities.hasEnabled)

        repo.clear()

        assertNull(repo.state.bootstrap)
        assertNull(repo.state.selectedProfile)
        assertNull(repo.state.opening)
        assertFalse(repo.state.capabilities.any)
        assertFalse(repo.state.capabilities.hasEnabled)
        assertNull(repo.state.bootstrapFailure)
    }

    // --- helpers ---

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

    private fun response(body: String, profiles: List<Map<String, Any>>, selectedProfile: Any?): MockResponse {
        val original = json.parseToJsonElement(body) as JsonObject
        val rewritten = original.toMutableMap().apply {
            this["profiles"] = JsonArray(profiles.map { json.parseToJsonElement(encodeProfile(it)) as JsonObject })
            this["selected_profile"] = if (selectedProfile == null) JsonNull else json.parseToJsonElement(selectedProfile.toString())
        }
        return response(json.encodeToString(JsonObject.serializer(), JsonObject(rewritten)))
    }

    private fun encodeProfile(profile: Map<String, Any>): String =
        profile.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            val encodedValue = if (value is Boolean) value.toString() else "\"$value\""
            "\"$key\":$encodedValue"
        }

    private companion object {
        private val oneProfile: Map<String, Any> = mapOf(
            "name" to "OUTLET-01",
            "company" to "Roti Ropi",
            "warehouse" to "Outlet 01 - RR",
            "currency" to "IDR",
            "selling_price_list" to "PG-Outlet 01",
            "customer" to "Walk In Customer",
            "allow_partial_payment" to false,
            "invoice_mode" to "POS Invoice"
        )
    }

    private fun assertCapabilities(
        state: RepositoryState,
        open: Boolean,
        submit: Boolean,
        returnSale: Boolean,
        cancel: Boolean,
        close: Boolean
    ) {
        assertEquals(open, state.capabilities.openSession)
        assertEquals(submit, state.capabilities.submitSale)
        assertEquals(returnSale, state.capabilities.createReturn)
        assertEquals(cancel, state.capabilities.cancelSale)
        assertEquals(close, state.capabilities.closeSession)
    }

    private fun RepositoryResult.isSuccess(): Boolean = this is RepositoryResult.Success
    private fun RepositoryResult.isFailure(): Boolean = this is RepositoryResult.Failure

    private object StaticTokenProvider : AuthTokenProvider {
        override fun currentAccessToken(): String = "token-secret"
        override fun refreshAccessToken(): String? = null
        override fun currentTokens() = OAuthTokens("token-secret", null, Long.MAX_VALUE)
    }
}
