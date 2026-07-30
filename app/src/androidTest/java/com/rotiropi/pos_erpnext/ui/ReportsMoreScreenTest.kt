package com.rotiropi.pos_erpnext.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.reports.ReportBreakdown
import com.rotiropi.pos_erpnext.ui.reports.ReportChartBar
import com.rotiropi.pos_erpnext.ui.reports.ReportMetric
import com.rotiropi.pos_erpnext.ui.reports.ReportPeriod
import com.rotiropi.pos_erpnext.ui.reports.ReportTopProduct
import com.rotiropi.pos_erpnext.ui.reports.ReportsContent
import com.rotiropi.pos_erpnext.ui.reports.ReportsScreen
import com.rotiropi.pos_erpnext.ui.reports.ReportsUiState
import com.rotiropi.pos_erpnext.ui.theme.PosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportsMoreScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun release_reports_is_honest_and_has_no_populated_controls() {
        composeRule.setContent {
            PosTheme {
                ReportsScreen(ReportsUiState.Unavailable, PosLayoutMode.COMPACT)
            }
        }

        composeRule.onNodeWithText("Reports unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Demo data").assertDoesNotExist()
        composeRule.onNodeWithText("Today").assertDoesNotExist()
        composeRule.onNodeWithText("Retry").assertDoesNotExist()
        composeRule.onNodeWithTag("reports-chart").assertDoesNotExist()
    }

    @Test
    fun populated_reports_exposes_demo_labels_period_and_chart_alternative() {
        var selected: ReportPeriod? = null
        composeRule.setContent {
            PosTheme {
                ReportsScreen(
                    state = reportsFixture(),
                    layoutMode = PosLayoutMode.COMPACT,
                    onPeriodSelected = { selected = it },
                )
            }
        }

        composeRule.onNodeWithText("Demo data").assertIsDisplayed()
        composeRule.onNodeWithTag("reports-period-week")
            .assertIsSelected()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("reports-period-month").performClick()
        composeRule.runOnIdle { assertEquals(ReportPeriod.MONTH, selected) }
        composeRule.onNodeWithText("IDR 825,000").assertIsDisplayed()
        composeRule.onNodeWithText("IDR 525,000").assertIsDisplayed()
        composeRule.onNodeWithTag("reports-top-product-coffee")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("18 sold").assertIsDisplayed()
        composeRule.onNodeWithText("Peak sales on Friday.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Sales trend chart. Peak sales on Friday."
        ).assertIsDisplayed()
    }

    @Test
    fun reports_layout_adapts_from_stack_to_two_panes() {
        val layoutMode = mutableStateOf(PosLayoutMode.COMPACT)
        composeRule.setContent {
            PosTheme {
                ReportsScreen(
                    state = reportsFixture(),
                    layoutMode = layoutMode.value,
                )
            }
        }

        composeRule.onNodeWithTag("reports-compact").assertIsDisplayed()
        composeRule.runOnIdle { layoutMode.value = PosLayoutMode.EXPANDED }
        composeRule.onNodeWithTag("reports-expanded").assertIsDisplayed()
        composeRule.onNodeWithTag("reports-expanded-primary-pane").assertIsDisplayed()
        composeRule.onNodeWithTag("reports-expanded-top-products-pane").assertIsDisplayed()
        composeRule.onNodeWithTag("reports-compact").assertDoesNotExist()
    }

    @Test
    fun report_periods_follow_external_keyboard_order() {
        composeRule.setContent {
            PosTheme {
                ReportsScreen(
                    state = reportsFixture(),
                    layoutMode = PosLayoutMode.COMPACT,
                )
            }
        }

        composeRule.onNodeWithTag("reports-period-today").requestFocus().assertIsFocused()
        composeRule.onNodeWithTag("reports-period-today").performKeyInput { pressKey(Key.Tab) }
        composeRule.onNodeWithTag("reports-period-week").assertIsFocused()
        composeRule.onNodeWithTag("reports-period-week").performKeyInput { pressKey(Key.Tab) }
        composeRule.onNodeWithTag("reports-period-month").assertIsFocused()
    }

    @Test
    fun reports_remain_scrollable_at_font_scale_1_5() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                PosTheme {
                    Box(Modifier.width(400.dp).height(600.dp)) {
                        ReportsScreen(
                            state = reportsFixture(),
                            layoutMode = PosLayoutMode.COMPACT,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("reports-top-product-coffee")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun reportsFixture(): ReportsUiState.Content {
        return ReportsUiState.Content(
            ReportsContent(
                selectedPeriod = ReportPeriod.WEEK,
                metrics = listOf(
                    ReportMetric("sales", "Total Sales", "IDR 825,000", "7 days aggregate"),
                    ReportMetric("txns", "Transactions", "32", "Avg IDR 25,781/txn"),
                ),
                breakdown = listOf(
                    ReportBreakdown("pastry", "Pastry Category", "IDR 525,000"),
                    ReportBreakdown("beverage", "Beverage Category", "IDR 300,000"),
                ),
                chartBars = listOf(
                    ReportChartBar("wed", "Wed", "IDR 200,000", 0.6f),
                    ReportChartBar("thu", "Thu", "IDR 250,000", 0.75f),
                    ReportChartBar("fri", "Fri", "IDR 375,000", 1.0f),
                ),
                chartSummary = "Peak sales on Friday.",
                topProducts = listOf(
                    ReportTopProduct("croissant", "Croissant Pack", "24 sold"),
                    ReportTopProduct("coffee", "Iced Latte", "18 sold"),
                ),
                demoData = true,
            )
        )
    }
}
