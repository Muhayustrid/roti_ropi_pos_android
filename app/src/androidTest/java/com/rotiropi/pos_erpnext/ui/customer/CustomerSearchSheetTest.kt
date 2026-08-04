package com.rotiropi.pos_erpnext.ui.customer

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.ui.theme.PosTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomerSearchSheetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun walkInAndRegisteredSelectionAreAccessible() {
        var selected: CustomerRecord? = null
        rule.setContent {
            PosTheme {
                CustomerSearchSheet(
                    CustomerSearchUiState(customers = listOf(CustomerRecord("CUST-1", "Ayu Bakery", null, false)), selection = CustomerSelection.WalkIn("WALK", "")),
                    {}, {}, {}, { selected = it }, {}, {}, {},
                )
            }
        }
        rule.onNodeWithTag("customer-search-input").assertIsDisplayed()
        rule.onNodeWithTag("customer-walk-in-name").assertIsDisplayed()
        rule.onNodeWithTag("customer-CUST-1").assertHasClickAction().performClick()
        rule.runOnIdle { assert(selected?.id == "CUST-1") }
    }

    @Test fun loadingAndRetryStatesAreVisible() {
        rule.setContent {
            PosTheme { CustomerSearchSheet(CustomerSearchUiState(loading = true, error = CustomerSearchError.Unavailable), {}, {}, {}, {}, {}, {}, {}) }
        }
        rule.onNodeWithTag("customer-loading").assertIsDisplayed()
        rule.onNodeWithTag("customer-retry").assertIsDisplayed()
        rule.onNodeWithText("Customer").assertIsDisplayed()
    }

    @Test fun emptySearchStateIsVisible() {
        rule.setContent {
            PosTheme { CustomerSearchSheet(CustomerSearchUiState(), {}, {}, {}, {}, {}, {}, {}) }
        }

        rule.onNodeWithTag("customer-empty").assertIsDisplayed()
    }
}
