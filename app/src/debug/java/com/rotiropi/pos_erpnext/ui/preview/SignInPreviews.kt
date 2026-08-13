package com.rotiropi.pos_erpnext.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rotiropi.pos_erpnext.ui.auth.SignInScreen
import com.rotiropi.pos_erpnext.ui.theme.WarmCommerceTheme

// Fixture-only ERPNext origin used for design-time previews. This value is a preview
// fixture only; production uses the canonical origin supplied by the application.
private const val PREVIEW_ORIGIN = "https://erpnext.rotiropi.example"

@Preview(name = "Sign in phone light", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun SignInPhonePreview() {
    WarmCommerceTheme {
        SignInScreen(serverOrigin = PREVIEW_ORIGIN)
    }
}

@Preview(name = "Sign in phone dark", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun SignInDarkPreview() {
    WarmCommerceTheme(darkTheme = true) {
        SignInScreen(serverOrigin = PREVIEW_ORIGIN)
    }
}

@Preview(name = "Sign in without origin", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun SignInNoOriginPreview() {
    WarmCommerceTheme {
        SignInScreen(serverOrigin = null)
    }
}

@Preview(name = "Sign in waiting", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun SignInWaitingPreview() {
    WarmCommerceTheme {
        SignInScreen(signingIn = true, serverOrigin = PREVIEW_ORIGIN)
    }
}

@Preview(name = "Sign in error", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun SignInErrorPreview() {
    WarmCommerceTheme {
        SignInScreen(
            errorMessage = "Sign-in could not be completed. Please try again.",
            serverOrigin = PREVIEW_ORIGIN,
        )
    }
}

@Preview(name = "Sign in landscape 1.5x", widthDp = 800, heightDp = 360, fontScale = 1.5f)
@Composable
fun SignInLandscapeFontScalePreview() {
    WarmCommerceTheme {
        SignInScreen(serverOrigin = PREVIEW_ORIGIN)
    }
}
