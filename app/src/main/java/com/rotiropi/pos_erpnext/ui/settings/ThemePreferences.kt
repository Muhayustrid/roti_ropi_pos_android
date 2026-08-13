package com.rotiropi.pos_erpnext.ui.settings

import android.content.Context
import android.content.SharedPreferences
import com.rotiropi.pos_erpnext.ui.theme.PosAccent

class ThemePreferences(private val preferences: SharedPreferences) {

    fun read(): ThemeSelection = ThemeSelection(
        mode = parseThemeMode(preferences.getString(KEY_THEME_MODE, null)),
        accent = parseAccent(preferences.getString(KEY_ACCENT, null)),
        language = parseLanguage(preferences.getString(KEY_LANGUAGE, null)),
    )

    fun writeMode(mode: PosThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun writeAccent(accent: PosAccent) {
        preferences.edit().putString(KEY_ACCENT, accent.name).apply()
    }

    fun writeLanguage(language: PosLanguage) {
        preferences.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    companion object {
        internal const val FILE_NAME = "pos_ui_preferences"
        internal const val KEY_THEME_MODE = "theme_mode"
        internal const val KEY_ACCENT = "accent"
        internal const val KEY_LANGUAGE = "language"

        fun from(context: Context): ThemePreferences = ThemePreferences(
            context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        )

        internal fun parseThemeMode(value: String?): PosThemeMode =
            PosThemeMode.entries.firstOrNull { it.name == value } ?: PosThemeMode.SYSTEM

        internal fun parseAccent(value: String?): PosAccent =
            PosAccent.entries.firstOrNull { it.name == value } ?: PosAccent.BLUE

        internal fun parseLanguage(value: String?): PosLanguage = PosLanguage.parse(value)
    }
}
