package com.rotiropi.pos_erpnext.ui.customer

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
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
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.then
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.ui.theme.PosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.Dispatchers

@RunWith(AndroidJUnit4::class)
class CustomerSearchSheetTest {
    @get:Rule val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun profileDefaultMarkedRowUsesWalkInSelectionAndPreservesName() {
        val viewModel = selectionViewModel()
        viewModel.onWalkInDisplayNameChanged("Ayu")
        setSelectionContent(viewModel, listOf(CustomerRecord("WALK", "Walk In", null, true)))

        rule.onNodeWithTag("customer-WALK").performClick()

        rule.runOnIdle { assertEquals(CustomerSelection.WalkIn("WALK", "Ayu"), viewModel.state.value.selection) }
        rule.onNodeWithTag("customer-walk-in-name").assertIsDisplayed()
    }

    @Test fun incorrectlyMarkedOtherRowUsesRegisteredSelection() {
        val viewModel = selectionViewModel()
        setSelectionContent(viewModel, listOf(CustomerRecord("OTHER", "Other", null, true)))

        rule.onNodeWithTag("customer-OTHER").performClick()

        rule.runOnIdle { assertEquals(CustomerSelection.Registered("OTHER", "Other", null), viewModel.state.value.selection) }
        rule.onNodeWithTag("customer-select-walk-in").assertIsDisplayed()
    }

    @Test fun registeredRowCanTransitionToProfileDefaultWalkInRow() {
        val viewModel = selectionViewModel()
        setSelectionContent(viewModel, listOf(
            CustomerRecord("REGISTERED", "Registered", null, false),
            CustomerRecord("WALK", "Walk In", null, true),
        ))

        rule.onNodeWithTag("customer-REGISTERED").performClick()
        rule.onNodeWithTag("customer-WALK").performClick()

        rule.runOnIdle { assertEquals(CustomerSelection.WalkIn("WALK", ""), viewModel.state.value.selection) }
    }

    @Test fun walkInRowCanTransitionToRegisteredAndClearsName() {
        val viewModel = selectionViewModel()
        viewModel.onWalkInDisplayNameChanged("Must clear")
        setSelectionContent(viewModel, listOf(CustomerRecord("REGISTERED", "Registered", null, false)))

        rule.onNodeWithTag("customer-REGISTERED").performClick()

        rule.runOnIdle { assertEquals(CustomerSelection.Registered("REGISTERED", "Registered", null), viewModel.state.value.selection) }
        rule.onNodeWithTag("customer-walk-in-name").assertDoesNotExist()
    }

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

    @Test fun externalKeyboardTraversesResultLoadMoreRetryAndDoneAndActivatesSelection() {
        var state by mutableStateOf(customerState(
            customers = listOf(CustomerRecord("CUST-1", "Ayu Bakery", null, false)),
            pageError = CustomerSearchError.Unavailable,
        ))
        var loadMoreCount = 0
        var retryCount = 0
        var dismissCount = 0
        rule.setContent {
            PosTheme {
                CustomerSearchSheet(
                    state = state,
                    onQueryChanged = { state = state.copy(query = it) },
                    onWalkInNameChanged = {},
                    onSelectWalkIn = {},
                    onSelectRegistered = {
                        state = state.copy(selection = CustomerSelection.Registered(it.id, it.displayLabel, it.mobile))
                    },
                    onRetry = { retryCount++ },
                    onLoadMore = { loadMoreCount++ },
                    onDismiss = { dismissCount++ },
                )
            }
        }

        val search = rule.onNodeWithTag("customer-search-input").requestFocus().assertIsFocused()
        search.performKeyInput { pressKey(Key.Tab) }
        rule.onNodeWithTag("customer-walk-in-name").assertIsFocused()
            .performKeyInput { pressKey(Key.Tab) }
        rule.onNodeWithTag("customer-CUST-1").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        rule.runOnIdle {
            assertEquals(CustomerSelection.Registered("CUST-1", "Ayu Bakery", null), state.selection)
        }
        val registeredSelection = SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription,
            context.getString(R.string.customer_registered_selected, "Ayu Bakery"),
        )
        rule.onNode(registeredSelection).assert(registeredSelection)

        rule.onNodeWithTag("customer-CUST-1").performKeyInput { pressKey(Key.Tab) }
        rule.onNodeWithTag("customer-load-more").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter); pressKey(Key.Tab) }
        rule.onNodeWithTag("customer-page-retry").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter); pressKey(Key.Tab) }
        rule.onNodeWithTag("customer-dismiss").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }

        rule.runOnIdle {
            assertEquals(1, loadMoreCount)
            assertEquals(1, retryCount)
            assertEquals(1, dismissCount)
        }
    }

    @Test fun loadingAndRetryStatesAreVisible() {
        var retryCount = 0
        rule.setContent {
            PosTheme { CustomerSearchSheet(CustomerSearchUiState(loading = true, error = CustomerSearchError.Unavailable), {}, {}, {}, {}, { retryCount++ }, {}, {}) }
        }
        rule.onNodeWithTag("customer-loading").assertIsDisplayed()
        rule.onNodeWithTag("customer-retry").assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertHasClickAction().performClick()
        rule.runOnIdle { assertEquals(1, retryCount) }
        rule.onNodeWithText(context.getString(R.string.customer_label)).assertIsDisplayed()
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
        val registeredSelection = SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription,
            context.getString(R.string.customer_registered_selected, "Customer 30"),
        )
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

    private fun selectionViewModel() = CustomerSearchViewModel(
        dispatcher = Dispatchers.Unconfined,
        search = { _, _ -> error("Search is not expected") },
    ).also { it.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK")) }

    private fun setSelectionContent(viewModel: CustomerSearchViewModel, customers: List<CustomerRecord>) {
        rule.setContent {
            val state by viewModel.state.collectAsState()
            PosTheme {
                CustomerSearchSheet(
                    state.copy(customers = customers),
                    {},
                    viewModel::onWalkInDisplayNameChanged,
                    viewModel::selectWalkIn,
                    viewModel::selectCustomer,
                    {},
                    {},
                    {},
                )
            }
        }
    }
}
