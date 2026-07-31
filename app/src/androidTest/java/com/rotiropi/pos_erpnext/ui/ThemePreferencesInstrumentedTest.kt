package com.rotiropi.pos_erpnext.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.ui.settings.PosThemeMode
import com.rotiropi.pos_erpnext.ui.settings.ThemePreferences
import com.rotiropi.pos_erpnext.ui.settings.ThemeSelection
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePreferencesInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences(
        "theme_preferences_instrumented_test",
        Context.MODE_PRIVATE,
    )

    @Before
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @After
    fun clearPreferencesAfterTest() {
        preferences.edit().clear().commit()
    }

    @Test
    fun selections_round_trip_through_application_private_preferences() {
        val store = ThemePreferences(preferences)
        store.writeMode(PosThemeMode.DARK)
        store.writeAccent(PosAccent.TEAL)

        assertEquals(ThemeSelection(PosThemeMode.DARK, PosAccent.TEAL), store.read())
    }

    @Test
    fun corrupt_values_read_as_safe_defaults() {
        preferences.edit()
            .putString("theme_mode", "BROKEN")
            .putString("accent", "ORANGE")
            .commit()

        assertEquals(ThemeSelection(), ThemePreferences(preferences).read())
    }
}
