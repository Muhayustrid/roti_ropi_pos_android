package com.rotiropi.pos_erpnext.ui.settings

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rotiropi.pos_erpnext.MainActivity
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.auth.TokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Indonesian is the product default regardless of the device language: `MainActivity`
 * applies the stored [PosLanguage] on create, and `values/strings.xml` holds Indonesian
 * so it is also the fallback for any locale without its own translation.
 *
 * The expected text is read from `values/` through a locale-overridden [Context] instead
 * of being hard-coded, so the assertion follows the resource file rather than a copy of
 * it, and still fails if the app renders English.
 */
@RunWith(AndroidJUnit4::class)
class AppLanguageTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Java's legacy `Locale` rewrites "id" to "in", which is why the tag is not spelled here. */
    private fun localized(language: PosLanguage): Context {
        val configuration = Configuration(targetContext.resources.configuration)
        configuration.setLocale(Locale(language.tag))
        return targetContext.createConfigurationContext(configuration)
    }

    @Before
    fun signOutSoTheSignInScreenIsShown() {
        TokenStore(composeRule.activity.applicationContext).clear()
        composeRule.activityRule.scenario.onActivity { activity ->
            (activity.application as com.rotiropi.pos_erpnext.MobilePosApplication)
                .authenticationOwner.restoreAuthenticationState()
        }
    }

    @Test
    fun sign_in_renders_indonesian_while_the_default_language_is_stored() {
        assertEquals(PosLanguage.INDONESIAN, ThemePreferences.from(composeRule.activity).read().language)

        val indonesian = localized(PosLanguage.INDONESIAN).getString(R.string.sign_in_button)
        val english = localized(PosLanguage.ENGLISH).getString(R.string.sign_in_button)
        // A translation that matched English would make the assertion below vacuous.
        assertNotEquals(english, indonesian)

        composeRule.onNodeWithTag("sign-in-button").assertIsDisplayed()
        composeRule.onNodeWithText(indonesian).assertIsDisplayed()
        composeRule.onNodeWithText(english).assertDoesNotExist()
    }

    /**
     * The activity resolves its own strings through the applied locale, so an assertion
     * written against `activity.getString` stays correct in either language. Every other
     * instrumentation assertion in this suite depends on that.
     */
    @Test
    fun activity_resources_resolve_through_the_applied_language() {
        val indonesian = localized(PosLanguage.INDONESIAN).getString(R.string.sign_in_button)

        assertEquals(indonesian, composeRule.activity.getString(R.string.sign_in_button))
    }
}
