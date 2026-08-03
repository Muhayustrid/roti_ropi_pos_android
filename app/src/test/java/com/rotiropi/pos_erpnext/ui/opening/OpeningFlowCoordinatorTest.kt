package com.rotiropi.pos_erpnext.ui.opening

import com.rotiropi.pos_erpnext.data.BootstrapRefreshTrigger
import com.rotiropi.pos_erpnext.data.CurrentSessionResult
import com.rotiropi.pos_erpnext.data.OpeningAmountPolicy
import com.rotiropi.pos_erpnext.data.OpeningPaymentMode
import com.rotiropi.pos_erpnext.data.OpeningSession
import com.rotiropi.pos_erpnext.data.OpeningStatus
import com.rotiropi.pos_erpnext.data.PosProfile
import com.rotiropi.pos_erpnext.data.api.OpenSessionRequestDto
import com.rotiropi.pos_erpnext.recovery.RecoveryExecution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningFlowCoordinatorTest {
    @Test
    fun `no current session shows opening flow while existing session bypasses it`() {
        val absent = coordinator(current = CurrentSessionResult.Success(null))
        assertEquals(OpeningDestination.OPENING, absent.reconcile())

        val present = coordinator(current = CurrentSessionResult.Success(opening()))
        assertEquals(OpeningDestination.SHELL, present.reconcile())
    }

    @Test
    fun `immediate opening success reconciles and refreshes capabilities exactly once`() {
        var currentCalls = 0
        var submitCalls = 0
        val triggers = mutableListOf<BootstrapRefreshTrigger>()
        val flow = coordinator(
            currentProvider = {
                currentCalls++
                CurrentSessionResult.Success(opening())
            },
            submit = {
                submitCalls++
                RecoveryExecution.Completed("123e4567-e89b-42d3-a456-426614174000")
            },
            refresh = { trigger -> triggers += trigger },
        )

        val execution = RecoveryExecution.Completed("123e4567-e89b-42d3-a456-426614174000")
        val result = flow.handleExecution(execution)

        assertEquals(OpeningDestination.SHELL, result.destination)
        assertEquals(0, submitCalls)
        assertEquals(1, currentCalls)
        assertEquals(listOf(BootstrapRefreshTrigger.OPENING_COMPLETED), triggers)
    }

    @Test
    fun `immediate rejection waits for typed terminal result before reconciliation`() {
        var currentCalls = 0
        val triggers = mutableListOf<BootstrapRefreshTrigger>()
        val flow = coordinator(
            currentProvider = {
                currentCalls++
                CurrentSessionResult.Success(opening())
            },
            submit = { RecoveryExecution.Rejected("123e4567-e89b-42d3-a456-426614174000") },
            refresh = { trigger -> triggers += trigger },
        )

        val result = flow.submit(OpenSessionRequestDto("PROFILE-EXAMPLE", emptyList()))

        assertEquals(OpeningDestination.OPENING, result.destination)
        assertFalse(result.reconciled)
        assertEquals(0, currentCalls)
        assertTrue(triggers.isEmpty())
    }

    @Test
    fun `retry and authentication outcomes stay in opening flow and do not refresh`() {
        val triggers = mutableListOf<BootstrapRefreshTrigger>()
        listOf<RecoveryExecution>(
            RecoveryExecution.WaitingRetry("123e4567-e89b-42d3-a456-426614174000"),
            RecoveryExecution.AuthRequired,
            RecoveryExecution.RetrySchedulingFailed("123e4567-e89b-42d3-a456-426614174000"),
        ).forEach { outcome ->
            val result = coordinator(
                submit = { outcome },
                refresh = { trigger -> triggers += trigger },
            ).submit(OpenSessionRequestDto("PROFILE-EXAMPLE", emptyList()))
            assertEquals(OpeningDestination.OPENING, result.destination)
            assertFalse(result.reconciled)
        }
        assertTrue(triggers.isEmpty())
    }

    @Test
    fun `recovered terminal success refreshes once per transaction across repeated observations`() {
        val triggers = mutableListOf<BootstrapRefreshTrigger>()
        var currentCalls = 0
        val flow = coordinator(
            currentProvider = {
                currentCalls++
                CurrentSessionResult.Success(opening())
            },
            refresh = { trigger -> triggers += trigger },
        )

        assertEquals(OpeningDestination.SHELL, flow.onRecoveredOpening("transaction-1").destination)
        assertEquals(OpeningDestination.SHELL, flow.onRecoveredOpening("transaction-1").destination)

        assertEquals(1, currentCalls)
        assertEquals(listOf(BootstrapRefreshTrigger.OPENING_COMPLETED), triggers)
    }

    @Test
    fun `recovered rejected session-already-open reconciles and refreshes once`() {
        val triggers = mutableListOf<BootstrapRefreshTrigger>()
        val flow = coordinator(
            current = CurrentSessionResult.Success(opening()),
            refresh = { trigger -> triggers += trigger },
        )

        assertEquals(OpeningDestination.SHELL, flow.onRecoveredRejection("transaction-1", "SESSION_ALREADY_OPEN").destination)
        assertEquals(OpeningDestination.SHELL, flow.onRecoveredRejection("transaction-1", "SESSION_ALREADY_OPEN").destination)
        assertEquals(listOf(BootstrapRefreshTrigger.OPENING_COMPLETED), triggers)
    }

    private fun coordinator(
        current: CurrentSessionResult = CurrentSessionResult.Success(null),
        currentProvider: (() -> CurrentSessionResult)? = null,
        submit: (OpenSessionRequestDto) -> RecoveryExecution = { RecoveryExecution.WaitingRetry("transaction") },
        refresh: (BootstrapRefreshTrigger) -> Unit = {},
    ) = OpeningFlowCoordinator(
        currentSession = currentProvider ?: { current },
        submitOpening = submit,
        refreshCapabilities = refresh,
    )

    private fun opening() = OpeningSession(
        name = "OPENING-EXAMPLE-0001",
        posProfile = "PROFILE-EXAMPLE",
        company = "Example Company",
        user = "cashier@example.test",
        status = OpeningStatus.OPEN,
        postingDate = "2026-08-03",
        periodStartDate = "2026-08-03T08:00:00+07:00",
        openingBalances = emptyList(),
        warnings = emptyList(),
    )
}
