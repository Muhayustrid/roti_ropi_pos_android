package com.rotiropi.pos_erpnext.ui.settings

import com.rotiropi.pos_erpnext.recovery.RecoveryScreenState
import com.rotiropi.pos_erpnext.ui.theme.PosAccent

enum class PosThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

data class ThemeSelection(
    val mode: PosThemeMode = PosThemeMode.SYSTEM,
    val accent: PosAccent = PosAccent.BLUE,
)

data class MoreUiState(
    val outletLabel: String?,
    val userSessionLabel: String?,
    val themeMode: PosThemeMode,
    val accent: PosAccent,
    val demoData: Boolean,
    val logoutMessage: String? = null,
    val recovery: RecoveryScreenState = RecoveryScreenState.Hidden,
    val closingAvailable: Boolean = false,
)
