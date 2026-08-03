package com.rotiropi.pos_erpnext.ui.opening

import com.rotiropi.pos_erpnext.data.OpeningAmountPolicy
import com.rotiropi.pos_erpnext.data.OpeningPaymentMode
import com.rotiropi.pos_erpnext.data.PosProfile
import com.rotiropi.pos_erpnext.data.api.OpenSessionRequestDto
import com.rotiropi.pos_erpnext.recovery.RecoveryExecution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningViewModelTest {
    @Test
    fun `server rows initialize in server order with suggested amounts and editable flags`() {
        val viewModel = OpeningViewModel("cashier@example.test", profile(), submit = { error("not called") })

        assertEquals(listOf("Mode B", "Mode A"), viewModel.state.value.rows.map { it.modeOfPayment })
        assertEquals(listOf("12.30", "0.00"), viewModel.state.value.rows.map { it.input })
        assertEquals(listOf(true, false), viewModel.state.value.rows.map { it.editable })
    }

    @Test
    fun `missing contract fails closed without fallback rows`() {
        val state = OpeningViewModel("cashier@example.test", profile(modes = emptyList()), submit = { error("not called") }).state.value

        assertTrue(state.unavailable)
        assertTrue(state.rows.isEmpty())
        assertFalse(state.canSubmit)
    }

    @Test
    fun `malformed server minimum fails closed instead of crashing`() {
        val invalidProfile = profile().copy(
            openingAmountPolicy = profile().openingAmountPolicy?.copy(minimum = "invalid"),
        )

        val state = OpeningViewModel("cashier@example.test", invalidProfile, submit = { error("not called") }).state.value

        assertTrue(state.unavailable)
        assertTrue(state.rows.isEmpty())
        assertFalse(state.canSubmit)
    }

    @Test
    fun `editable input accepts comma and submit receives canonical decimal-dot strings`() {
        var request: OpenSessionRequestDto? = null
        val viewModel = OpeningViewModel("cashier@example.test", profile(), submit = {
            request = it
            RecoveryExecution.WaitingRetry("123e4567-e89b-42d3-a456-426614174000")
        })

        viewModel.updateAmount("Mode B", "12,4")
        viewModel.submit()

        assertEquals("12.40", request!!.opening_balances[0].amount)
        assertEquals("0.00", request!!.opening_balances[1].amount)
        assertTrue(viewModel.state.value.recoveryPending)
    }

    @Test
    fun `invalid input cannot submit and immutable row ignores edits`() {
        var calls = 0
        val viewModel = OpeningViewModel("cashier@example.test", profile(), submit = { calls++; RecoveryExecution.BlockedIdentity })

        viewModel.updateAmount("Mode A", "99.00")
        assertEquals("0.00", viewModel.state.value.rows[1].input)
        viewModel.updateAmount("Mode B", "1.001")
        viewModel.submit()

        assertEquals(0, calls)
        assertFalse(viewModel.state.value.canSubmit)
        assertTrue(viewModel.state.value.rows[0].error != null)
    }

    @Test
    fun `double submit creates one logical mutation`() {
        var calls = 0
        lateinit var viewModel: OpeningViewModel
        viewModel = OpeningViewModel("cashier@example.test", profile(), submit = {
            calls++
            viewModel.submit()
            RecoveryExecution.WaitingRetry("123e4567-e89b-42d3-a456-426614174000")
        })

        viewModel.submit()

        assertEquals(1, calls)
    }

    @Test
    fun `authentication and retry results remain recoverable while terminal local rejection allows correction`() {
        val outcomes = ArrayDeque<RecoveryExecution>(
            listOf(
                RecoveryExecution.AuthRequired,
                RecoveryExecution.RetrySchedulingFailed("123e4567-e89b-42d3-a456-426614174000"),
                RecoveryExecution.Rejected("123e4567-e89b-42d3-a456-426614174000"),
            )
        )
        val viewModel = OpeningViewModel("cashier@example.test", profile(), submit = { outcomes.removeFirst() })

        viewModel.submit()
        assertTrue(viewModel.state.value.authenticationRequired)
        viewModel.allowResubmitAfterTerminalReconciliation()
        viewModel.submit()
        assertTrue(viewModel.state.value.recoveryPending)
        viewModel.allowResubmitAfterTerminalReconciliation()
        viewModel.submit()
        assertFalse(viewModel.state.value.submitting)
        assertFalse(viewModel.state.value.recoveryPending)
        assertTrue(viewModel.state.value.error != null)
    }

    @Test
    fun `opening input belongs only to its cashier and profile identity`() {
        val viewModel = OpeningViewModel("cashier-a@example.test", profile(), submit = { RecoveryExecution.AuthRequired })

        assertTrue(viewModel.belongsTo("cashier-a@example.test", "PROFILE-EXAMPLE"))
        assertFalse(viewModel.belongsTo("cashier-b@example.test", "PROFILE-EXAMPLE"))
        assertFalse(viewModel.belongsTo("cashier-a@example.test", "OTHER-PROFILE"))
    }

    @Test
    fun `clear removes opening inputs and status`() {
        val viewModel = OpeningViewModel("cashier@example.test", profile(), submit = { RecoveryExecution.AuthRequired })
        viewModel.updateAmount("Mode B", "99.00")
        viewModel.submit()

        viewModel.clear()

        assertTrue(viewModel.state.value.rows.isEmpty())
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.authenticationRequired)
    }

    private fun profile(
        modes: List<OpeningPaymentMode> = listOf(
            OpeningPaymentMode("Mode B", "12.30", true),
            OpeningPaymentMode("Mode A", "0.00", false),
        ),
    ) = PosProfile(
        name = "PROFILE-EXAMPLE",
        company = "Example Company",
        warehouse = "Example Warehouse",
        currency = "CUR",
        sellingPriceList = "Example Price List",
        customer = "Example Customer",
        allowPartialPayment = false,
        invoiceMode = "POS Invoice",
        openingPaymentModes = modes,
        openingAmountPolicy = OpeningAmountPolicy("CUR", 2, "0.00", "ascii_decimal_dot", "reject", "opening-amount/v1"),
    )
}
