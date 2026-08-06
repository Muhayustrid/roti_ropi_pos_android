package com.rotiropi.pos_erpnext.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.ui.navigation.PosDestination
import com.rotiropi.pos_erpnext.ui.navigation.PosShell
import com.rotiropi.pos_erpnext.ui.opening.OpeningUiState
import com.rotiropi.pos_erpnext.ui.theme.PosTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Task4RootOpeningRoutingTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun reconciliationLoadingKeepsAuthenticatedShellUnavailable() {
        composeRule.setContent {
            PosTheme {
                PosShell(openingState = OpeningUiState(reconciling = true))
            }
        }

        composeRule.onNodeWithTag("opening-reconciling").assertIsDisplayed()
        composeRule.onNodeWithTag("root-cashier").assertDoesNotExist()
    }

    @Test
    fun reconciledOpeningEntersCashierDestination() {
        composeRule.setContent {
            PosTheme {
                PosShell(startDestination = PosDestination.CASHIER)
            }
        }

        composeRule.onNodeWithTag("destination-content-cashier").assertIsDisplayed()
    }
}
