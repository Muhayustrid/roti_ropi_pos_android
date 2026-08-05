package com.rotiropi.pos_erpnext.session

import com.rotiropi.pos_erpnext.recovery.PendingMutationState
import com.rotiropi.pos_erpnext.recovery.RecoveryIdentity
import com.rotiropi.pos_erpnext.recovery.RecoveryLogoutBlocker
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryLogoutCoordinatorTest {
    private val cashier = RecoveryIdentity("cashier-1", "https://example.test", "client")

    @Test
    fun `logout blocks every persisted recovery state before cleanup`() {
        PendingMutationState.entries.forEach { state ->
            val events = mutableListOf<String>()
            val coordinator = coordinator(events) { RecoveryLogoutBlocker(cashier.cashier, state) }

            assertEquals(LogoutResult.Blocked(cashier.cashier, state), coordinator.logout())
            assertEquals(emptyList<String>(), events)
        }
    }

    @Test
    fun `logout without recovery record clears in existing order`() {
        val events = mutableListOf<String>()
        val coordinator = coordinator(events) { null }

        assertEquals(LogoutResult.LoggedOut, coordinator.logout())
        assertEquals(listOf("repository", "profile", "authentication"), events)
    }

    @Test
    fun `logout uses exact customer repository profile authentication order`() {
        val events = mutableListOf<String>()
        val coordinator = LogoutCoordinator(
            invalidateCustomerAuthority = { events += "customer-authority-invalidated" },
            cancelCustomerRequest = { events += "customer-request-cancelled" },
            clearCustomerUi = { events += "customer-ui-cleared" },
            clearRepository = { events += "repository" },
            clearProfileUi = { events += "profile" },
            clearAuthentication = { events += "authentication" },
            runCleanupIfNoRecovery = { cleanup -> cleanup(); null },
        )

        assertEquals(LogoutResult.LoggedOut, coordinator.logout())
        assertEquals(
            listOf(
                "customer-authority-invalidated",
                "customer-request-cancelled",
                "customer-ui-cleared",
                "repository",
                "profile",
                "authentication",
            ),
            events,
        )
    }

    @Test
    fun `blocked logout identifies owning cashier`() {
        val events = mutableListOf<String>()
        val coordinator = coordinator(events) {
            RecoveryLogoutBlocker("cashier-2", PendingMutationState.AUTH_REQUIRED)
        }

        assertEquals(
            LogoutResult.Blocked("cashier-2", PendingMutationState.AUTH_REQUIRED),
            coordinator.logout(),
        )
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `repeated logout remains blocked until terminal acknowledgement`() {
        val events = mutableListOf<String>()
        var blocker: RecoveryLogoutBlocker? = RecoveryLogoutBlocker(cashier.cashier, PendingMutationState.COMPLETED)
        val coordinator = coordinator(events) { blocker }

        assertEquals(LogoutResult.Blocked(cashier.cashier, PendingMutationState.COMPLETED), coordinator.logout())
        assertEquals(LogoutResult.Blocked(cashier.cashier, PendingMutationState.COMPLETED), coordinator.logout())
        blocker = null

        assertEquals(LogoutResult.LoggedOut, coordinator.logout())
        assertEquals(listOf("repository", "profile", "authentication"), events)
    }

    private fun coordinator(
        events: MutableList<String>,
        blocker: () -> RecoveryLogoutBlocker?,
    ) = LogoutCoordinator(
        clearRepository = { events += "repository" },
        clearProfileUi = { events += "profile" },
        clearAuthentication = { events += "authentication" },
        runCleanupIfNoRecovery = { cleanup ->
            blocker()?.let { it } ?: run {
                cleanup()
                null
            }
        },
    )
}
