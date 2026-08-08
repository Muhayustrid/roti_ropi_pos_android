package com.rotiropi.pos_erpnext.recovery

import com.rotiropi.pos_erpnext.auth.AuthenticationSnapshot
import com.rotiropi.pos_erpnext.auth.AuthenticationState
import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryViewModelTest {
    private val identityA = RecoveryIdentity("cashier-a", "https://example.test", "client")
    private val identityB = RecoveryIdentity("cashier-b", "https://example.test", "client")
    private val transactionId = "123e4567-e89b-42d3-a456-426614174000"

    @Test
    fun delayedCashierARefreshCannotPublishIntoCashierBSession() {
        var snapshot = AuthenticationSnapshot(1, AuthenticationState.Authenticated)
        var identity: RecoveryIdentity? = identityA
        var reads = 0
        val viewModel = viewModel(
            snapshot = { snapshot },
            identity = { identity },
            recovery = { RecoveryUiState(identityA, TerminalRecovery(transactionId, PendingMutationState.COMPLETED)) },
            read = { reads++; terminal(MobilePosEndpoint.SALES_SUBMIT, saleEnvelope()) },
        )
        val captured = snapshot

        snapshot = AuthenticationSnapshot(2, AuthenticationState.Authenticated)
        identity = identityB
        viewModel.refresh(captured, identityA)

        assertEquals(RecoveryScreenState.Hidden, viewModel.state.value)
        assertEquals(0, reads)
    }

    @Test
    fun authenticationTransitionClearsTerminalTokenAndBlocksStaleAcknowledgement() {
        var snapshot = AuthenticationSnapshot(1, AuthenticationState.Authenticated)
        var identity: RecoveryIdentity? = identityA
        var acknowledged = false
        val viewModel = viewModel(
            snapshot = { snapshot },
            identity = { identity },
            recovery = { RecoveryUiState(identityA, TerminalRecovery(transactionId, PendingMutationState.COMPLETED)) },
            read = { terminal(MobilePosEndpoint.SALES_SUBMIT, saleEnvelope()) },
            acknowledge = { acknowledged = true; RecoveryAcknowledgement.Acknowledged(it.transactionId) },
        )
        viewModel.refresh(snapshot, identityA)
        assertTrue(viewModel.state.value is RecoveryScreenState.Terminal)

        snapshot = AuthenticationSnapshot(2, AuthenticationState.Unauthenticated)
        identity = null
        viewModel.onAuthenticationChanged(snapshot)
        val result = viewModel.acknowledge()

        assertEquals(RecoveryScreenState.Hidden, viewModel.state.value)
        assertTrue(result is RecoveryAcknowledgement.NotAcknowledged)
        assertFalse(acknowledged)
    }

    @Test
    fun authenticationRequiredRecoveryPublishesExplicitReauthenticationState() {
        val snapshot = AuthenticationSnapshot(1, AuthenticationState.Authenticated)
        val viewModel = viewModel(
            snapshot = { snapshot },
            identity = { identityA },
            recovery = { RecoveryUiState(identityA, authenticationRequiredTransactionId = transactionId) },
        )

        viewModel.refresh(snapshot, identityA)

        assertEquals(RecoveryScreenState.AuthenticationRequired(transactionId), viewModel.state.value)
    }

    @Test
    fun schedulingFailureSurvivesRepeatedSameGenerationStateAndClearsOnNewGeneration() {
        var snapshot = AuthenticationSnapshot(1, AuthenticationState.Authenticated)
        val viewModel = viewModel(
            snapshot = { snapshot },
            identity = { identityA },
            recovery = { RecoveryUiState(identityA, retrySchedulingFailedTransactionId = transactionId) },
        )
        viewModel.refresh(snapshot, identityA)

        viewModel.onAuthenticationChanged(snapshot)
        assertEquals(RecoveryScreenState.RetrySchedulingFailed(transactionId), viewModel.state.value)

        snapshot = AuthenticationSnapshot(2, AuthenticationState.Authorizing)
        viewModel.onAuthenticationChanged(snapshot)
        assertEquals(RecoveryScreenState.Hidden, viewModel.state.value)
    }

    @Test
    fun malformedTerminalResultIsQuarantinedAndCannotBeAcknowledged() {
        var quarantined: String? = null
        val snapshot = AuthenticationSnapshot(1, AuthenticationState.Authenticated)
        val viewModel = viewModel(
            snapshot = { snapshot },
            identity = { identityA },
            recovery = { RecoveryUiState(identityA, TerminalRecovery(transactionId, PendingMutationState.COMPLETED)) },
            read = { terminal(MobilePosEndpoint.SALES_SUBMIT, "not-json") },
            quarantine = { quarantined = it },
        )

        viewModel.refresh(snapshot, identityA)

        assertEquals(transactionId, quarantined)
        assertTrue(viewModel.state.value is RecoveryScreenState.ManualRecovery)
        assertFalse((viewModel.state.value as RecoveryScreenState.ManualRecovery).canAcknowledge)
    }

    @Test
    fun mapsSafeCompletedResultsForAllMutationEndpoints() {
        val opening = RecoveryTerminalMapper.parse(terminal(MobilePosEndpoint.SESSIONS_OPEN, openingEnvelope()))
        val sale = RecoveryTerminalMapper.parse(terminal(MobilePosEndpoint.SALES_SUBMIT, saleEnvelope()))
        val returned = RecoveryTerminalMapper.parse(terminal(MobilePosEndpoint.SALES_CREATE_RETURN, returnEnvelope()))
        val closing = RecoveryTerminalMapper.parse(terminal(MobilePosEndpoint.CLOSING_SUBMIT, closingEnvelope()))

        assertEquals(RecoveryTerminalResult.Completed("Opening", "OPEN-1", "OPEN", null), opening)
        assertEquals(RecoveryTerminalResult.Completed("Sale", "SINV-1", "PAID", "100.00"), sale)
        assertEquals(RecoveryTerminalResult.Completed("Return", "SINV-RET-1", "RETURN", "-20.00"), returned)
        assertEquals(RecoveryTerminalResult.Completed("Closing", "CLOSE-1", "QUEUED", null), closing)
    }

    @Test
    fun mapsStableRejectionWithoutExposingArbitraryDetails() {
        val result = RecoveryTerminalMapper.parse(
            terminal(
                MobilePosEndpoint.SALES_SUBMIT,
                """{"message":{"ok":false,"data":null,"meta":{"api_version":"v1","request_id":"REQ-1","server_time":"now"},"error":{"code":"PRICE_CHANGED","message":"Review updated price","details":{"secret":"ignored"},"retryable":false}}}""",
                PendingMutationState.REJECTED,
            ),
        )

        assertEquals(RecoveryTerminalResult.Rejected("PRICE_CHANGED", "Review updated price", "REQ-1"), result)
    }

    private fun viewModel(
        snapshot: () -> AuthenticationSnapshot,
        identity: () -> RecoveryIdentity?,
        recovery: () -> RecoveryUiState,
        read: (String) -> ValidatedTerminalResult? = { null },
        acknowledge: (TerminalReadToken) -> RecoveryAcknowledgement = { RecoveryAcknowledgement.NotAcknowledged(it.transactionId) },
        quarantine: (String) -> Unit = {},
    ) = RecoveryViewModel(snapshot, identity, recovery, read, acknowledge, quarantine)

    private fun terminal(
        endpoint: MobilePosEndpoint,
        response: String,
        state: PendingMutationState = PendingMutationState.COMPLETED,
    ) = ValidatedTerminalResult(
        transactionId = transactionId,
        state = state,
        endpoint = endpoint,
        reference = "REQ-1",
        responseText = response,
        token = TerminalReadToken(transactionId, TERMINAL_RESULT_FORMAT_VERSION),
    )

    private fun openingEnvelope() = successEnvelope(
        """{"opening_session":{"name":"OPEN-1","pos_profile":"OUTLET","company":"Company","user":"cashier-a","status":"open","posting_date":"2026-08-02","period_start_date":"2026-08-02","opening_balances":[],"warnings":[]}}""",
    )

    private fun saleEnvelope() = successEnvelope(
        """{"sale":{"summary":{"doctype":"POS Invoice","name":"SINV-1","status":"paid","customer":"Walk In","walk_in_customer_name":null,"currency":"USD","grand_total":"100.00","paid_amount":"100.00","change_amount":"0.00","posting_date":"2026-08-02","posting_time":"10:00:00"},"items":[],"taxes":[],"payments":[]}}""",
    )

    private fun returnEnvelope() = successEnvelope(
        """{"return_sale":{"summary":{"doctype":"POS Invoice","name":"SINV-RET-1","status":"return","customer":"Walk In","walk_in_customer_name":null,"currency":"USD","grand_total":"-20.00","paid_amount":"-20.00","change_amount":"0.00","posting_date":"2026-08-02","posting_time":"10:00:00"},"items":[],"taxes":[],"payments":[]}}""",
    )

    private fun closingEnvelope() = successEnvelope(
        """{"closing":{"name":"CLOSE-1","opening_entry":"OPEN-1","pos_profile":"OUTLET","status":"queued","invoice_count":1,"grand_total":"100.00","net_total":"90.00","total_quantity":"1.00","total_taxes_and_charges":"10.00","payments":[{"mode_of_payment":"Cash","opening_amount":"0.00","expected_amount":"100.00","counted_amount":"100.00","difference":"0.00"}],"reconciliation":{"expected_total":"100.00","counted_total":"100.00","difference_total":"0.00"},"failure":null}}""",
    )

    private fun successEnvelope(data: String) =
        """{"message":{"ok":true,"data":$data,"meta":{"api_version":"v1","request_id":"REQ-1","server_time":"now"},"error":null}}"""
}
