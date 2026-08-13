package com.rotiropi.pos_erpnext.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.recovery.RecoveryScreen
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

@Composable
fun MoreScreen(
    state: MoreUiState,
    layoutMode: PosLayoutMode,
    modifier: Modifier = Modifier,
    demoToggleVisible: Boolean = false,
    logoutVisible: Boolean = false,
    onThemeModeSelected: (PosThemeMode) -> Unit = {},
    onAccentSelected: (PosAccent) -> Unit = {},
    onLanguageSelected: (PosLanguage) -> Unit = {},
    onDemoDataToggled: (Boolean) -> Unit = {},
    onLogout: () -> Unit = {},
    onOpenClosing: () -> Unit = {},
    onOpenProducts: () -> Unit = {},
    onOpenReports: () -> Unit = {},
    onAcknowledgeRecovery: (String) -> Unit = {},
    onReauthenticateRecovery: () -> Unit = {},
    onRecoverManualClosing: (String) -> Unit = {},
) {
    val rootTag = if (layoutMode == PosLayoutMode.EXPANDED) "more-expanded" else "more-compact"
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(rootTag)
            .padding(PosDimensions.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MoreHeader(demoData = state.demoData)
        if (layoutMode == PosLayoutMode.EXPANDED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutletGroup(state.outletLabel)
                    UserSessionGroup(state.userSessionLabel)
                    CatalogGroup(onOpenProducts, onOpenReports)
                    if (state.closingAvailable) ClosingGroup(onOpenClosing)
                    if (logoutVisible) {
                        LogoutGroup(state.logoutMessage, state.recovery, onLogout, onAcknowledgeRecovery, onReauthenticateRecovery, onRecoverManualClosing)
                    }
                    AppearanceGroup(
                        themeMode = state.themeMode,
                        accent = state.accent,
                        language = state.language,
                        onThemeModeSelected = onThemeModeSelected,
                        onAccentSelected = onAccentSelected,
                        onLanguageSelected = onLanguageSelected,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PrinterGroup()
                    SynchronizationGroup()
                    if (demoToggleVisible) {
                        DebugToolsGroup(state.demoData, onDemoDataToggled)
                    }
                }
            }
        } else {
            OutletGroup(state.outletLabel)
            UserSessionGroup(state.userSessionLabel)
            CatalogGroup(onOpenProducts, onOpenReports)
            if (state.closingAvailable) ClosingGroup(onOpenClosing)
            if (logoutVisible) {
                LogoutGroup(state.logoutMessage, state.recovery, onLogout, onAcknowledgeRecovery, onReauthenticateRecovery, onRecoverManualClosing)
            }
            AppearanceGroup(
                themeMode = state.themeMode,
                accent = state.accent,
                language = state.language,
                onThemeModeSelected = onThemeModeSelected,
                onAccentSelected = onAccentSelected,
                onLanguageSelected = onLanguageSelected,
            )
            PrinterGroup()
            SynchronizationGroup()
            if (demoToggleVisible) {
                DebugToolsGroup(state.demoData, onDemoDataToggled)
            }
        }
    }
}

@Composable
private fun CatalogGroup(onOpenProducts: () -> Unit, onOpenReports: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.more_group_catalog), style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = onOpenProducts,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PosDimensions.touchTarget)
                    .testTag("more-products"),
            ) {
                Text(stringResource(R.string.more_products))
            }
            Button(
                onClick = onOpenReports,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PosDimensions.touchTarget)
                    .testTag("more-reports"),
            ) {
                Text(stringResource(R.string.more_reports))
            }
        }
    }
}

@Composable
private fun DebugToolsGroup(demoData: Boolean, onDemoDataToggled: (Boolean) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.more_group_debug), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.more_debug_detail),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            FilterChip(
                selected = demoData,
                onClick = { onDemoDataToggled(!demoData) },
                label = { Text(stringResource(R.string.more_demo_layout)) },
                modifier = Modifier
                    .heightIn(min = PosDimensions.touchTarget)
                    .testTag("more-demo-data"),
            )
        }
    }
}

@Composable
private fun MoreHeader(demoData: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.more_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (demoData) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = stringResource(R.string.badge_demo_data),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun OutletGroup(outletLabel: String?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.more_group_outlet), style = MaterialTheme.typography.titleMedium)
            Text(
                outletLabel ?: stringResource(R.string.state_unavailable),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun UserSessionGroup(userSessionLabel: String?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.more_group_user_session), style = MaterialTheme.typography.titleMedium)
            Text(
                userSessionLabel ?: stringResource(R.string.state_unavailable),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ClosingGroup(onOpenClosing: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.more_group_session), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.more_session_detail),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onOpenClosing,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PosDimensions.touchTarget)
                    .testTag("more-closing"),
            ) {
                Text(stringResource(R.string.more_closing))
            }
        }
    }
}

@Composable
private fun LogoutGroup(
    message: String?,
    recovery: com.rotiropi.pos_erpnext.recovery.RecoveryScreenState,
    onLogout: () -> Unit,
    onAcknowledgeRecovery: (String) -> Unit,
    onReauthenticateRecovery: () -> Unit,
    onRecoverManualClosing: (String) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PosDimensions.touchTarget)
                    .testTag("more-logout"),
            ) {
                Text(stringResource(R.string.more_sign_out))
            }
            if (message != null) {
                Text(message, modifier = Modifier.testTag("more-logout-blocked"))
            }
            RecoveryScreen(
                state = recovery,
                onAcknowledge = {
                    (recovery as? com.rotiropi.pos_erpnext.recovery.RecoveryScreenState.Terminal)
                        ?.transactionId
                        ?.let(onAcknowledgeRecovery)
                },
                onReauthenticate = onReauthenticateRecovery,
                onRecoverClosing = {
                    (recovery as? com.rotiropi.pos_erpnext.recovery.RecoveryScreenState.ManualRecovery)
                        ?.takeIf { it.canRecoverClosing }
                        ?.transactionId
                        ?.let(onRecoverManualClosing)
                },
            )
        }
    }
}

@Composable
private fun AppearanceGroup(
    themeMode: PosThemeMode,
    accent: PosAccent,
    language: PosLanguage,
    onThemeModeSelected: (PosThemeMode) -> Unit,
    onAccentSelected: (PosAccent) -> Unit,
    onLanguageSelected: (PosLanguage) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.more_group_appearance), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.more_theme_mode), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PosThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == themeMode,
                        onClick = { onThemeModeSelected(mode) },
                        label = { Text(stringResource(mode.labelRes)) },
                        modifier = Modifier
                            .heightIn(min = PosDimensions.touchTarget)
                            .testTag("more-theme-${mode.name.lowercase()}"),
                    )
                }
            }
            Text(stringResource(R.string.more_accent_color), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PosAccent.entries.forEach { item ->
                    FilterChip(
                        selected = item == accent,
                        onClick = { onAccentSelected(item) },
                        label = { Text(stringResource(item.labelRes)) },
                        modifier = Modifier
                            .heightIn(min = PosDimensions.touchTarget)
                            .testTag("more-accent-${item.name.lowercase()}"),
                    )
                }
            }
            Text(stringResource(R.string.more_language), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PosLanguage.entries.forEach { item ->
                    FilterChip(
                        selected = item == language,
                        onClick = { onLanguageSelected(item) },
                        label = { Text(item.label) },
                        modifier = Modifier
                            .heightIn(min = PosDimensions.touchTarget)
                            .testTag("more-language-${item.tag}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrinterGroup() {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("more-printer")
            .semantics { disabled() },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.more_group_printer), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.state_not_supported),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SynchronizationGroup() {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("more-synchronization")
            .semantics { disabled() },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.more_group_synchronization), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.state_not_supported),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
