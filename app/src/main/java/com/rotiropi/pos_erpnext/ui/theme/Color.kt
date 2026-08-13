package com.rotiropi.pos_erpnext.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.rotiropi.pos_erpnext.R

enum class PosAccent(@param:StringRes val labelRes: Int) {
    BLUE(R.string.more_accent_blue),
    TEAL(R.string.more_accent_teal),
}

private val BlueLight = Color(0xFF2457C5)
private val BlueDark = Color(0xFFB4C5FF)
private val TealLight = Color(0xFF006B5F)
private val TealDark = Color(0xFF58DBC7)

fun posColorScheme(darkTheme: Boolean, accent: PosAccent): ColorScheme {
    val primary = when (accent) {
        PosAccent.BLUE -> if (darkTheme) BlueDark else BlueLight
        PosAccent.TEAL -> if (darkTheme) TealDark else TealLight
    }
    return if (darkTheme) {
        darkColorScheme(
            primary = primary,
            secondary = TealDark,
            background = Color(0xFF111318),
            surface = Color(0xFF191C20),
            surfaceVariant = Color(0xFF40434A),
        )
    } else {
        lightColorScheme(
            primary = primary,
            secondary = TealLight,
            background = Color(0xFFF7F8FC),
            surface = Color.White,
            surfaceVariant = Color(0xFFE5E7ED),
        )
    }
}
