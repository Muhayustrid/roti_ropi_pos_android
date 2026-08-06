package com.rotiropi.pos_erpnext.ui.opening

import com.rotiropi.pos_erpnext.data.BootstrapFailure
import com.rotiropi.pos_erpnext.data.BootstrapRefreshTrigger
import com.rotiropi.pos_erpnext.data.BootstrapData
import com.rotiropi.pos_erpnext.data.BootstrapUser
import com.rotiropi.pos_erpnext.data.CurrentSessionResult
import com.rotiropi.pos_erpnext.data.OpeningSession
import com.rotiropi.pos_erpnext.data.OpeningStatus
import com.rotiropi.pos_erpnext.data.PosCapabilities
import com.rotiropi.pos_erpnext.data.PosProfile
import com.rotiropi.pos_erpnext.data.RepositoryResult
import com.rotiropi.pos_erpnext.data.RepositoryState
import org.junit.Assert.assertEquals
import org.junit.Test

class OpeningPostLoginRoutingTest {
    @Test
    fun `login without current session routes to opening`() {
        val result = gate(current = { CurrentSessionResult.Success(null) }).afterAuthentication(authority())

        assertEquals(OpeningRoutingDestination.OPENING, result.destination)
    }

    @Test
    fun `login with matching current session routes to cashier after one current-session request`() {
        var currentCalls = 0
        val result = gate(
            current = {
                currentCalls++
                CurrentSessionResult.Success(opening())
            },
        ).afterAuthentication(authority())

        assertEquals(OpeningRoutingDestination.CASHIER, result.destination)
        assertEquals(1, currentCalls)
    }

    @Test
    fun `login with mismatched current session remains in opening`() {
        val result = gate(
            current = { CurrentSessionResult.Success(opening(profile = "OTHER")) },
        ).afterAuthentication(authority())

        assertEquals(OpeningRoutingDestination.OPENING, result.destination)
    }

    @Test
    fun `current session failure never routes to cashier`() {
        val result = gate(
            current = { CurrentSessionResult.Failure(BootstrapFailure.Unavailable) },
        ).afterAuthentication(authority())

        assertEquals(OpeningRoutingDestination.OPENING, result.destination)
        assertEquals(BootstrapFailure.Unavailable, result.failure)
    }

    @Test
    fun `logout login with an existing opening performs one current-session request`() {
        var currentCalls = 0
        val result = gate(
            current = {
                currentCalls++
                CurrentSessionResult.Success(opening())
            },
        ).afterAuthentication(authority(authenticationGeneration = 8L))

        assertEquals(OpeningRoutingDestination.CASHIER, result.destination)
        assertEquals(1, currentCalls)
    }

    @Test
    fun `profile change reconciles the newly selected profile`() {
        var requestedProfile: String? = null
        val result = gate(
            current = { profile ->
                requestedProfile = profile
                CurrentSessionResult.Success(opening(profile = profile))
            },
        ).afterAuthentication(authority(posProfile = "OUTLET-02", repositoryGeneration = 12L))

        assertEquals(OpeningRoutingDestination.CASHIER, result.destination)
        assertEquals("OUTLET-02", requestedProfile)
    }

    @Test
    fun `immediate opening success refreshes before routing to cashier`() {
        val refreshes = mutableListOf<BootstrapRefreshTrigger>()
        val result = gate(
            current = { CurrentSessionResult.Success(opening()) },
            refresh = { trigger -> refreshes += trigger; RepositoryResult.Success(routingState()) },
        ).afterOpeningSucceeded(authority())

        assertEquals(OpeningRoutingDestination.CASHIER, result.destination)
        assertEquals(listOf(BootstrapRefreshTrigger.OPENING_COMPLETED), refreshes)
    }

    @Test
    fun `replayed opening success refreshes before routing to cashier`() {
        val refreshes = mutableListOf<BootstrapRefreshTrigger>()
        val result = gate(
            current = { CurrentSessionResult.Success(opening()) },
            refresh = { trigger -> refreshes += trigger; RepositoryResult.Success(routingState()) },
        ).afterOpeningSucceeded(authority())

        assertEquals(OpeningRoutingDestination.CASHIER, result.destination)
        assertEquals(listOf(BootstrapRefreshTrigger.OPENING_COMPLETED), refreshes)
    }

    @Test
    fun `capability refresh failure never routes to cashier`() {
        val result = gate(
            current = { CurrentSessionResult.Success(opening()) },
            refresh = { RepositoryResult.Failure(BootstrapFailure.Unavailable) },
        ).afterOpeningSucceeded(authority())

        assertEquals(OpeningRoutingDestination.OPENING, result.destination)
        assertEquals(BootstrapFailure.Unavailable, result.failure)
    }

    @Test
    fun `capability refresh without a matching active opening never routes to cashier`() {
        val result = gate(
            current = { CurrentSessionResult.Success(opening()) },
            refresh = { RepositoryResult.Success(RepositoryState()) },
        ).afterOpeningSucceeded(authority())

        assertEquals(OpeningRoutingDestination.OPENING, result.destination)
    }

    private fun gate(
        current: (String) -> CurrentSessionResult,
        refresh: (BootstrapRefreshTrigger) -> RepositoryResult = { RepositoryResult.Success(routingState()) },
    ) = OpeningRoutingGate(
        currentSession = current,
        refreshCapabilities = refresh,
    )

    private fun authority(
        posProfile: String = "PROFILE-EXAMPLE",
        authenticationGeneration: Long = 7L,
        repositoryGeneration: Long = 11L,
    ) = OpeningRoutingAuthority(
        cashier = "cashier@example.test",
        posProfile = posProfile,
        authenticationGeneration = authenticationGeneration,
        repositoryGeneration = repositoryGeneration,
    )

    private fun opening(profile: String = "PROFILE-EXAMPLE") = OpeningSession(
        name = "OPENING-EXAMPLE-0001",
        posProfile = profile,
        company = "Example Company",
        user = "cashier@example.test",
        status = OpeningStatus.OPEN,
        postingDate = "2026-08-03",
        periodStartDate = "2026-08-03T08:00:00+07:00",
        openingBalances = emptyList(),
        warnings = emptyList(),
    )

    private fun routingState(opening: OpeningSession? = opening()) = RepositoryState(
        bootstrap = BootstrapData(
            user = BootstrapUser("cashier@example.test", "Cashier"),
            profiles = listOf(profile()),
            selectedProfile = profile(),
            opening = opening,
            capabilities = PosCapabilities(false, true, false, false, false),
            posMode = "POS Invoice",
        ),
    )

    private fun profile() = PosProfile(
        name = "PROFILE-EXAMPLE",
        company = "Example Company",
        warehouse = "WH-EXAMPLE",
        currency = "IDR",
        sellingPriceList = "Standard Selling",
        customer = "Walk In",
        allowPartialPayment = false,
        invoiceMode = "POS Invoice",
        openingPaymentModes = emptyList(),
        openingAmountPolicy = null,
    )
}
