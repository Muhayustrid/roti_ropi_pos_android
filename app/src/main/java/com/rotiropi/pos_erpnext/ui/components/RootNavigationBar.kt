package com.rotiropi.pos_erpnext.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rotiropi.pos_erpnext.ui.navigation.PosDestination
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

@Composable
fun RootNavigationBar(
    selectedDestination: PosDestination,
    onDestinationSelected: (PosDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.selectableGroup(),
        tonalElevation = 3.dp,
    ) {
        PosDestination.entries.forEach { destination ->
            RootNavigationItem(
                destination = destination,
                selected = destination == selectedDestination,
                onClick = { onDestinationSelected(destination) },
            )
        }
    }
}

@Composable
private fun RowScope.RootNavigationItem(
    destination: PosDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                painter = painterResource(destination.iconRes),
                contentDescription = null,
            )
        },
        label = { Text(stringResource(destination.labelRes)) },
        modifier = Modifier
            .heightIn(min = PosDimensions.touchTarget)
            .testTag("root-${destination.route}"),
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    )
}
