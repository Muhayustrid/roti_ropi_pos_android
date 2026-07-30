package com.rotiropi.pos_erpnext.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun PosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: PosAccent = PosAccent.BLUE,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = posColorScheme(darkTheme, accent),
        typography = PosTypography,
        shapes = PosShapes,
        content = content,
    )
}
