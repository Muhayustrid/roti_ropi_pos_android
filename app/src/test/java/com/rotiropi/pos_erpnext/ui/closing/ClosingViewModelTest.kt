package com.rotiropi.pos_erpnext.ui.closing

import com.rotiropi.pos_erpnext.data.ClosingCountedAmountPolicy
import com.rotiropi.pos_erpnext.data.ClosingPayment
import com.rotiropi.pos_erpnext.data.ClosingPreview
import com.rotiropi.pos_erpnext.data.ClosingPreviewBinding
import com.rotiropi.pos_erpnext.data.ClosingReadResult
import com.rotiropi.pos_erpnext.data.ClosingReceipt
import com.rotiropi.pos_erpnext.data.ClosingReconciliation
import com.rotiropi.pos_erpnext.data.ExpectedClosingPayment
import com.rotiropi.pos_erpnext.data.OpeningSession
import com.rotiropi.pos_erpnext.data.OpeningStatus
import com.rotiropi.pos_erpnext.data.api.ClosingStatus
import com.rotiropi.pos_erpnext.data.api.SubmitClosingRequestDto
import com.rotiropi.pos_erpnext.recovery.RecoveryExecution
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClosingViewModelTest {
    @Test
    fun `preview exposes exact server modes and submits preview-bound counted strings`() {
        var submitted: SubmitClosingRequestDto? = null
        val viewModel = viewModel(
            submit = {
                submitted = it
                RecoveryExecution.Completed("123e4567-e89b-42d3-a456-426614174000")
            },
            completed = { receipt(ClosingStatus.SUBMITTED) },
        )

        viewModel.load("OUTLET-01")
        viewModel.updateCountedAmount("Cash", "69000.00")
        viewModel.updateCountedAmount("Bank", "1000.00")
        viewModel.submit()

        assertEquals("preview-1", submitted?.preview_id)
        assertEquals(listOf("Cash", "Bank"), submitted?.closing_balances?.map { it.mode_of_payment })
        assertEquals(listOf("69000.00", "1000.00"), submitted?.closing_balances?.map { it.closing_amount })
        assertTrue(viewModel.state.value is ClosingUiState.Receipt)
    }

    @Test
    fun `counted input rejects malformed scale bounds missing and unknown modes before mutation`() {
        var submissions = 0
        val viewModel = viewModel(submit = { submissions++; RecoveryExecution.BlockedIdentity })
        viewModel.load("OUTLET-01")

        viewModel.updateCountedAmount("Unknown", "1.00")
        assertEquals("payment_mode_unknown", (viewModel.state.value as ClosingUiState.Editing).error)
        viewModel.updateCountedAmount("Cash", "1.000")
        assertEquals("counted_amount_scale_exceeded", (viewModel.state.value as ClosingUiState.Editing).error)
        viewModel.updateCountedAmount("Cash", "1e2")
        assertEquals("counted_amount_malformed", (viewModel.state.value as ClosingUiState.Editing).error)
        viewModel.updateCountedAmount("Cash", "1000000000000.00")
        assertEquals("counted_amount_out_of_bounds", (viewModel.state.value as ClosingUiState.Editing).error)
        viewModel.updateCountedAmount("Cash", "1.00")
        viewModel.submit()

        assertEquals("counted_amount_required", (viewModel.state.value as ClosingUiState.Editing).error)
        assertEquals(0, submissions)
    }

    @Test
    fun `invalid replacement clears prior valid amount and blocks submit`() {
        var submissions = 0
        val viewModel = viewModel(submit = { submissions++; RecoveryExecution.BlockedIdentity })
        viewModel.load("OUTLET-01")
        viewModel.updateCountedAmount("Cash", "100.00")
        viewModel.updateCountedAmount("Bank", "100.00")

        viewModel.updateCountedAmount("Cash", "100.000")
        val invalid = viewModel.state.value as ClosingUiState.Editing
        assertEquals(null, invalid.countedAmounts["Cash"])
        assertEquals("counted_amount_scale_exceeded", invalid.error)

        viewModel.submit()

        val blocked = viewModel.state.value as ClosingUiState.Editing
        assertEquals(null, blocked.countedAmounts["Cash"])
        assertEquals("counted_amount_required", blocked.error)
        assertEquals(0, submissions)
    }

    @Test
    fun `late preview response cannot restore old profile authority`() {
        lateinit var viewModel: ClosingViewModel
        viewModel = viewModel(
            previewClosing = { _, _ ->
                viewModel.synchronizeAuthority(authority(posProfile = "OUTLET-02"))
                ClosingReadResult.Success(preview())
            },
        )

        viewModel.load("OUTLET-01")

        assertEquals(ClosingUiState.Unavailable, viewModel.state.value)
    }

    @Test
    fun `queued projection without local mutation polls authoritative status`() {
        val statuses = mutableListOf<String>()
        val viewModel = viewModel(
            status = { name, _ ->
                statuses += name
                ClosingReadResult.Success(receipt(ClosingStatus.QUEUED))
            },
        )

        viewModel.recoverProjection("CLOSING-1")

        assertTrue(viewModel.state.value is ClosingUiState.Queued)
        assertEquals(listOf("CLOSING-1"), statuses)
    }

    @Test
    fun `authority change clears queued and recovering Closing UI`() {
        val queued = viewModel(completed = { receipt(ClosingStatus.QUEUED) })
        queued.setForeground(false)
        queued.recover("123e4567-e89b-42d3-a456-426614174000")
        assertTrue(queued.state.value is ClosingUiState.Queued)

        queued.synchronizeAuthority(authority(posProfile = "OUTLET-02"))

        assertEquals(ClosingUiState.Unavailable, queued.state.value)

        val recovering = viewModel()
        recovering.recover("123e4567-e89b-42d3-a456-426614174000")
        assertTrue(recovering.state.value is ClosingUiState.Recovering)

        recovering.synchronizeAuthority(authority(posProfile = "OUTLET-02"))

        assertEquals(ClosingUiState.Unavailable, recovering.state.value)
    }

    @Test
    fun `late submit result cannot publish after authority change`() {
        lateinit var viewModel: ClosingViewModel
        viewModel = viewModel(
            submit = {
                viewModel.synchronizeAuthority(authority(posProfile = "OUTLET-02"))
                RecoveryExecution.Completed("123e4567-e89b-42d3-a456-426614174000")
            },
            completed = { receipt(ClosingStatus.SUBMITTED) },
        )
        viewModel.load("OUTLET-01")
        viewModel.updateCountedAmount("Cash", "100.00")
        viewModel.updateCountedAmount("Bank", "100.00")

        viewModel.submit()

        assertEquals(ClosingUiState.Unavailable, viewModel.state.value)
    }

    @Test
    fun `late projection result cannot publish after authority change`() {
        lateinit var viewModel: ClosingViewModel
        viewModel = viewModel(
            status = { _, _ ->
                viewModel.synchronizeAuthority(authority(posProfile = "OUTLET-02"))
                ClosingReadResult.Success(receipt(ClosingStatus.SUBMITTED))
            },
        )

        viewModel.recoverProjection("CLOSING-1")

        assertEquals(ClosingUiState.Unavailable, viewModel.state.value)
    }

    @Test
    fun `projection terminals refresh authoritative state once`() {
        var submittedRefreshes = 0
        val submitted = viewModel(
            status = { _, _ -> ClosingReadResult.Success(receipt(ClosingStatus.SUBMITTED)) },
            terminal = { submittedRefreshes++ },
        )

        submitted.recoverProjection("CLOSING-1")
        submitted.recoverProjection("CLOSING-1")

        assertEquals(1, submittedRefreshes)

        var cancelledRefreshes = 0
        val cancelled = viewModel(
            status = { _, _ -> ClosingReadResult.Success(receipt(ClosingStatus.CANCELLED)) },
            cancelled = { cancelledRefreshes++ },
        )

        cancelled.recoverProjection("CLOSING-2")
        cancelled.recoverProjection("CLOSING-2")

        assertEquals(1, cancelledRefreshes)
    }

    @Test
    fun `profile authority change clears preview and blocks stale submit`() {
        var submissions = 0
        val viewModel = viewModel(
            submit = {
                submissions++
                RecoveryExecution.BlockedIdentity
            },
        )
        viewModel.load("OUTLET-01")
        viewModel.updateCountedAmount("Cash", "100.00")
        viewModel.updateCountedAmount("Bank", "100.00")

        viewModel.synchronizeAuthority(authority(posProfile = "OUTLET-02"))
        viewModel.submit()

        assertEquals(ClosingUiState.Unavailable, viewModel.state.value)
        assertEquals(0, submissions)
    }

    @Test
    fun `queued completion persists authoritative status before receipt and terminal callback`() {
        val terminalBytes = "terminal-status-envelope".encodeToByteArray()
        val statuses = ArrayDeque(listOf(
            ClosingReadResult.Success(receipt(ClosingStatus.QUEUED)),
            ClosingReadResult.Success(receipt(ClosingStatus.SUBMITTED), terminalBytes),
        ))
        val delays = mutableListOf<Long>()
        val events = mutableListOf<String>()
        val viewModel = viewModel(
            completed = { receipt(ClosingStatus.QUEUED) },
            status = { _, _ -> statuses.removeFirst() },
            persistTerminal = { _, receipt, rawResponse ->
                assertEquals(terminalBytes.toList(), rawResponse.toList())
                events += "persist:${receipt.status}"
                true
            },
            delayMillis = { delays += it },
            terminal = { events += "terminal:${it.status}" },
        )

        viewModel.recover("123e4567-e89b-42d3-a456-426614174000")

        assertEquals(listOf(2_000L, 4_000L), delays)
        assertEquals(ClosingStatus.SUBMITTED, (viewModel.state.value as ClosingUiState.Receipt).receipt.status)
        assertEquals(listOf("persist:SUBMITTED", "terminal:SUBMITTED"), events)
    }

    @Test
    fun `queued status auth failure requires reauthentication instead of status retry`() {
        val viewModel = viewModel(
            completed = { receipt(ClosingStatus.QUEUED) },
            status = { _, _ -> ClosingReadResult.Failure("AUTH_REQUIRED") },
        )

        viewModel.recover("123e4567-e89b-42d3-a456-426614174000")

        val queued = viewModel.state.value as ClosingUiState.Queued
        assertEquals("AUTH_REQUIRED", queued.error)
        assertFalse(queued.checkStatusAvailable)
    }

    @Test
    fun `queued acceptance triggers authoritative capability refresh`() {
        var refreshes = 0
        val viewModel = viewModel(
            completed = { receipt(ClosingStatus.QUEUED) },
            queued = { refreshes++ },
        )

        viewModel.recover("123e4567-e89b-42d3-a456-426614174000")

        assertEquals(1, refreshes)
        assertTrue(viewModel.state.value is ClosingUiState.Queued)
    }

    @Test
    fun `cancelled terminal triggers authoritative capability refresh`() {
        var refreshes = 0
        val viewModel = viewModel(
            completed = { receipt(ClosingStatus.CANCELLED) },
            cancelled = { refreshes++ },
        )

        viewModel.recover("123e4567-e89b-42d3-a456-426614174000")

        assertEquals(1, refreshes)
        assertTrue(viewModel.state.value is ClosingUiState.Failed)
    }

    @Test
    fun `terminal status remains recoverable when durable persistence fails`() {
        var terminalCallbacks = 0
        val viewModel = viewModel(
            completed = { receipt(ClosingStatus.QUEUED) },
            status = { _, _ -> ClosingReadResult.Success(receipt(ClosingStatus.SUBMITTED), "terminal".encodeToByteArray()) },
            persistTerminal = { _, _, _ -> false },
            terminal = { terminalCallbacks++ },
        )

        viewModel.recover("123e4567-e89b-42d3-a456-426614174000")

        assertTrue(viewModel.state.value is ClosingUiState.Recovering)
        assertEquals(0, terminalCallbacks)
    }

    @Test
    fun `failed closing persists terminal receipt without successful close refresh`() {
        val events = mutableListOf<String>()
        val viewModel = viewModel(
            completed = { receipt(ClosingStatus.QUEUED) },
            status = { _, _ -> ClosingReadResult.Success(receipt(ClosingStatus.FAILED), "terminal".encodeToByteArray()) },
            persistTerminal = { _, receipt, _ -> events += "persist:${receipt.status}"; true },
            terminal = { events += "terminal:${it.status}" },
        )

        viewModel.recover("123e4567-e89b-42d3-a456-426614174000")

        assertTrue(viewModel.state.value is ClosingUiState.Failed)
        assertEquals(listOf("persist:FAILED"), events)
    }

    @Test
    fun `durable failed closing never invokes successful close refresh`() {
        var terminalCallbacks = 0
        val viewModel = viewModel(
            completed = { receipt(ClosingStatus.FAILED) },
            terminal = { terminalCallbacks++ },
        )

        viewModel.recover("123e4567-e89b-42d3-a456-426614174000")

        assertTrue(viewModel.state.value is ClosingUiState.Failed)
        assertEquals(0, terminalCallbacks)
    }

    @Test
    fun `repeated durable submitted recovery refreshes authoritative bootstrap once`() {
        var terminalCallbacks = 0
        val transactionId = "123e4567-e89b-42d3-a456-426614174000"
        val viewModel = viewModel(
            completed = { receipt(ClosingStatus.SUBMITTED) },
            terminal = { terminalCallbacks++ },
        )

        viewModel.recover(transactionId)
        viewModel.recover(transactionId)

        assertTrue(viewModel.state.value is ClosingUiState.Receipt)
        assertEquals(1, terminalCallbacks)
    }

    @Test
    fun `recovery synchronizer resumes each queued transaction once`() {
        val recovered = mutableListOf<String>()
        val synchronizer = ClosingRecoverySynchronizer { recovered += it }

        synchronizer.synchronize("transaction-1", authority())
        synchronizer.synchronize("transaction-1", authority())
        synchronizer.synchronize("transaction-2", authority())
        synchronizer.synchronize(null, authority())
        synchronizer.synchronize("transaction-2", authority())

        assertEquals(
            listOf("transaction-1", "transaction-2", "transaction-2"),
            recovered,
        )
    }

    @Test
    fun `recovery synchronizer rehydrates same queued transaction after authority change`() {
        val recovered = mutableListOf<String>()
        val synchronizer = ClosingRecoverySynchronizer { recovered += it }
        val profileA = authority()
        val profileB = authority(posProfile = "OUTLET-02")

        synchronizer.synchronize("transaction-1", profileA)
        synchronizer.synchronize("transaction-1", profileA)
        synchronizer.synchronize("transaction-1", profileB)
        synchronizer.synchronize("transaction-1", profileA)

        assertEquals(
            listOf("transaction-1", "transaction-1", "transaction-1"),
            recovered,
        )
    }

    @Test
    fun `recovery synchronizer keeps queued evidence when logout remains blocked`() {
        var queued: String? = "transaction-1"
        val synchronizer = ClosingRecoverySynchronizer {
            queued = it
        }

        synchronizer.synchronize(queued, authority())

        assertEquals("transaction-1", queued)
        assertEquals("transaction-1", synchronizer.currentTransactionId)
    }

    @Test
    fun `queued polling stops after bounded window and resumes only through check status`() {
        var calls = 0
        var now = 0L
        val delays = mutableListOf<Long>()
        val viewModel = viewModel(
            completed = { receipt(ClosingStatus.QUEUED) },
            status = { _, _ -> calls++; ClosingReadResult.Success(receipt(ClosingStatus.QUEUED)) },
            delayMillis = { delays += it; now += it },
            monotonicMillis = { now },
        )

        viewModel.recover("123e4567-e89b-42d3-a456-426614174000")

        val paused = viewModel.state.value as ClosingUiState.Queued
        assertFalse(paused.polling)
        assertTrue(paused.checkStatusAvailable)
        assertTrue(delays.sum() <= 300_000L)
        val before = calls
        viewModel.checkStatus()
        assertTrue(calls > before)
    }

    @Test
    fun `queued polling uses monotonic deadline instead of requested delay sum`() {
        var calls = 0
        var now = 0L
        val viewModel = viewModel(
            completed = { receipt(ClosingStatus.QUEUED) },
            status = { _, _ -> calls++; ClosingReadResult.Success(receipt(ClosingStatus.QUEUED)) },
            delayMillis = { now = 301_000L },
            monotonicMillis = { now },
        )

        viewModel.recover("123e4567-e89b-42d3-a456-426614174000")

        assertEquals(0, calls)
        assertFalse((viewModel.state.value as ClosingUiState.Queued).polling)
    }

    @Test
    fun `queued polling stops before status request outside foreground`() {
        var calls = 0
        lateinit var viewModel: ClosingViewModel
        viewModel = viewModel(
            completed = { receipt(ClosingStatus.QUEUED) },
            status = { _, _ -> calls++; ClosingReadResult.Success(receipt(ClosingStatus.QUEUED)) },
            delayMillis = { viewModel.setForeground(false) },
        )

        viewModel.recover("123e4567-e89b-42d3-a456-426614174000")

        assertEquals(0, calls)
        assertFalse((viewModel.state.value as ClosingUiState.Queued).polling)
    }

    @Test
    fun `receipt acknowledgement clears durable evidence and Closing UI`() {
        var acknowledged: String? = null
        val viewModel = viewModel(
            completed = { receipt(ClosingStatus.SUBMITTED) },
            acknowledge = { acknowledged = it; true },
        )
        val transactionId = "123e4567-e89b-42d3-a456-426614174000"
        viewModel.recover(transactionId)

        assertTrue(viewModel.closeReceipt())

        assertEquals(transactionId, acknowledged)
        assertEquals(ClosingUiState.Unavailable, viewModel.state.value)
    }

    @Test
    fun `receipt remains visible when durable acknowledgement fails`() {
        val viewModel = viewModel(
            completed = { receipt(ClosingStatus.SUBMITTED) },
            acknowledge = { false },
        )
        viewModel.recover("123e4567-e89b-42d3-a456-426614174000")

        assertFalse(viewModel.closeReceipt())

        assertTrue(viewModel.state.value is ClosingUiState.Receipt)
    }

    @Test
    fun `stale preview rejection clears old binding and reloads only on explicit retry`() {
        val viewModel = viewModel(
            submit = { RecoveryExecution.Rejected("123e4567-e89b-42d3-a456-426614174000") },
            rejected = { "CLOSING_PREVIEW_STALE" },
        )
        viewModel.load("OUTLET-01")
        viewModel.updateCountedAmount("Cash", "1.00")
        viewModel.updateCountedAmount("Bank", "1.00")

        viewModel.submit()

        assertEquals(ClosingUiState.StalePreview("OUTLET-01"), viewModel.state.value)
    }

    private fun viewModel(
        previewClosing: (String, com.rotiropi.pos_erpnext.data.api.ApiCallCancellation) -> ClosingReadResult<ClosingPreview> = { _, _ -> ClosingReadResult.Success(preview()) },
        submit: (SubmitClosingRequestDto) -> RecoveryExecution = { RecoveryExecution.BlockedIdentity },
        completed: (String) -> ClosingReceipt? = { null },
        rejected: (String) -> String? = { null },
        status: (String, com.rotiropi.pos_erpnext.data.api.ApiCallCancellation) -> ClosingReadResult<ClosingReceipt> = { _, _ -> ClosingReadResult.Failure("unused") },
        persistTerminal: (String, ClosingReceipt, ByteArray) -> Boolean = { _, _, _ -> true },
        delayMillis: suspend (Long) -> Unit = {},
        monotonicMillis: () -> Long = { 0L },
        queued: (ClosingReceipt) -> Unit = {},
        terminal: (ClosingReceipt) -> Unit = {},
        cancelled: (ClosingReceipt) -> Unit = {},
        acknowledge: (String) -> Boolean = { false },
    ) = ClosingViewModel(
        dispatcher = UnconfinedTestDispatcher(),
        previewClosing = previewClosing,
        submitClosing = submit,
        completedClosing = completed,
        rejectedClosing = rejected,
        closingStatus = status,
        persistTerminal = persistTerminal,
        delayMillis = delayMillis,
        monotonicMillis = monotonicMillis,
        onQueued = queued,
        onTerminal = terminal,
        onCancelled = cancelled,
        acknowledge = acknowledge,
    ).also { it.synchronizeAuthority(authority()) }

    private fun authority(
        posProfile: String = "OUTLET-01",
    ) = ClosingAuthority(
        cashier = "cashier@example.com",
        posProfile = posProfile,
        authenticationGeneration = 1L,
        repositoryGeneration = 1L,
    )

    private fun preview() = ClosingPreview(
        opening = OpeningSession("OPENING-1", "OUTLET-01", "Roti Ropi", "cashier@example.com", OpeningStatus.OPEN, "2026-08-07", "2026-08-07T08:00:00Z", emptyList(), emptyList()),
        previewId = "preview-1",
        previewVersion = "closing-preview/v1",
        binding = ClosingPreviewBinding("OPENING-1", "OUTLET-01", "cashier@example.com", 10, listOf("Cash", "Bank")),
        invoiceCount = 10,
        grandTotal = "100000.00",
        netTotal = "90909.09",
        totalQuantity = "10.00",
        totalTaxesAndCharges = "9090.91",
        expectedPayments = listOf(
            ExpectedClosingPayment("Cash", "10000.00", "70000.00"),
            ExpectedClosingPayment("Bank", "0.00", "1000.00"),
        ),
        countedAmountPolicy = ClosingCountedAmountPolicy("IDR", 2, 2, "ascii_decimal_dot", "0.00", "999999999999.99", "reject", "closing-counted-amount/v1"),
    )

    private fun receipt(status: ClosingStatus) = ClosingReceipt(
        name = "CLOSING-1",
        openingEntry = "OPENING-1",
        posProfile = "OUTLET-01",
        status = status,
        invoiceCount = 10,
        grandTotal = "100000.00",
        netTotal = "90909.09",
        totalQuantity = "10.00",
        totalTaxesAndCharges = "9090.91",
        payments = listOf(ClosingPayment("Cash", "10000.00", "70000.00", "69000.00", "-1000.00")),
        reconciliation = ClosingReconciliation("70000.00", "69000.00", "-1000.00"),
        failureCode = null,
        failureMessage = null,
    )
}
