package com.rotiropi.pos_erpnext.ui

import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.MainActivity
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.MobilePosApplication
import com.rotiropi.pos_erpnext.auth.OAuthTokens
import com.rotiropi.pos_erpnext.auth.TokenStore
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeShellTest {

    private val tokenStore: TokenStore by lazy {
        TokenStore(composeRule.activity.applicationContext)
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * Resolved through the activity so the expected text follows the applied interface
     * language instead of pinning one translation into the assertion.
     */
    private fun text(id: Int): String = composeRule.activity.getString(id)

    @Before
    fun authenticateShellFixture() {
        tokenStore.write(
            OAuthTokens(
                accessToken = "compose-shell-fixture",
                refreshToken = null,
                expiresAt = Long.MAX_VALUE,
                canonicalOrigin = MobilePosApplication.CANONICAL_ORIGIN,
                clientId = MobilePosApplication.CLIENT_ID,
            )
        )
        composeRule.activityRule.scenario.onActivity { activity ->
            (activity.application as MobilePosApplication).authenticationOwner.restoreAuthenticationState()
        }
    }

    @After
    fun clearShellFixture() {
        composeRule.activityRule.scenario.onActivity { activity ->
            (activity.application as MobilePosApplication).authenticationOwner.logout()
        }
    }

    @Test
    fun production_launch_without_valid_token_shows_sign_in() {
        tokenStore.clear()
        composeRule.activityRule.scenario.onActivity { activity ->
            (activity.application as MobilePosApplication).authenticationOwner.restoreAuthenticationState()
        }

        composeRule.onNodeWithTag("sign-in-button").assertIsDisplayed()
        composeRule.onNodeWithTag("root-cashier").assertDoesNotExist()
    }

    @Test
    fun authenticated_fixture_exposes_shell_and_logout_returns_to_sign_in() {
        composeRule.onNodeWithTag("root-more").performClick()
        composeRule.onNodeWithTag("more-logout").performScrollTo().performClick()

        composeRule.onNodeWithTag("sign-in-button").assertIsDisplayed()
        composeRule.onNodeWithTag("root-more").assertDoesNotExist()
    }

    @Test
    fun launch_displays_compose_cashier_destination() {
        composeRule.onNodeWithTag("destination-content-cashier").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.cashier_unavailable)).assertIsDisplayed()
        composeRule.onNodeWithTag("root-cashier")
            .assertIsSelected()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun recreation_preserves_selected_root_without_duplicate_destination() {
        composeRule.onNodeWithTag("root-history").performClick()
        composeRule.onNodeWithTag("destination-content-history").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag("root-history").assertIsSelected()
        composeRule.onAllNodes(hasTestTag("destination-content-history"))
            .assertCountEquals(1)
    }

    @Test
    fun root_destinations_are_accessible_and_carry_no_elevated_action() {
        listOf("cashier", "history", "more").forEach { root ->
            composeRule.onNodeWithTag("root-$root").performClick().assertIsSelected()
            composeRule.onNodeWithTag("destination-content-$root").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("root-cashier")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("cashier-elevated-action", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun external_keyboard_traverses_root_destinations_in_visual_order() {
        val roots = listOf("cashier", "history", "more")

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.window.decorView.requestFocusFromTouch()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("root-${roots.first()}").requestFocus()
        roots.forEach { root ->
            composeRule.onNodeWithTag("root-$root").assertIsFocused()
            composeRule.onNodeWithTag("root-$root")
                .performKeyInput { pressKey(Key.Tab) }
        }
    }

    @Test
    fun cashier_release_destination_is_honest_and_has_no_input() {
        composeRule.onNodeWithTag("root-cashier").performClick()
        composeRule.onNodeWithText(text(R.string.cashier_unavailable)).assertIsDisplayed()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun more_release_destination_is_an_honest_feature_surface() {
        composeRule.onNodeWithTag("root-more").performClick()
        composeRule.onNodeWithText(text(R.string.more_group_appearance)).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.state_unavailable)).assertCountEquals(2)
        composeRule.onAllNodesWithText(text(R.string.state_not_supported)).assertCountEquals(2)
    }

    @Test
    fun theme_selection_persists_across_activity_recreation() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences("pos_ui_preferences", Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
        try {
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("root-more").performClick()
            composeRule.onNodeWithTag("more-accent-teal").performScrollTo().performClick()

            composeRule.activityRule.scenario.recreate()

            composeRule.onNodeWithTag("root-more").performClick()
            composeRule.onNodeWithTag("more-accent-teal").performScrollTo().assertIsSelected()
            composeRule.onNodeWithTag("more-accent-blue").performClick()
        } finally {
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.getSharedPreferences("pos_ui_preferences", Context.MODE_PRIVATE)
                    .edit().clear().commit()
            }
        }
    }
}
