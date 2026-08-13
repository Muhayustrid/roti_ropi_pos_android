package com.rotiropi.pos_erpnext.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.settings.MoreScreen
import com.rotiropi.pos_erpnext.ui.settings.MoreUiState
import com.rotiropi.pos_erpnext.ui.settings.PosLanguage
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import com.rotiropi.pos_erpnext.ui.theme.PosTheme
import com.rotiropi.pos_erpnext.ui.settings.PosThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoreScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun more_shows_honest_groups_and_emits_theme_selections() {
        val mode = mutableStateOf(PosThemeMode.SYSTEM)
        val accent = mutableStateOf(PosAccent.BLUE)
        composeRule.setContent {
            PosTheme {
                MoreScreen(
                    state = MoreUiState(
                        outletLabel = null,
                        userSessionLabel = null,
                        themeMode = mode.value,
                        accent = accent.value,
                    ),
                    layoutMode = PosLayoutMode.COMPACT,
                    onThemeModeSelected = { mode.value = it },
                    onAccentSelected = { accent.value = it },
                )
            }
        }

        listOf(
            R.string.more_group_outlet,
            R.string.more_group_user_session,
            R.string.more_group_appearance,
            R.string.more_group_printer,
            R.string.more_group_synchronization,
        ).map(context::getString)
            .forEach {
                composeRule.onNodeWithText(it)
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        composeRule.onAllNodesWithText(context.getString(R.string.state_unavailable)).assertCountEquals(2)
        composeRule.onNodeWithTag("more-theme-system").assertIsSelected()
        composeRule.onNodeWithTag("more-accent-blue").assertIsSelected()
        composeRule.onNodeWithTag("more-theme-dark")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag("more-accent-teal")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag("more-theme-dark").assertIsSelected()
        composeRule.onNodeWithTag("more-accent-teal").assertIsSelected()
    }

    @Test
    fun language_chips_show_each_language_in_its_own_words_and_emit_selections() {
        val language = mutableStateOf(PosLanguage.INDONESIAN)
        composeRule.setContent {
            PosTheme {
                MoreScreen(
                    state = MoreUiState(
                        outletLabel = null,
                        userSessionLabel = null,
                        themeMode = PosThemeMode.SYSTEM,
                        accent = PosAccent.BLUE,
                        language = language.value,
                    ),
                    layoutMode = PosLayoutMode.COMPACT,
                    onLanguageSelected = { language.value = it },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.more_language))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("more-language-id").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("more-language-en")
            .performScrollTo()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(PosLanguage.ENGLISH, language.value) }
        composeRule.onNodeWithTag("more-language-en").assertIsSelected()
        // Each label stays in its own language so a cashier can find theirs in any interface.
        composeRule.onNodeWithText(PosLanguage.INDONESIAN.label).assertIsDisplayed()
        composeRule.onNodeWithText(PosLanguage.ENGLISH.label).assertIsDisplayed()
    }

    @Test
    fun more_exposes_Closing_only_when_authoritative_capability_is_enabled() {
        var opened = false
        val state = mutableStateOf(false)
        composeRule.setContent {
            PosTheme {
                MoreScreen(
                    state = MoreUiState(
                        outletLabel = "OUTLET-01",
                        userSessionLabel = "cashier@example.test",
                        themeMode = PosThemeMode.SYSTEM,
                        accent = PosAccent.BLUE,
                        closingAvailable = state.value,
                    ),
                    layoutMode = PosLayoutMode.COMPACT,
                    onOpenClosing = { opened = true },
                )
            }
        }

        composeRule.onNodeWithTag("more-closing").assertDoesNotExist()
        composeRule.runOnIdle { state.value = true }
        composeRule.onNodeWithTag("more-closing")
            .performScrollTo()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun unsupported_more_capabilities_are_disabled_without_actions() {
        composeRule.setContent {
            PosTheme {
                MoreScreen(
                    state = MoreUiState(
                        outletLabel = null,
                        userSessionLabel = null,
                        themeMode = PosThemeMode.SYSTEM,
                        accent = PosAccent.BLUE,
                    ),
                    layoutMode = PosLayoutMode.COMPACT,
                )
            }
        }

        composeRule.onAllNodesWithText(context.getString(R.string.state_not_supported)).assertCountEquals(2)
        composeRule.onNodeWithTag("more-printer")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        composeRule.onNodeWithTag("more-synchronization")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test
    fun more_layout_adapts_from_stack_to_two_columns() {
        val layoutMode = mutableStateOf(PosLayoutMode.COMPACT)
        composeRule.setContent {
            PosTheme {
                MoreScreen(
                    state = MoreUiState(
                        outletLabel = null,
                        userSessionLabel = null,
                        themeMode = PosThemeMode.SYSTEM,
                        accent = PosAccent.BLUE,
                    ),
                    layoutMode = layoutMode.value,
                )
            }
        }

        composeRule.onNodeWithTag("more-compact").assertIsDisplayed()
        composeRule.runOnIdle { layoutMode.value = PosLayoutMode.EXPANDED }
        composeRule.onNodeWithTag("more-expanded").assertIsDisplayed()
        composeRule.onNodeWithTag("more-compact").assertDoesNotExist()
    }

    @Test
    fun appearance_controls_follow_external_keyboard_order() {
        composeRule.setContent {
            PosTheme {
                MoreScreen(
                    state = MoreUiState(
                        outletLabel = null,
                        userSessionLabel = null,
                        themeMode = PosThemeMode.SYSTEM,
                        accent = PosAccent.BLUE,
                    ),
                    layoutMode = PosLayoutMode.COMPACT,
                )
            }
        }

        composeRule.onNodeWithTag("more-theme-system").requestFocus().assertIsFocused()
        composeRule.onNodeWithTag("more-theme-system").performKeyInput { pressKey(Key.Tab) }
        composeRule.onNodeWithTag("more-theme-light").assertIsFocused()
        composeRule.onNodeWithTag("more-theme-light").performKeyInput { pressKey(Key.Tab) }
        composeRule.onNodeWithTag("more-theme-dark").assertIsFocused()
    }

    @Test
    fun more_remains_scrollable_at_font_scale_1_5() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                PosTheme {
                    Box(Modifier.width(400.dp).height(600.dp)) {
                        MoreScreen(
                            state = MoreUiState(
                                outletLabel = null,
                                userSessionLabel = null,
                                themeMode = PosThemeMode.SYSTEM,
                                accent = PosAccent.BLUE,
                            ),
                            layoutMode = PosLayoutMode.COMPACT,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("more-synchronization")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
