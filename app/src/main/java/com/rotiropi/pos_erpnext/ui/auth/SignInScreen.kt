package com.rotiropi.pos_erpnext.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.ui.theme.WarmCommerceDimensions

/**
 * OAuth sign-in landing surface (Warm Commerce).
 *
 * The only primary action launches the existing system browser / Custom Tab
 * authorization via [onSignInClick]. This screen never captures an email,
 * password, PIN, or server address: the ERPNext origin is shown only as
 * read-only environment information when [serverOrigin] is provided.
 */
@Composable
fun SignInScreen(
    onSignInClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    signingIn: Boolean = false,
    serverOrigin: String? = null,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WarmCommerceDimensions.screenMargin * 1.5f)
                .padding(vertical = WarmCommerceDimensions.screenMargin * 2f)
                .testTag("sign-in-screen"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Roti Ropi",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Point of Sale System",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(WarmCommerceDimensions.screenMargin * 2f))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sign-in-card"),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = WarmCommerceDimensions.elevationSurface,
            ) {
                Column(
                    modifier = Modifier.padding(WarmCommerceDimensions.containerPadding),
                    verticalArrangement = Arrangement.spacedBy(WarmCommerceDimensions.gutter),
                ) {
                    if (!serverOrigin.isNullOrBlank()) {
                        Text(
                            text = "ERPNext server",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = serverOrigin,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sign-in-server-origin"),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = "You will sign in securely through ERPNext.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            errorMessage?.let {
                Spacer(modifier = Modifier.height(WarmCommerceDimensions.stackGap))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sign-in-error")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            Spacer(modifier = Modifier.height(WarmCommerceDimensions.screenMargin * 1.5f))

            if (signingIn) {
                CircularProgressIndicator(
                    modifier = Modifier.testTag("sign-in-progress"),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(WarmCommerceDimensions.stackGap))
                Text(
                    text = "Waiting for ERPNext sign-in…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("sign-in-waiting"),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Please complete the authentication process in the browser window that opened.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Button(
                    onClick = onSignInClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = WarmCommerceDimensions.touchTarget)
                        .testTag("sign-in-button"),
                ) {
                    Text("Continue with ERPNext")
                }
            }

            Spacer(modifier = Modifier.height(WarmCommerceDimensions.screenMargin))
            Text(
                text = "Your password is entered only on the secure ERPNext sign-in page.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
