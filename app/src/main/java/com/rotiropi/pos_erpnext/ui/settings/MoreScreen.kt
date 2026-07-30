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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.theme.PosAccent
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

@Composable
fun MoreScreen(
    state: MoreUiState,
    layoutMode: PosLayoutMode,
    modifier: Modifier = Modifier,
    onThemeModeSelected: (PosThemeMode) -> Unit = {},
    onAccentSelected: (PosAccent) -> Unit = {},
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
                    AppearanceGroup(
                        themeMode = state.themeMode,
                        accent = state.accent,
                        onThemeModeSelected = onThemeModeSelected,
                        onAccentSelected = onAccentSelected,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PrinterGroup()
                    SynchronizationGroup()
                }
            }
        } else {
            OutletGroup(state.outletLabel)
            UserSessionGroup(state.userSessionLabel)
            AppearanceGroup(
                themeMode = state.themeMode,
                accent = state.accent,
                onThemeModeSelected = onThemeModeSelected,
                onAccentSelected = onAccentSelected,
            )
            PrinterGroup()
            SynchronizationGroup()
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
            text = "More",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (demoData) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = "Demo data",
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
            Text("Outlet", style = MaterialTheme.typography.titleMedium)
            Text(
                outletLabel ?: "Unavailable",
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
            Text("User and session", style = MaterialTheme.typography.titleMedium)
            Text(
                userSessionLabel ?: "Unavailable",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AppearanceGroup(
    themeMode: PosThemeMode,
    accent: PosAccent,
    onThemeModeSelected: (PosThemeMode) -> Unit,
    onAccentSelected: (PosAccent) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Text("Theme mode", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PosThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == themeMode,
                        onClick = { onThemeModeSelected(mode) },
                        label = { Text(mode.label) },
                        modifier = Modifier
                            .heightIn(min = PosDimensions.touchTarget)
                            .testTag("more-theme-${mode.name.lowercase()}"),
                    )
                }
            }
            Text("Accent color", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PosAccent.entries.forEach { item ->
                    FilterChip(
                        selected = item == accent,
                        onClick = { onAccentSelected(item) },
                        label = { Text(item.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier
                            .heightIn(min = PosDimensions.touchTarget)
                            .testTag("more-accent-${item.name.lowercase()}"),
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
            Text("Printer", style = MaterialTheme.typography.titleMedium)
            Text(
                "Not supported",
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
            Text("Synchronization", style = MaterialTheme.typography.titleMedium)
            Text(
                "Not supported",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
