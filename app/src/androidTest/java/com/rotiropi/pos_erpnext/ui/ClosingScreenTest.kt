package com.rotiropi.pos_erpnext.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.data.ClosingCountedAmountPolicy
import com.rotiropi.pos_erpnext.data.ClosingPayment
import com.rotiropi.pos_erpnext.data.ClosingPreview
import com.rotiropi.pos_erpnext.data.ClosingPreviewBinding
import com.rotiropi.pos_erpnext.data.ClosingReceipt
import com.rotiropi.pos_erpnext.data.ClosingReconciliation
import com.rotiropi.pos_erpnext.data.ExpectedClosingPayment
import com.rotiropi.pos_erpnext.data.OpeningSession
import com.rotiropi.pos_erpnext.data.OpeningStatus
import com.rotiropi.pos_erpnext.data.api.ClosingStatus
import com.rotiropi.pos_erpnext.ui.closing.ClosingScreen
import com.rotiropi.pos_erpnext.ui.closing.ClosingUiState
import com.rotiropi.pos_erpnext.ui.navigation.PosDestination
import com.rotiropi.pos_erpnext.ui.navigation.PosShell
import com.rotiropi.pos_erpnext.ui.theme.PosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClosingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun More_Closing_child_route_loads_preview_and_back_returns_to_More() {
        var loads = 0
        composeRule.setContent {
            PosTheme {
                PosShell(
                    startDestination = PosDestination.MORE,
                    closingAvailable = true,
                    closingState = ClosingUiState.Editing(preview()),
                    onOpenClosing = { loads++ },
                )
            }
        }

        composeRule.onNodeWithTag("more-closing").performScrollTo().performClick()
        composeRule.onNodeWithTag("closing-screen").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, loads) }
        composeRule.onNodeWithTag("closing-back").performClick()
        composeRule.onNodeWithTag("more-closing").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun preview_renders_authoritative_totals_and_exact_payment_modes() {
        val updates = mutableListOf<Pair<String, String>>()
        val state = androidx.compose.runtime.mutableStateOf(ClosingUiState.Editing(preview()))
        var submitted = 0
        composeRule.setContent {
            PosTheme {
                ClosingScreen(
                    state = state.value,
                    onCountedAmountChanged = { mode, value ->
                        updates += mode to value
                        state.value = state.value.copy(
                            countedAmounts = state.value.countedAmounts + (mode to value),
                        )
                    },
                    onSubmit = { submitted++ },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.closing_title)).assertIsDisplayed()
        composeRule.onNodeWithText("100000.00").assertIsDisplayed()
        composeRule.onNodeWithTag("closing-counted-cash").performTextInput("69000.00")
        composeRule.onNodeWithTag("closing-counted-bank").performTextInput("1000.00")
        composeRule.onNodeWithTag("closing-submit").assertIsEnabled()
        composeRule.runOnIdle {
            assertEquals("69000.00", updates.last { it.first == "Cash" }.second)
            assertEquals("1000.00", updates.last { it.first == "Bank" }.second)
            assertEquals(0, submitted)
        }
    }

    @Test
    fun editing_state_enables_submit_only_after_view_model_accepts_every_mode() {
        val state = androidx.compose.runtime.mutableStateOf<ClosingUiState>(
            ClosingUiState.Editing(preview()),
        )
        composeRule.setContent {
            PosTheme {
                ClosingScreen(
                    state = state.value,
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithTag("closing-submit").assertIsNotEnabled()
        composeRule.runOnIdle {
            state.value = ClosingUiState.Editing(
                preview(),
                mapOf("Cash" to "69000.00", "Bank" to "1000.00"),
            )
        }
        composeRule.onNodeWithTag("closing-submit").assertIsEnabled()
    }

    @Test
    fun submit_auth_failure_offers_reauthentication() {
        composeRule.setContent {
            PosTheme {
                ClosingScreen(
                    state = ClosingUiState.Recovering("auth_required"),
                )
            }
        }

        composeRule.onNodeWithTag("closing-reauthenticate")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun queued_auth_failure_offers_reauthentication_without_status_retry() {
        var reauthenticated = 0
        composeRule.setContent {
            PosTheme {
                ClosingScreen(
                    state = ClosingUiState.Queued(
                        "transaction",
                        receipt(ClosingStatus.QUEUED),
                        polling = false,
                        checkStatusAvailable = false,
                        error = "AUTH_REQUIRED",
                    ),
                    onReauthenticate = { reauthenticated++ },
                )
            }
        }

        composeRule.onNodeWithTag("closing-reauthenticate").performClick()
        composeRule.onNodeWithTag("closing-check-status").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, reauthenticated) }
    }

    @Test
    fun restored_terminal_receipt_opens_full_Closing_route() {
        composeRule.setContent {
            PosTheme {
                PosShell(
                    startDestination = PosDestination.MORE,
                    closingState = ClosingUiState.Receipt(
                        "transaction",
                        receipt(ClosingStatus.SUBMITTED),
                    ),
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.closing_submitted)).assertIsDisplayed()
        composeRule.onNodeWithTag("closing-done").assertIsDisplayed()
    }

    @Test
    fun failed_receipt_acknowledgement_keeps_Closing_route_visible() {
        composeRule.setContent {
            PosTheme {
                PosShell(
                    startDestination = PosDestination.MORE,
                    closingState = ClosingUiState.Receipt(
                        "transaction",
                        receipt(ClosingStatus.SUBMITTED),
                    ),
                    onCloseClosingReceipt = { false },
                )
            }
        }

        composeRule.onNodeWithTag("closing-done").performClick()

        composeRule.onNodeWithText(context.getString(R.string.closing_submitted)).assertIsDisplayed()
    }

    @Test
    fun queued_and_terminal_states_offer_only_safe_actions() {
        var checked = 0
        var finished = 0
        val state = androidx.compose.runtime.mutableStateOf<ClosingUiState>(
            ClosingUiState.Queued("transaction", receipt(ClosingStatus.QUEUED), false, true),
        )
        composeRule.setContent {
            PosTheme {
                ClosingScreen(
                    state = state.value,
                    onCheckStatus = { checked++ },
                    onDone = { finished++ },
                )
            }
        }

        composeRule.onNodeWithTag("closing-check-status")
            .assertIsEnabled()
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, checked)
            state.value = ClosingUiState.Receipt("transaction", receipt(ClosingStatus.SUBMITTED))
        }
        composeRule.onNodeWithText(context.getString(R.string.closing_submitted)).assertIsDisplayed()
        composeRule.onNodeWithTag("closing-done").performClick()
        composeRule.runOnIdle { assertEquals(1, finished) }
    }

    private fun preview() = ClosingPreview(
        opening = OpeningSession("OPENING-1", "OUTLET-01", "Roti Ropi", "cashier@example.test", OpeningStatus.OPEN, "2026-08-08", "2026-08-08T08:00:00Z", emptyList(), emptyList()),
        previewId = "preview-1",
        previewVersion = "closing-preview/v1",
        binding = ClosingPreviewBinding("OPENING-1", "OUTLET-01", "cashier@example.test", 10, listOf("Cash", "Bank")),
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
