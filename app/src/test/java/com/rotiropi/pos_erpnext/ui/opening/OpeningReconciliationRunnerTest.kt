package com.rotiropi.pos_erpnext.ui.opening

import com.rotiropi.pos_erpnext.data.BootstrapRefreshTrigger
import com.rotiropi.pos_erpnext.data.CurrentSessionResult
import com.rotiropi.pos_erpnext.data.OpeningSession
import com.rotiropi.pos_erpnext.data.OpeningStatus
import com.rotiropi.pos_erpnext.data.api.OpenSessionRequestDto
import com.rotiropi.pos_erpnext.recovery.RecoveryExecution
import com.rotiropi.pos_erpnext.recovery.RecoveryIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningReconciliationRunnerTest {
    @Test
    fun `recovered success dispatches reconciliation off caller thread`() {
        val caller = Thread.currentThread()
        var reconciliationThread: Thread? = null
        val runner = runner(
            currentSession = {
                reconciliationThread = Thread.currentThread()
                CurrentSessionResult.Success(opening())
            },
        )

        runner.recovered(completed("transaction-1"))

        assertNotSame(caller, reconciliationThread)
    }

    @Test
    fun `immediate and recovered successes reconcile once and refresh once`() {
        var currentCalls = 0
        var refreshCalls = 0
        var resultCalls = 0
        val runner = OpeningReconciliationRunner(
            flow = OpeningFlowCoordinator(
                currentSession = {
                    currentCalls++
                    CurrentSessionResult.Success(opening())
                },
                submitOpening = { RecoveryExecution.WaitingRetry("unused") },
                refreshCapabilities = { refreshCalls++ },
            ),
            dispatch = { it() },
            isCurrent = { true },
            onResult = { _, _ -> resultCalls++ },
        )
        val immediate = RecoveryExecution.Completed("immediate")

        runner.immediate(immediate)
        runner.immediate(immediate)
        runner.recovered(completed("recovered"))
        runner.recovered(completed("recovered"))

        assertEquals(2, currentCalls)
        assertEquals(2, refreshCalls)
        assertEquals(2, resultCalls)
    }

    @Test
    fun `reconciliation failure surfaces once without refresh`() {
        var refreshCalls = 0
        val results = mutableListOf<OpeningFlowResult>()
        val runner = OpeningReconciliationRunner(
            flow = OpeningFlowCoordinator(
                currentSession = { CurrentSessionResult.Failure(com.rotiropi.pos_erpnext.data.BootstrapFailure.Unavailable) },
                submitOpening = { RecoveryExecution.WaitingRetry("unused") },
                refreshCapabilities = { refreshCalls++ },
            ),
            dispatch = { it() },
            isCurrent = { true },
            onResult = { _, result -> results += result },
        )

        runner.recovered(completed("transaction-1"))
        runner.recovered(completed("transaction-1"))

        assertEquals(1, results.size)
        assertEquals(com.rotiropi.pos_erpnext.data.BootstrapFailure.Unavailable, results.single().failure)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun `identity change during reconciliation suppresses capability refresh and publication`() {
        var current = true
        var refreshCalls = 0
        var resultCalls = 0
        val runner = OpeningReconciliationRunner(
            flow = OpeningFlowCoordinator(
                currentSession = {
                    current = false
                    CurrentSessionResult.Success(opening())
                },
                submitOpening = { RecoveryExecution.WaitingRetry("unused") },
                refreshCapabilities = { refreshCalls++ },
            ),
            dispatch = { it() },
            isCurrent = { current },
            onResult = { _, _ -> resultCalls++ },
        )

        runner.recovered(completed("transaction-1"))

        assertEquals(0, refreshCalls)
        assertEquals(0, resultCalls)
    }

    @Test
    fun `stale identity or generation does not reconcile or publish`() {
        var currentCalls = 0
        var resultCalls = 0
        val expected = completed("transaction-1")
        var current = true
        val runner = OpeningReconciliationRunner(
            flow = OpeningFlowCoordinator(
                currentSession = {
                    currentCalls++
                    CurrentSessionResult.Success(opening())
                },
                submitOpening = { RecoveryExecution.WaitingRetry("unused") },
                refreshCapabilities = {},
            ),
            dispatch = { action ->
                current = false
                action()
            },
            isCurrent = { current && it.identity == expected.identity && it.generation == expected.generation },
            onResult = { _, _ -> resultCalls++ },
        )

        runner.recovered(expected)

        assertEquals(0, currentCalls)
        assertEquals(0, resultCalls)
    }

    private fun runner(
        currentSession: () -> CurrentSessionResult = { CurrentSessionResult.Success(opening()) },
        refresh: (BootstrapRefreshTrigger) -> Unit = {},
        dispatch: ((() -> Unit) -> Unit) = { action ->
            Thread(action, "opening-reconciliation-test").apply { start(); join() }
        },
    ) = OpeningReconciliationRunner(
        flow = OpeningFlowCoordinator(
            currentSession = currentSession,
            submitOpening = { RecoveryExecution.WaitingRetry("transaction") },
            refreshCapabilities = refresh,
        ),
        dispatch = dispatch,
        isCurrent = { true },
        onResult = { _, _ -> },
    )

    private fun completed(transactionId: String) = RecoveredOpeningTerminal.Completed(
        identity = RecoveryIdentity("cashier@example.test", ORIGIN, CLIENT_ID),
        generation = 7L,
        transactionId = transactionId,
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

    private companion object {
        const val ORIGIN = "https://example.test"
        const val CLIENT_ID = "client"
    }
}
