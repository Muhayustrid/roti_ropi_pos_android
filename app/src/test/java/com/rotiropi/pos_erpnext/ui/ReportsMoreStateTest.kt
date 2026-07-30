package com.rotiropi.pos_erpnext.ui

import com.rotiropi.pos_erpnext.ui.reports.ReportBreakdown
import com.rotiropi.pos_erpnext.ui.reports.ReportChartBar
import com.rotiropi.pos_erpnext.ui.reports.ReportMetric
import com.rotiropi.pos_erpnext.ui.reports.ReportPeriod
import com.rotiropi.pos_erpnext.ui.reports.ReportTopProduct
import com.rotiropi.pos_erpnext.ui.reports.ReportsContent
import com.rotiropi.pos_erpnext.ui.reports.chartBarSlot
import com.rotiropi.pos_erpnext.ui.settings.PosThemeMode
import com.rotiropi.pos_erpnext.ui.settings.ThemePreferences
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportsMoreStateTest {

    @Test
    fun reports_keep_caller_supplied_labels_without_aggregation() {
        val content = ReportsContent(
            selectedPeriod = ReportPeriod.WEEK,
            metrics = listOf(ReportMetric("sales", "Net sales", "IDR 825,000", "Demo snapshot")),
            breakdown = listOf(ReportBreakdown("cash", "Cash", "IDR 525,000")),
            chartBars = listOf(ReportChartBar("fri", "Fri", "IDR 210,000", 0.75f)),
            chartSummary = "Peak sales on Friday.",
            topProducts = listOf(ReportTopProduct("croissant", "Croissant Pack", "18 sold")),
            demoData = true,
        )

        assertEquals("IDR 825,000", content.metrics.single().value)
        assertEquals("IDR 525,000", content.breakdown.single().value)
        assertEquals("Peak sales on Friday.", content.chartSummary)
        assertEquals("18 sold", content.topProducts.single().value)
    }

    @Test
    fun chart_fraction_is_clamped_only_for_safe_geometry() {
        assertEquals(0f, ReportChartBar("low", "Low", "0", -0.5f).safeFraction)
        assertEquals(0.4f, ReportChartBar("mid", "Mid", "40", 0.4f).safeFraction)
        assertEquals(1f, ReportChartBar("high", "High", "100", 1.5f).safeFraction)
        assertEquals(0f, ReportChartBar("invalid", "Invalid", "—", Float.NaN).safeFraction)
    }

    @Test
    fun chart_bars_are_centered_in_equal_width_slots() {
        val width = 800f
        val count = 4
        val slotWidth = width / count

        repeat(count) { index ->
            val slot = chartBarSlot(index, count, width)
            val center = slot.x + slot.width / 2f
            assertEquals((index + 0.5f) * slotWidth, center, 0.01f)
            assertTrue("bar must stay inside its slot", slot.width <= slotWidth)
        }
    }

    @Test
    fun narrow_chart_keeps_minimum_bar_width() {
        val slot = chartBarSlot(0, 40, 100f)
        assertEquals(10f, slot.width, 0.01f)
    }

    @Test
    fun unknown_theme_preferences_use_safe_defaults() {
        assertEquals(PosThemeMode.SYSTEM, ThemePreferences.parseThemeMode(null))
        assertEquals(PosThemeMode.SYSTEM, ThemePreferences.parseThemeMode("BROKEN"))
        assertEquals(PosAccent.BLUE, ThemePreferences.parseAccent(null))
        assertEquals(PosAccent.BLUE, ThemePreferences.parseAccent("ORANGE"))
    }

    @Test
    fun valid_theme_preferences_parse_enum_names() {
        assertEquals(PosThemeMode.DARK, ThemePreferences.parseThemeMode("DARK"))
        assertEquals(PosAccent.TEAL, ThemePreferences.parseAccent("TEAL"))
    }
}
