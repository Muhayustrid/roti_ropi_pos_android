package com.rotiropi.pos_erpnext.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rotiropi.pos_erpnext.recovery.RecoveryScreenState
import com.rotiropi.pos_erpnext.ui.opening.OpeningRowUiState
import com.rotiropi.pos_erpnext.ui.opening.OpeningScreen
import com.rotiropi.pos_erpnext.ui.opening.OpeningUiState
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import com.rotiropi.pos_erpnext.ui.theme.PosTheme

private val openingRows = listOf(
    OpeningRowUiState("Cash", "0.00", true),
    OpeningRowUiState("Bank Transfer", "10000,50", true),
    OpeningRowUiState("Card", "12.30", false),
)

private val openingState = OpeningUiState(
    profileName = "PROFILE-EXAMPLE",
    currency = "IDR",
    rows = openingRows,
    canSubmit = true,
)

@Preview(name = "Opening phone light", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun OpeningPhonePreview() {
    OpeningPreview(darkTheme = false, accent = PosAccent.BLUE)
}

@Preview(name = "Opening phone dark", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun OpeningDarkPreview() {
    OpeningPreview(darkTheme = true, accent = PosAccent.TEAL)
}

@Preview(name = "Opening landscape 1.5x", widthDp = 800, heightDp = 360, fontScale = 1.5f)
@Composable
fun OpeningLandscapeFontScalePreview() {
    OpeningPreview(darkTheme = false, accent = PosAccent.BLUE)
}

@Preview(name = "Opening validation", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun OpeningValidationPreview() {
    PosTheme {
        OpeningScreen(
            state = openingState.copy(
                rows = listOf(
                    OpeningRowUiState("Cash", "1.001", true, "Use at most 2 decimal places."),
                    openingRows[1],
                    openingRows[2],
                ),
                canSubmit = false,
            ),
            onAmountChanged = { _, _ -> },
            onSubmit = {},
        )
    }
}

@Preview(name = "Opening recovery pending", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun OpeningRecoveryPreview() {
    PosTheme {
        OpeningScreen(
            state = openingState.copy(recoveryPending = true),
            recoveryState = RecoveryScreenState.AuthenticationRequired("transaction-1"),
            onAmountChanged = { _, _ -> },
            onSubmit = {},
        )
    }
}

@Composable
private fun OpeningPreview(darkTheme: Boolean, accent: PosAccent) {
    PosTheme(darkTheme = darkTheme, accent = accent) {
        OpeningScreen(
            state = openingState,
            onAmountChanged = { _, _ -> },
            onSubmit = {},
        )
    }
}
