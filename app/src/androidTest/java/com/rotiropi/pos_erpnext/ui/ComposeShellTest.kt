package com.rotiropi.pos_erpnext.ui

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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeShellTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launch_displays_compose_home_destination() {
        composeRule.onNodeWithTag("destination-content-home").assertIsDisplayed()
        composeRule.onNodeWithText("Complete dashboard metrics unavailable")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("root-home")
            .assertIsSelected()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun recreation_preserves_selected_root_without_duplicate_destination() {
        composeRule.onNodeWithTag("root-products").performClick()
        composeRule.onNodeWithTag("destination-content-products").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag("root-products").assertIsSelected()
        composeRule.onAllNodes(hasTestTag("destination-content-products"))
            .assertCountEquals(1)
    }

    @Test
    fun root_destinations_are_accessible_and_cashier_is_elevated() {
        listOf("home", "products", "cashier", "reports", "more").forEach { root ->
            composeRule.onNodeWithTag("root-$root").performClick().assertIsSelected()
            composeRule.onNodeWithTag("destination-content-$root").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("root-cashier")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("cashier-elevated-action", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun external_keyboard_traverses_root_destinations_in_visual_order() {
        val roots = listOf("home", "products", "cashier", "reports", "more")

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
    fun foundation_shell_does_not_claim_scanner_input_before_cashier_integration() {
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
}
