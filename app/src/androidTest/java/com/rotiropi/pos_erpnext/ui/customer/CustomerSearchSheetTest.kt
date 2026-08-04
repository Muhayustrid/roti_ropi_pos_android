package com.rotiropi.pos_erpnext.ui.customer

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.then
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.ui.theme.PosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomerSearchSheetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun walkInAndRegisteredSelectionAreAccessible() {
        var selected by mutableStateOf<CustomerRecord?>(null)
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
        rule.runOnIdle { assertEquals("CUST-1", selected?.id) }
    }

    @Test fun loadingAndRetryStatesAreVisible() {
        var retryCount = 0
        rule.setContent {
            PosTheme { CustomerSearchSheet(CustomerSearchUiState(loading = true, error = CustomerSearchError.Unavailable), {}, {}, {}, {}, { retryCount++ }, {}, {}) }
        }
        rule.onNodeWithTag("customer-loading").assertIsDisplayed()
        rule.onNodeWithTag("customer-retry").assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertHasClickAction().performClick()
        rule.runOnIdle { assertEquals(1, retryCount) }
        rule.onNodeWithText("Customer").assertIsDisplayed()
    }

    @Test fun emptySearchStateIsVisible() {
        rule.setContent {
            PosTheme { CustomerSearchSheet(CustomerSearchUiState(), {}, {}, {}, {}, {}, {}, {}) }
        }

        rule.onNodeWithTag("customer-empty").assertIsDisplayed()
    }

    @Test fun stickyHeaderRemainsVisibleAfterScrollingCustomerResults() {
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 640.dp))) {
                PosTheme {
                    CustomerSearchSheet(
                        customerState(customers = (1..30).map { CustomerRecord("CUST-$it", "Customer $it", null, false) }),
                        {}, {}, {}, {}, {}, {}, {},
                    )
                }
            }
        }

        rule.onNodeWithTag("customer-results").performScrollToNode(hasTestTag("customer-CUST-30"))
        rule.onNodeWithTag("customer-search-header").assertIsDisplayed()
    }

    @Test fun compactCustomerSelectorKeepsSearchSelectionAndActionsUsable() {
        adaptiveCustomerSelectorKeepsControlsUsable(320.dp, 640.dp, 1f)
    }

    @Test fun landscapeCustomerSelectorKeepsSearchSelectionAndActionsUsable() {
        adaptiveCustomerSelectorKeepsControlsUsable(800.dp, 360.dp, 1f)
    }

    @Test fun largeFontCustomerSelectorKeepsSearchSelectionAndActionsUsable() {
        adaptiveCustomerSelectorKeepsControlsUsable(400.dp, 640.dp, 1.5f)
    }

    private fun adaptiveCustomerSelectorKeepsControlsUsable(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, fontScale: Float) {
        var state by mutableStateOf(customerState(customers = (1..30).map { CustomerRecord("CUST-$it", "Customer $it", null, false) }, pageError = CustomerSearchError.Unavailable))
        var selected by mutableStateOf<CustomerRecord?>(null)
        var dismissCount by mutableIntStateOf(0)
        var loadMoreCount by mutableIntStateOf(0)
        var initialRetryCount by mutableIntStateOf(0)
        var pageRetryCount by mutableIntStateOf(0)
        rule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(width, height)) then
                DeviceConfigurationOverride.FontScale(fontScale),
            ) {
                PosTheme {
                    CustomerSearchContent(
                        state = state,
                        onQueryChanged = { state = state.copy(query = it) },
                        onWalkInNameChanged = {},
                        onSelectWalkIn = {},
                        onSelectRegistered = {
                            selected = it
                            state = state.copy(selection = CustomerSelection.Registered(it.id, it.displayLabel, it.mobile))
                        },
                        onRetry = {
                            if (state.error == null) pageRetryCount++ else initialRetryCount++
                        },
                        onLoadMore = { loadMoreCount++ },
                        onDismiss = { dismissCount++ },
                    )
                }
            }
        }

        rule.onAllNodes(hasScrollAction()).assertCountEquals(1)
        rule.onNodeWithTag("customer-results").assertWidthIsEqualTo(width)
        rule.onNodeWithTag("customer-search-header").assertIsDisplayed()
        rule.onNodeWithTag("customer-dismiss").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            .assertHasClickAction().performClick()
        rule.runOnIdle { assertEquals(1, dismissCount) }
        rule.onNodeWithTag("customer-search-input").performTextInput("ayu")
        rule.runOnIdle { assertEquals("ayu", state.query) }
        rule.onNodeWithTag("customer-walk-in-name").assertIsDisplayed()
        rule.onNodeWithTag("customer-results").performScrollToNode(hasTestTag("customer-CUST-30"))
        rule.onNodeWithTag("customer-CUST-30").assertIsDisplayed().assertHasClickAction().performClick()
        rule.runOnIdle { assertEquals("CUST-30", selected?.id) }
        val registeredSelection = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Registered customer Customer 30 selected")
        rule.onNode(registeredSelection).assert(registeredSelection)
        rule.onNodeWithTag("customer-results").performScrollToNode(hasTestTag("customer-load-more"))
        rule.onNodeWithTag("customer-load-more").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            .assertHasClickAction().performClick()
        rule.onNodeWithTag("customer-results").performScrollToNode(hasTestTag("customer-page-retry"))
        rule.onNodeWithTag("customer-page-retry").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            .assertHasClickAction().performClick()
        rule.runOnIdle {
            assertEquals(1, loadMoreCount)
            assertEquals(1, pageRetryCount)
            state = state.copy(
                customers = emptyList(),
                loading = false,
                hasMore = false,
                error = CustomerSearchError.Unavailable,
                pageError = null,
            )
        }
        rule.onNodeWithTag("customer-results").performScrollToNode(hasTestTag("customer-retry"))
        rule.onNodeWithTag("customer-retry").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            .assertHasClickAction().performClick()
        rule.runOnIdle { assertEquals(1, initialRetryCount) }
    }

    private fun customerState(
        query: String = "",
        pageError: CustomerSearchError? = null,
        customers: List<CustomerRecord> = listOf(CustomerRecord("CUST-1", "Ayu Bakery", null, false)),
    ) = CustomerSearchUiState(
        query = query,
        customers = customers,
        selection = CustomerSelection.WalkIn("WALK", ""),
        hasMore = true,
        pageError = pageError,
    )
}
