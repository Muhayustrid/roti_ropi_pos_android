package com.rotiropi.pos_erpnext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rotiropi.pos_erpnext.ui.navigation.PosShell
import com.rotiropi.pos_erpnext.ui.settings.PosThemeMode
import com.rotiropi.pos_erpnext.ui.settings.ThemePreferences
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import com.rotiropi.pos_erpnext.ui.theme.PosTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { ThemePreferences.from(applicationContext) }
            var selection by remember { mutableStateOf(preferences.read()) }
            val darkTheme = when (selection.mode) {
                PosThemeMode.SYSTEM -> isSystemInDarkTheme()
                PosThemeMode.LIGHT -> false
                PosThemeMode.DARK -> true
            }

            PosTheme(darkTheme = darkTheme, accent = selection.accent) {
                PosShell(
                    themeMode = selection.mode,
                    accent = selection.accent,
                    onThemeModeSelected = { mode ->
                        selection = selection.copy(mode = mode)
                        preferences.writeMode(mode)
                    },
                    onAccentSelected = { accent ->
                        selection = selection.copy(accent = accent)
                        preferences.writeAccent(accent)
                    },
                )
            }
        }
    }
}
