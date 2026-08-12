package com.rotiropi.pos_erpnext.ui.opening

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.ui.theme.PosTheme
import com.rotiropi.pos_erpnext.ui.theme.WarmCommerceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpeningScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun configured_rows_render_in_server_order_and_submit_is_accessible() {
        composeRule.setContent {
            PosTheme {
                OpeningScreen(
                    state = OpeningUiState(
                        profileName = "PROFILE-EXAMPLE",
                        currency = "CUR",
                        rows = listOf(
                            OpeningRowUiState("Mode B", "12.30", true),
                            OpeningRowUiState("Mode A", "0.00", false),
                        ),
                        canSubmit = true,
                    ),
                    onAmountChanged = { _, _ -> },
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithText("Mode B").assertIsDisplayed()
        composeRule.onNodeWithText("Mode A").assertIsDisplayed()
        composeRule.onNodeWithTag("opening-submit").assertHasClickAction().assertIsEnabled()
    }

    @Test
    fun zero_amount_replaces_with_first_typed_digit() {
        var changedValue: String? = null
        composeRule.setContent {
            PosTheme {
                OpeningScreen(
                    state = OpeningUiState(
                        profileName = "PROFILE-EXAMPLE",
                        rows = listOf(OpeningRowUiState("Cash", "0.00", true)),
                        canSubmit = true,
                    ),
                    onAmountChanged = { _, value -> changedValue = value },
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithTag("opening-amount-Cash").assertTextContains("0").performTextInput("1")

        assertEquals("1", changedValue)
    }

    @Test
    fun amount_display_groups_integer_and_preserves_fraction_digits() {
        composeRule.setContent {
            PosTheme {
                OpeningScreen(
                    state = OpeningUiState(
                        profileName = "PROFILE-EXAMPLE",
                        rows = listOf(OpeningRowUiState("Cash", "10000,50", true)),
                        canSubmit = true,
                    ),
                    onAmountChanged = { _, _ -> },
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithTag("opening-amount-Cash").assertTextContains("10.000,50")
    }

    @Test
    fun non_editable_amount_is_locked_and_recovery_blocks_submission() {
        composeRule.setContent {
            PosTheme {
                OpeningScreen(
                    state = OpeningUiState(
                        profileName = "PROFILE-EXAMPLE",
                        rows = listOf(OpeningRowUiState("Cash", "12.30", false)),
                        canSubmit = true,
                        recoveryPending = true,
                    ),
                    recoveryState = com.rotiropi.pos_erpnext.recovery.RecoveryScreenState.AuthenticationRequired("transaction-1"),
                    onAmountChanged = { _, _ -> },
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithTag("opening-amount-Cash").assertIsNotEnabled()
        composeRule.onNodeWithTag("opening-submit").assertIsNotEnabled()
    }

    @Test
    fun missing_contract_displays_unavailable_without_fallback_inputs() {
        composeRule.setContent {
            PosTheme {
                OpeningScreen(
                    state = OpeningUiState(unavailable = true, error = "Opening configuration is unavailable."),
                    onAmountChanged = { _, _ -> },
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithTag("opening-unavailable").assertIsDisplayed()
        composeRule.onAllNodesWithText("Cash").assertCountEquals(0)
        composeRule.onNodeWithTag("opening-submit").assertDoesNotExist()
    }

    @Test
    fun recovery_authentication_action_remains_visible_during_opening() {
        composeRule.setContent {
            PosTheme {
                OpeningScreen(
                    state = OpeningUiState(
                        profileName = "PROFILE-EXAMPLE",
                        rows = listOf(OpeningRowUiState("Mode B", "12.30", true)),
                        canSubmit = false,
                        recoveryPending = true,
                    ),
                    recoveryState = com.rotiropi.pos_erpnext.recovery.RecoveryScreenState.AuthenticationRequired("transaction-1"),
                    onAmountChanged = { _, _ -> },
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithTag("recovery-reauthenticate").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun narrow_phone_content_remains_scrollable() {
        composeRule.setContent {
            PosTheme {
                OpeningScreen(
                    state = OpeningUiState(
                        profileName = "PROFILE-EXAMPLE",
                        currency = "CUR",
                        rows = (1..8).map { OpeningRowUiState("Mode $it", "10000,50", true) },
                        canSubmit = true,
                    ),
                    onAmountChanged = { _, _ -> },
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithTag("opening-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("opening-submit").assertIsDisplayed()
    }

    @Test
    fun large_font_content_keeps_submit_action_present() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.5f)) {
                PosTheme {
                    OpeningScreen(
                        state = OpeningUiState(
                            profileName = "PROFILE-EXAMPLE",
                            currency = "CUR",
                            rows = listOf(OpeningRowUiState("Cash", "10000,50", true)),
                            canSubmit = true,
                        ),
                        onAmountChanged = { _, _ -> },
                        onSubmit = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("opening-submit").assertIsDisplayed()
    }

    @Test
    fun invalid_state_disables_submission() {
        composeRule.setContent {
            PosTheme {
                OpeningScreen(
                    state = OpeningUiState(
                        profileName = "PROFILE-EXAMPLE",
                        rows = listOf(OpeningRowUiState("Mode B", "1.001", true, "Use at most 2 decimal places.")),
                        canSubmit = false,
                    ),
                    onAmountChanged = { _, _ -> },
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithText("Use at most 2 decimal places.").assertIsDisplayed()
        composeRule.onNodeWithTag("opening-submit").assertIsNotEnabled()
    }

    @Test
    fun start_shift_first_tap_opens_confirmation_without_submitting() {
        var submitted = 0
        composeRule.setContent {
            WarmCommerceTheme {
                OpeningScreen(
                    state = OpeningUiState(
                        profileName = "PROFILE-EXAMPLE",
                        cashier = "cashier@example.test",
                        rows = listOf(OpeningRowUiState("Cash", "200000", true)),
                        canSubmit = true,
                    ),
                    onAmountChanged = { _, _ -> },
                    onSubmit = { submitted++ },
                )
            }
        }

        composeRule.onNodeWithTag("opening-submit").performClick()
        awaitConfirmSheet()

        composeRule.onNodeWithTag("opening-confirm-modal").assertIsDisplayed()
        composeRule.onNodeWithTag("opening-confirm").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("opening-edit-amounts").assertIsDisplayed()
        assertEquals(0, submitted)
    }

    @Test
    fun final_confirmation_submits_exactly_once() {
        var submitted = 0
        composeRule.setContent {
            WarmCommerceTheme {
                OpeningScreen(
                    state = OpeningUiState(
                        profileName = "PROFILE-EXAMPLE",
                        rows = listOf(OpeningRowUiState("Cash", "200000", true)),
                        canSubmit = true,
                    ),
                    onAmountChanged = { _, _ -> },
                    onSubmit = { submitted++ },
                )
            }
        }

        composeRule.onNodeWithTag("opening-submit").performClick()
        awaitConfirmSheet()
        composeRule.onNodeWithTag("opening-confirm").performClick()

        assertEquals(1, submitted)
    }

    @Test
    fun submitting_disables_repeated_confirmation() {
        var submitting by mutableStateOf(false)
        composeRule.setContent {
            WarmCommerceTheme {
                OpeningScreen(
                    state = OpeningUiState(
                        profileName = "PROFILE-EXAMPLE",
                        rows = listOf(OpeningRowUiState("Cash", "200000", true)),
                        canSubmit = true,
                        submitting = submitting,
                    ),
                    onAmountChanged = { _, _ -> },
                    onSubmit = { submitting = true },
                )
            }
        }

        composeRule.onNodeWithTag("opening-submit").performClick()
        awaitConfirmSheet()
        composeRule.onNodeWithTag("opening-confirm").assertIsEnabled()
        composeRule.onNodeWithTag("opening-confirm").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("opening-confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("opening-submitting").assertIsDisplayed()
    }

    @Test
    fun edit_amounts_dismisses_confirmation_without_submitting() {
        var submitted = 0
        composeRule.setContent {
            WarmCommerceTheme {
                OpeningScreen(
                    state = OpeningUiState(
                        profileName = "PROFILE-EXAMPLE",
                        rows = listOf(OpeningRowUiState("Cash", "200000", true)),
                        canSubmit = true,
                    ),
                    onAmountChanged = { _, _ -> },
                    onSubmit = { submitted++ },
                )
            }
        }

        composeRule.onNodeWithTag("opening-submit").performClick()
        awaitConfirmSheet()
        composeRule.onNodeWithTag("opening-edit-amounts").performClick()
        composeRule.waitForIdle()

        assertEquals(0, submitted)
        composeRule.onNodeWithTag("opening-confirm-modal").assertDoesNotExist()
    }

    @Test
    fun session_details_render_profile_read_only() {
        composeRule.setContent {
            WarmCommerceTheme {
                OpeningScreen(
                    state = OpeningUiState(
                        profileName = "PROFILE-EXAMPLE",
                        cashier = "cashier@example.test",
                        company = "Example Company",
                        warehouse = "Example Warehouse",
                        currency = "CUR",
                        rows = listOf(OpeningRowUiState("Cash", "200000", true)),
                        canSubmit = true,
                    ),
                    onAmountChanged = { _, _ -> },
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithText("PROFILE-EXAMPLE").assertIsDisplayed()
        composeRule.onNodeWithText("cashier@example.test").assertIsDisplayed()
        composeRule.onNodeWithText("Example Company").assertIsDisplayed()
        // The POS Profile is text only; there is no editable profile input on this screen.
        composeRule.onAllNodesWithText("PROFILE-EXAMPLE").assertCountEquals(1)
    }

    @Test
    fun recovery_card_offers_no_replacement_opening() {
        composeRule.setContent {
            WarmCommerceTheme {
                OpeningScreen(
                    state = OpeningUiState(
                        profileName = "PROFILE-EXAMPLE",
                        rows = listOf(OpeningRowUiState("Cash", "200000", true)),
                        canSubmit = false,
                        recoveryPending = true,
                    ),
                    recoveryState = com.rotiropi.pos_erpnext.recovery.RecoveryScreenState.ManualRecovery(
                        transactionId = "transaction-1",
                        message = "Opening result needs recovery.",
                    ),
                    onAmountChanged = { _, _ -> },
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithTag("recovery-card").assertIsDisplayed()
        composeRule.onNodeWithText("Replace").assertDoesNotExist()
        composeRule.onNodeWithText("New Opening").assertDoesNotExist()
        composeRule.onNodeWithText("Start Over").assertDoesNotExist()
    }

    private fun awaitConfirmSheet() {
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onAllNodesWithTag("opening-confirm").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()
    }
}
