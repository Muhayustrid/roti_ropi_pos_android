package com.rotiropi.pos_erpnext.ui.opening

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.ui.theme.PosTheme
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
        composeRule.onNodeWithTag("opening-submit").assertHasClickAction()
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
}
