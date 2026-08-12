package com.rotiropi.pos_erpnext.ui

import com.rotiropi.pos_erpnext.ui.navigation.PosDestination
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.navigation.parentDestinationOf
import com.rotiropi.pos_erpnext.ui.navigation.posLayoutModeForWidth
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import com.rotiropi.pos_erpnext.ui.theme.PosShapes
import com.rotiropi.pos_erpnext.ui.theme.posColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PosFoundationTest {

    @Test
    fun root_destinations_are_unique_and_start_with_cashier() {
        assertEquals(
            listOf("cashier", "history", "more"),
            PosDestination.entries.map { it.route }
        )
        assertEquals(PosDestination.CASHIER, PosDestination.entries.first())
    }

    @Test
    fun child_routes_keep_their_parent_tab_selected() {
        assertEquals(PosDestination.MORE, parentDestinationOf("products"))
        assertEquals(PosDestination.MORE, parentDestinationOf("reports"))
        assertEquals(PosDestination.MORE, parentDestinationOf("closing"))
        assertEquals(PosDestination.HISTORY, parentDestinationOf("sale/SINV-0001"))
        assertEquals(PosDestination.HISTORY, parentDestinationOf("return/SINV-0001"))
        assertEquals(PosDestination.CASHIER, parentDestinationOf(null))
    }

    @Test
    fun layout_switches_at_tablet_width() {
        assertEquals(PosLayoutMode.COMPACT, posLayoutModeForWidth(599))
        assertEquals(PosLayoutMode.EXPANDED, posLayoutModeForWidth(600))
    }

    @Test
    fun theme_uses_twelve_dp_card_radius() {
        assertEquals(
            12f,
            PosShapes.medium.topStart.toPx(
                androidx.compose.ui.geometry.Size.Zero,
                androidx.compose.ui.unit.Density(1f),
            )
        )
    }

    @Test
    fun theme_distinguishes_light_dark_and_supported_accents() {
        assertNotEquals(
            posColorScheme(darkTheme = false, accent = PosAccent.BLUE).background,
            posColorScheme(darkTheme = true, accent = PosAccent.BLUE).background
        )
        assertNotEquals(
            posColorScheme(darkTheme = false, accent = PosAccent.BLUE).primary,
            posColorScheme(darkTheme = false, accent = PosAccent.TEAL).primary
        )
    }
}
