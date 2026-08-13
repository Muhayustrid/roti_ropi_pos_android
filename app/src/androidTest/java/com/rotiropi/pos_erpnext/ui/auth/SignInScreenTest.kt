package com.rotiropi.pos_erpnext.ui.auth

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.ui.theme.WarmCommerceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignInScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun continue_action_is_browser_owned_and_never_captures_credentials() {
        var clicked = 0
        composeRule.setContent {
            WarmCommerceTheme {
                SignInScreen(onSignInClick = { clicked++ })
            }
        }

        composeRule.onNodeWithTag("sign-in-button").assertIsDisplayed().assertIsEnabled().assertHasClickAction()
        composeRule.onNodeWithTag("sign-in-button").performClick()

        assertEquals(1, clicked)
        // The screen never renders an editable credential or server capture field.
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        composeRule.onNodeWithText(context.getString(R.string.sign_in_button)).assertIsDisplayed()
    }

    @Test
    fun erpnext_origin_is_read_only_environment_information() {
        composeRule.setContent {
            WarmCommerceTheme {
                SignInScreen(serverOrigin = "https://erpnext.example.test")
            }
        }

        composeRule.onNodeWithTag("sign-in-server-origin")
            .assertIsDisplayed()
            .assertHasNoClickAction()
        composeRule.onNodeWithText("https://erpnext.example.test").assertIsDisplayed()
        // Read-only origin must not be an editable input.
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun origin_is_omitted_when_configuration_does_not_supply_it() {
        composeRule.setContent {
            WarmCommerceTheme {
                SignInScreen(serverOrigin = null)
            }
        }

        composeRule.onNodeWithTag("sign-in-server-origin").assertDoesNotExist()
        composeRule.onNodeWithTag("sign-in-button").assertIsDisplayed()
    }

    @Test
    fun waiting_state_shows_progress_and_hides_the_primary_action() {
        composeRule.setContent {
            WarmCommerceTheme {
                SignInScreen(signingIn = true)
            }
        }

        composeRule.onNodeWithTag("sign-in-progress").assertIsDisplayed()
        composeRule.onNodeWithTag("sign-in-waiting").assertIsDisplayed()
        composeRule.onNodeWithTag("sign-in-button").assertDoesNotExist()
    }

    @Test
    fun failure_presents_a_cashier_friendly_message() {
        val message = context.getString(R.string.sign_in_error_generic)
        composeRule.setContent {
            WarmCommerceTheme {
                SignInScreen(errorMessage = message)
            }
        }

        composeRule.onNodeWithTag("sign-in-error").assertIsDisplayed()
        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.onNodeWithTag("sign-in-button").assertIsDisplayed()
    }

    @Test
    fun error_mapper_never_exposes_raw_oauth_reasons() {
        // The mapper returns a resource id, so the guarantee has to be checked on the
        // resolved string.
        val friendly = InstrumentationRegistry.getInstrumentation().targetContext.getString(
            signInErrorMessage(
                com.rotiropi.pos_erpnext.auth.OAuthCompletionResult.Reason.AUTHORIZATION_CANCELLED,
            ),
        )
        assertTrue(friendly.isNotBlank())
        assertTrue(!friendly.contains("AUTHORIZATION_CANCELLED"))
        assertTrue(!friendly.contains("TOKEN"))
    }
}
