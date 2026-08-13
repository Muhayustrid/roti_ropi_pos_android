package com.rotiropi.pos_erpnext.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.settings.MoreScreen
import com.rotiropi.pos_erpnext.ui.settings.MoreUiState
import com.rotiropi.pos_erpnext.ui.settings.PosThemeMode
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import com.rotiropi.pos_erpnext.ui.theme.PosTheme

private fun moreDemoState(themeMode: PosThemeMode, accent: PosAccent) = MoreUiState(
    outletLabel = "Outlet Menteng",
    userSessionLabel = "Ayu · Open session",
    themeMode = themeMode,
    accent = accent,
)

@Preview(name = "More compact dark Blue", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun MoreCompactPreview() {
    PosTheme(darkTheme = true, accent = PosAccent.BLUE) {
        MoreScreen(moreDemoState(PosThemeMode.DARK, PosAccent.BLUE), PosLayoutMode.COMPACT)
    }
}

@Preview(name = "More expanded light Teal", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
fun MoreExpandedPreview() {
    PosTheme(darkTheme = false, accent = PosAccent.TEAL) {
        MoreScreen(moreDemoState(PosThemeMode.LIGHT, PosAccent.TEAL), PosLayoutMode.EXPANDED)
    }
}

@Preview(name = "More font 1.5x", widthDp = 800, heightDp = 1280, fontScale = 1.5f)
@Composable
fun MoreFontScalePreview() {
    PosTheme(darkTheme = false, accent = PosAccent.TEAL) {
        MoreScreen(moreDemoState(PosThemeMode.SYSTEM, PosAccent.TEAL), PosLayoutMode.EXPANDED)
    }
}
