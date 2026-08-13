package com.rotiropi.pos_erpnext.ui.settings

import androidx.annotation.StringRes
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.recovery.RecoveryScreenState
import com.rotiropi.pos_erpnext.ui.theme.PosAccent

enum class PosThemeMode(@param:StringRes val labelRes: Int) {
    SYSTEM(R.string.more_theme_system),
    LIGHT(R.string.more_theme_light),
    DARK(R.string.more_theme_dark),
}

data class ThemeSelection(
    val mode: PosThemeMode = PosThemeMode.SYSTEM,
    val accent: PosAccent = PosAccent.BLUE,
    val language: PosLanguage = PosLanguage.DEFAULT,
)

data class MoreUiState(
    val outletLabel: String?,
    val userSessionLabel: String?,
    val themeMode: PosThemeMode,
    val accent: PosAccent,
    val demoData: Boolean,
    val language: PosLanguage = PosLanguage.DEFAULT,
    val logoutMessage: String? = null,
    val recovery: RecoveryScreenState = RecoveryScreenState.Hidden,
    val closingAvailable: Boolean = false,
)
