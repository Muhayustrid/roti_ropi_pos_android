package com.rotiropi.pos_erpnext.ui

import com.rotiropi.pos_erpnext.ui.settings.PosThemeMode
import com.rotiropi.pos_erpnext.ui.settings.ThemePreferences
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import org.junit.Assert.assertEquals
import org.junit.Test

class MoreStateTest {

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
