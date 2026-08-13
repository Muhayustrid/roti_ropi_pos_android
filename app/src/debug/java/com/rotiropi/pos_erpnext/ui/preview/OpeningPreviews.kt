package com.rotiropi.pos_erpnext.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rotiropi.pos_erpnext.recovery.RecoveryScreenState
import com.rotiropi.pos_erpnext.ui.UiText
import com.rotiropi.pos_erpnext.ui.opening.OpeningConfirmSheet
import com.rotiropi.pos_erpnext.ui.opening.OpeningRowUiState
import com.rotiropi.pos_erpnext.ui.opening.OpeningScreen
import com.rotiropi.pos_erpnext.ui.opening.OpeningUiState
import com.rotiropi.pos_erpnext.ui.theme.WarmCommerceTheme

// Fixture-only payment rows for design-time previews. Cash and QRIS here are preview
// fixtures and never ship to release runtime (this file lives in the debug source set).
private val openingRows = listOf(
    OpeningRowUiState("Cash", "200000", true),
    OpeningRowUiState("QRIS", "0.00", true),
    OpeningRowUiState("Bank Transfer", "10000,50", false),
)

private val openingState = OpeningUiState(
    profileName = "Main Counter 01",
    cashier = "siti.rahma@rotiropi.example",
    company = "Roti Ropi Bakery",
    warehouse = "Central Kitchen",
    currency = "IDR",
    rows = openingRows,
    canSubmit = true,
)

@Preview(name = "Opening phone light", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun OpeningPhonePreview() {
    OpeningPreview(darkTheme = false)
}

@Preview(name = "Opening phone dark", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun OpeningDarkPreview() {
    OpeningPreview(darkTheme = true)
}

@Preview(name = "Opening landscape 1.5x", widthDp = 800, heightDp = 360, fontScale = 1.5f)
@Composable
fun OpeningLandscapeFontScalePreview() {
    OpeningPreview(darkTheme = false)
}

@Preview(name = "Opening validation", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun OpeningValidationPreview() {
    WarmCommerceTheme {
        OpeningScreen(
            state = openingState.copy(
                rows = listOf(
                    OpeningRowUiState("Cash", "1.001", true, UiText.Raw("Use at most 2 decimal places.")),
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
    WarmCommerceTheme {
        OpeningScreen(
            state = openingState.copy(recoveryPending = true),
            recoveryState = RecoveryScreenState.AuthenticationRequired("transaction-1"),
            onAmountChanged = { _, _ -> },
            onSubmit = {},
        )
    }
}

@Preview(name = "Opening reconciling", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun OpeningReconcilingPreview() {
    WarmCommerceTheme {
        OpeningScreen(
            state = openingState.copy(reconciling = true),
            onAmountChanged = { _, _ -> },
            onSubmit = {},
        )
    }
}

@Preview(name = "Opening unavailable", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun OpeningUnavailablePreview() {
    WarmCommerceTheme {
        OpeningScreen(
            state = OpeningUiState(unavailable = true, error = UiText.Raw("Opening configuration is unavailable.")),
            onAmountChanged = { _, _ -> },
            onSubmit = {},
        )
    }
}

@Preview(name = "Opening confirm sheet", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun OpeningConfirmSheetPreview() {
    WarmCommerceTheme {
        OpeningConfirmSheet(
            state = openingState,
            onDismiss = {},
            onEditAmounts = {},
            onConfirm = {},
        )
    }
}

@Preview(name = "Opening confirm sheet submitting", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun OpeningConfirmSheetSubmittingPreview() {
    WarmCommerceTheme {
        OpeningConfirmSheet(
            state = openingState.copy(submitting = true, canSubmit = false),
            onDismiss = {},
            onEditAmounts = {},
            onConfirm = {},
        )
    }
}

@Composable
private fun OpeningPreview(darkTheme: Boolean) {
    WarmCommerceTheme(darkTheme = darkTheme) {
        OpeningScreen(
            state = openingState,
            onAmountChanged = { _, _ -> },
            onSubmit = {},
        )
    }
}
