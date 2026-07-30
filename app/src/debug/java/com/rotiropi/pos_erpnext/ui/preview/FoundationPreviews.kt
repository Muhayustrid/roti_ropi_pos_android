package com.rotiropi.pos_erpnext.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rotiropi.pos_erpnext.ui.navigation.PosShell
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import com.rotiropi.pos_erpnext.ui.theme.PosTheme

@Preview(name = "Phone light blue", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun FoundationShellPreview() {
    FoundationPreview(darkTheme = false, accent = PosAccent.BLUE)
}

@Preview(name = "Phone dark teal", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun FoundationDarkTealPreview() {
    FoundationPreview(darkTheme = true, accent = PosAccent.TEAL)
}

@Preview(name = "Phone landscape 1.5x", widthDp = 800, heightDp = 360, fontScale = 1.5f)
@Composable
fun FoundationPhoneLandscapeFontScalePreview() {
    FoundationPreview(darkTheme = false, accent = PosAccent.BLUE)
}

@Preview(name = "Tablet portrait", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
fun FoundationTabletPortraitPreview() {
    FoundationPreview(darkTheme = false, accent = PosAccent.BLUE)
}

@Preview(name = "Tablet landscape", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
fun FoundationTabletLandscapePreview() {
    FoundationPreview(darkTheme = true, accent = PosAccent.TEAL)
}

@Composable
private fun FoundationPreview(darkTheme: Boolean, accent: PosAccent) {
    PosTheme(darkTheme = darkTheme, accent = accent) {
        PosShell()
    }
}
