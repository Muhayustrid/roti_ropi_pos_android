package com.rotiropi.pos_erpnext.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PosWindowTest {

    @Test
    fun phone_portrait_is_tall_enough_to_split_a_body() {
        assertTrue(PosWindow(width = 411.dp, height = 914.dp).isTall)
    }

    /**
     * The regression this guards: wide enough that the width-only layout mode reports
     * EXPANDED, yet too short for two full-height columns to fit without clipping both.
     */
    @Test
    fun phone_landscape_is_wide_but_too_short_to_split_a_body() {
        assertFalse(PosWindow(width = 914.dp, height = 411.dp).isTall)
    }

    @Test
    fun tablet_landscape_is_tall_enough_to_split_a_body() {
        assertTrue(PosWindow(width = 1280.dp, height = 800.dp).isTall)
    }

    @Test
    fun tablet_portrait_is_tall_enough_to_split_a_body() {
        assertTrue(PosWindow(width = 800.dp, height = 1280.dp).isTall)
    }

    @Test
    fun the_threshold_itself_counts_as_tall() {
        assertFalse(PosWindow(width = 800.dp, height = 599.dp).isTall)
        assertTrue(PosWindow(width = 800.dp, height = 600.dp).isTall)
    }

    /**
     * Screens read the default when no shell measured the window, as in previews and
     * in tests that drive a screen directly. It must stay tall so those callers keep
     * the layout their width-driven mode already selected.
     */
    @Test
    fun the_default_window_is_tall() {
        assertTrue(PosWindow(width = 400.dp, height = 800.dp).isTall)
    }
}
