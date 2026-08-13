package com.rotiropi.pos_erpnext.ui.customer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.MainActivity
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.MobilePosApplication
import com.rotiropi.pos_erpnext.test.enterKeyboardInputMode
import com.rotiropi.pos_erpnext.auth.OAuthTokens
import com.rotiropi.pos_erpnext.auth.TokenStore
import com.rotiropi.pos_erpnext.data.BootstrapData
import com.rotiropi.pos_erpnext.data.BootstrapUser
import com.rotiropi.pos_erpnext.data.CurrentSessionResult
import com.rotiropi.pos_erpnext.data.Customer
import com.rotiropi.pos_erpnext.data.CustomerSearchPage
import com.rotiropi.pos_erpnext.data.CustomerSearchResult
import com.rotiropi.pos_erpnext.data.CustomerSearchFailure
import com.rotiropi.pos_erpnext.data.PosCapabilities
import com.rotiropi.pos_erpnext.data.PosProfile
import com.rotiropi.pos_erpnext.data.OpeningSession
import com.rotiropi.pos_erpnext.data.OpeningStatus
import com.rotiropi.pos_erpnext.data.RepositoryResult
import com.rotiropi.pos_erpnext.data.RepositoryState
import com.rotiropi.pos_erpnext.data.api.ApiCallCancellation
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Field
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class CustomerSearchRootTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var tokens: TokenStore
    private val responses = Collections.synchronizedList(mutableListOf<CustomerSearchResult>())
    private val blankResponses = Collections.synchronizedList(mutableListOf<CustomerSearchResult>())
    private val requests = Collections.synchronizedList(mutableListOf<CustomerSearchRequest>())
    @Volatile private var delayMillis = 0L

    @Before fun installProductionRootFixture() {
        tokens = TokenStore(rule.activity.applicationContext)
        tokens.write(OAuthTokens("customer-root", null, Long.MAX_VALUE, MobilePosApplication.CANONICAL_ORIGIN, MobilePosApplication.CLIENT_ID))
        rule.activityRule.scenario.onActivity { activity ->
            val app = activity.application as MobilePosApplication
            set(app.mobilePosRepository, "currentState", RepositoryState(bootstrap = bootstrap()))
            app.openingRoutingGateFactory = { repository ->
                com.rotiropi.pos_erpnext.ui.opening.OpeningRoutingGate(
                    currentSession = { CurrentSessionResult.Success(repository.state.opening) },
                    refreshCapabilities = { RepositoryResult.Success(repository.state) },
                )
            }
            app.authenticationOwner.restoreAuthenticationState()
            set(app, "customerSearchViewModel", CustomerSearchViewModel(
                dispatcher = kotlinx.coroutines.Dispatchers.IO,
                search = { request, _ ->
                    requests += request
                    if (delayMillis > 0) Thread.sleep(delayMillis)
                    val queue = if (request.query.isEmpty()) blankResponses else responses
                    synchronized(queue) { queue.removeFirstOrNull() }
                        ?: page("CUST-1", "Ayu Bakery", request.start, false)
                },
            ))
            app.appViewModel.synchronizeRouteFromRepository()
        }
        rule.activityRule.scenario.recreate()
    }

    @After fun clearFixture() {
        rule.activityRule.scenario.onActivity { activity ->
            (activity.application as MobilePosApplication).openingRoutingGateFactory = null
        }
        tokens.clear()
    }

    @Test fun productionRootFirstOpenRunsOneDebouncedBlankSearchAndReopenIsNoOp() {
        delayMillis = 800
        blankResponses += CustomerSearchResult.Success(CustomerSearchPage(emptyList(), 0, 20, false))

        open()

        rule.waitUntil(2_000) { exists("customer-loading") }
        rule.waitUntil(3_000) { exists("customer-empty") }
        org.junit.Assert.assertEquals(
            listOf(CustomerSearchRequest("", "OUTLET-01", 0, 20)),
            requests.filter { it.query.isEmpty() },
        )

        rule.onNodeWithTag("customer-dismiss").performClick()
        rule.onNodeWithTag("customer-open").performClick()
        rule.waitForIdle()

        org.junit.Assert.assertEquals(1, requests.count { it.query.isEmpty() })
        rule.onNodeWithTag("customer-empty").assertIsDisplayed()
    }

    @Test fun productionRootShowsLoadingEmptyAndLiveRegion() {
        delayMillis = 800
        responses += CustomerSearchResult.Success(CustomerSearchPage(emptyList(), 0, 20, false))
        open()
        rule.onNodeWithTag("customer-search-input").performTextInput("none")
        rule.waitUntil(2_000) { rule.onNodeWithTag("customer-loading").fetchSemanticsNode().config.contains(SemanticsProperties.LiveRegion) }
        rule.onNodeWithTag("customer-loading").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.onNodeWithTag("customer-load-more").assertDoesNotExist()
        rule.waitUntil(3_000) { exists("customer-empty") }
        rule.onNodeWithTag("customer-empty").assertIsDisplayed()
    }

    @Test fun productionRootRetriesInitialFailureWithErrorLiveRegion() {
        responses += CustomerSearchResult.Failure(CustomerSearchFailure.Unavailable)
        responses += page("CUST-RETRY", "Retry Bakery", 0, false)
        open()
        rule.onNodeWithTag("customer-search-input").performTextInput("retry")
        rule.waitUntil(2_000) { exists("customer-retry") }
        rule.onNodeWithTag("customer-retry").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText(rule.activity.getString(CustomerSearchError.Unavailable.toUiMessage())).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
        val count = requests.size
        rule.onNodeWithTag("customer-retry").performClick()
        rule.waitUntil(2_000) { exists("customer-CUST-RETRY") }
        org.junit.Assert.assertEquals(count + 1, requests.size)
    }

    @Test fun productionRootPaginatesDeduplicatesAndRetriesFailedOffset() {
        responses += CustomerSearchResult.Success(CustomerSearchPage(listOf(Customer("A", "A", null, false), Customer("B", "B", null, false)), 0, 2, true))
        responses += CustomerSearchResult.Failure(CustomerSearchFailure.Unavailable)
        responses += CustomerSearchResult.Success(CustomerSearchPage(listOf(Customer("B", "B", null, false), Customer("C", "C", null, false)), 2, 2, false))
        open()
        rule.onNodeWithTag("customer-search-input").performTextInput("page")
        rule.waitUntil(2_000) { exists("customer-A") }
        rule.onNodeWithTag("customer-results").performTouchInput { swipeUp() }
        rule.waitUntil(2_000) { exists("customer-load-more") }
        rule.onNodeWithTag("customer-load-more").assertHeightIsAtLeast(48.dp).performClick()
        rule.waitUntil(2_000) {
            try { rule.onNodeWithTag("customer-page-retry").fetchSemanticsNode(); true } catch (_: AssertionError) { false }
        }
        rule.onNodeWithTag("customer-results").performTouchInput { swipeUp() }
        rule.waitUntil(2_000) { exists("customer-page-retry") }
        rule.onNodeWithTag("customer-page-retry").performClick()
        rule.waitUntil(2_000) { requests.count { it.query == "page" } == 3 }
        rule.waitUntil(2_000) { exists("customer-C") }
        rule.onNodeWithTag("customer-results").performScrollToNode(hasTestTag("customer-A"))
        rule.waitUntil(2_000) { exists("customer-A") }
        org.junit.Assert.assertEquals(
            listOf(0, 2, 2),
            requests.filter { it.query == "page" }.map(CustomerSearchRequest::start),
        )
    }

    @Test fun productionRootOpensCustomerSelectorWithProfileWalkInCustomer() {
        open()

        rule.onNodeWithTag("customer-search-sheet").assertIsDisplayed()
        rule.onNodeWithTag("customer-walk-in-name").assertIsDisplayed()
    }

    @Test fun productionRootSelectsMarkedProfileDefaultAsWalkIn() {
        responses += page("WALK-PROFILE", "Walk In", 0, false, true)
        openAndSearch("walk")

        rule.onNodeWithTag("customer-WALK-PROFILE").performClick()

        rule.onNodeWithTag("customer-walk-in-name").assertIsDisplayed()
    }

    @Test fun productionRootTreatsIncorrectlyMarkedOtherCustomerAsRegistered() {
        responses += page("OTHER", "Other", 0, false, true)
        openAndSearch("other")

        rule.onNodeWithTag("customer-OTHER").performClick()

        rule.onNodeWithTag("customer-select-walk-in").assertIsDisplayed()
    }

    @Test fun productionRootTransitionsRegisteredToMarkedProfileDefaultWalkIn() {
        responses += CustomerSearchResult.Success(CustomerSearchPage(listOf(
            Customer("REGISTERED", "Registered", null, false),
            Customer("WALK-PROFILE", "Walk In", null, true),
        ), 0, 20, false))
        openAndSearch("both")

        rule.onNodeWithTag("customer-REGISTERED").performClick()
        rule.onNodeWithTag("customer-WALK-PROFILE").performClick()

        rule.onNodeWithTag("customer-walk-in-name").assertIsDisplayed()
    }

    @Test fun productionRootTransitionsWalkInToRegisteredAndClearsName() {
        responses += page("REGISTERED", "Registered", 0, false)
        openAndSearch("registered")
        rule.onNodeWithTag("customer-walk-in-name").performTextInput("Must clear")

        rule.onNodeWithTag("customer-REGISTERED").performClick()

        rule.onNodeWithTag("customer-walk-in-name").assertDoesNotExist()
        rule.onNodeWithTag("customer-select-walk-in").assertIsDisplayed()
    }

    @Test fun productionRootExternalKeyboardSelectsLoadsReachesRetryAndDismisses() {
        blankResponses += CustomerSearchResult.Success(CustomerSearchPage(
            listOf(Customer("CUST-KBD", "Keyboard Customer", null, false)),
            0,
            20,
            true,
        ))
        blankResponses += CustomerSearchResult.Failure(CustomerSearchFailure.Unavailable)
        open()
        rule.waitUntil(2_000) { exists("customer-CUST-KBD") }

        enterKeyboardInputMode()
        rule.onNodeWithTag("customer-search-input").requestFocus().assertIsFocused()
            .performKeyInput { pressKey(Key.Tab) }
        rule.onNodeWithTag("customer-walk-in-name").assertIsFocused()
            .performKeyInput { pressKey(Key.Tab) }
        rule.onNodeWithTag("customer-CUST-KBD").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        val selected = SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription,
            rule.activity.getString(R.string.customer_registered_selected, "Keyboard Customer"),
        )
        rule.onNode(selected).assert(selected)

        rule.onNodeWithTag("customer-CUST-KBD").performKeyInput { pressKey(Key.Tab) }
        rule.onNodeWithTag("customer-load-more").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        rule.waitUntil(2_000) { exists("customer-page-retry") }
        rule.onNodeWithTag("customer-load-more").performKeyInput { pressKey(Key.Tab) }
        rule.onNodeWithTag("customer-page-retry").assertIsFocused()
            .performKeyInput { pressKey(Key.Tab) }
        rule.onNodeWithTag("customer-dismiss").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }

        rule.onNodeWithTag("customer-search-sheet").assertDoesNotExist()
        rule.onNodeWithText("Keyboard Customer").assertIsDisplayed()
    }

    @Test fun productionRootKeepsRegisteredSelectionAfterDismissAndCanReturnToWalkIn() {
        rule.onNodeWithTag("root-cashier").performClick()
        rule.onNodeWithTag("customer-open").performClick()
        rule.onNodeWithTag("customer-search-input").performTextInput("ayu")
        rule.waitUntil(2_000) {
            try { rule.onNodeWithTag("customer-CUST-1").assertIsDisplayed(); true } catch (_: AssertionError) { false }
        }
        rule.onNodeWithTag("customer-CUST-1").assertHasClickAction().performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.customer_label)).assertIsDisplayed()
        rule.onNodeWithTag("customer-dismiss").performClick()
        rule.onNodeWithTag("customer-search-sheet").assertDoesNotExist()
        rule.onNodeWithText("Ayu Bakery").assertIsDisplayed()

        rule.onNodeWithTag("customer-open").performClick()
        rule.onNodeWithTag("customer-select-walk-in").performClick()
        rule.onNodeWithTag("customer-walk-in-name").assertIsDisplayed()
    }

    private fun bootstrap() = BootstrapData(
        user = BootstrapUser("cashier@example.test", "Cashier"),
        profiles = listOf(profile()),
        selectedProfile = profile(),
        opening = OpeningSession(
            name = "OPENING-TEST-0001",
            posProfile = "OUTLET-01",
            company = "Roti Ropi",
            user = "cashier@example.test",
            status = OpeningStatus.OPEN,
            postingDate = "2026-08-06",
            periodStartDate = "2026-08-06T08:00:00+07:00",
            openingBalances = emptyList(),
            warnings = emptyList(),
        ),
        capabilities = PosCapabilities(
            openSession = false,
            submitSale = true,
            createReturn = false,
            cancelSale = false,
            closeSession = false,
        ),
        posMode = "POS Invoice",
    )

    private fun open() {
        rule.onNodeWithTag("root-cashier").performClick()
        rule.onNodeWithTag("customer-open").assertHeightIsAtLeast(48.dp).performClick()
    }

    private fun openAndSearch(query: String) {
        open()
        rule.onNodeWithTag("customer-search-input").performTextInput(query)
        rule.waitUntil(5_000) { requests.any { it.query == query } }
        rule.waitUntil(5_000) { responses.isEmpty() }
    }

    private fun exists(tag: String): Boolean = try {
        rule.onNodeWithTag(tag).assertIsDisplayed()
        true
    } catch (_: AssertionError) {
        false
    }


    private fun page(id: String, label: String, start: Int, more: Boolean, defaultWalkIn: Boolean = false) =
        CustomerSearchResult.Success(CustomerSearchPage(listOf(Customer(id, label, null, defaultWalkIn)), start, 20, more))

    private fun profile() = PosProfile("OUTLET-01", "Roti Ropi", "Warehouse", "IDR", "Retail", "WALK-PROFILE", false, "POS Invoice", emptyList(), null)

    private fun set(target: Any, name: String, value: Any) {
        field(target, name).apply { isAccessible = true }.set(target, value)
    }

    private fun field(target: Any, name: String): Field {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name) } catch (_: NoSuchFieldException) { type = type.superclass }
        }
        error("Missing test fixture field $name")
    }
}
