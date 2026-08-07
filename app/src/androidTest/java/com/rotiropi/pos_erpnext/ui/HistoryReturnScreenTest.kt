package com.rotiropi.pos_erpnext.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.data.api.PageDto
import com.rotiropi.pos_erpnext.data.api.ReturnabilityDto
import com.rotiropi.pos_erpnext.data.api.SaleStatus
import com.rotiropi.pos_erpnext.data.api.SaleSummaryDto
import com.rotiropi.pos_erpnext.ui.history.HistoryScreen
import com.rotiropi.pos_erpnext.ui.history.HistoryUiState
import com.rotiropi.pos_erpnext.ui.returning.ReturnQuantityPolicy
import com.rotiropi.pos_erpnext.ui.returning.ReturnScreen
import com.rotiropi.pos_erpnext.ui.returning.ReturnUiState
import com.rotiropi.pos_erpnext.ui.theme.PosTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryReturnScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun history_uses_bounded_rows_and_exposes_load_more() {
        composeRule.setContent { PosTheme { HistoryScreen(HistoryUiState.Content(listOf(summary()), "", false, true), {}, {}, {}, {}) } }
        composeRule.onNodeWithTag("history-sale-INV-1").assertHasClickAction()
        composeRule.onNodeWithText("Ari").assertIsDisplayed()
        composeRule.onNodeWithTag("history-load-more").assertHasClickAction()
    }

    @Test fun return_cannot_submit_before_authoritative_quote() {
        composeRule.setContent { PosTheme { ReturnScreen(editing(), {}, { _, _ -> }, {}, {}, {}, {}) } }
        composeRule.onNodeWithText("Original 2 · Returned 0 · Remaining 2").assertIsDisplayed()
        composeRule.onNodeWithTag("return-submit").assertIsNotEnabled()
        composeRule.onNodeWithTag("return-quote").assertHasClickAction()
    }

    private fun summary() = SaleSummaryDto("POS Invoice", "INV-1", SaleStatus.PAID, "Walk In", "Ari", "IDR", "100", "100", "0", "2026-08-07", "10:00")
    private fun editing() = ReturnUiState.Editing("INV-1", listOf(ReturnabilityDto("row-1", "BREAD", "2", "0", "2", "Nos", emptyList(), emptyList(), true)), ReturnQuantityPolicy(2, "0.01", "999999999999.99", "ascii_decimal_dot", "reject", "return-quantity/v1"), listOf("Cash"), false)
}
