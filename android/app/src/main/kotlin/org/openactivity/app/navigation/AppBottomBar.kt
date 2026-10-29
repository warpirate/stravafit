// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.RidgelineTheme

// Same height as a 24 dp icon so the Record label lines up with the other labels.
private val RecordPillWidth = 52.dp
private val RecordPillHeight = 24.dp
private val RecordGlyph = 12.dp

/**
 * Bottom navigation bar. The centre Record item is a filled primary pill so it reads as the
 * app's main action, and never shows a selected indicator because it is not a tab.
 */
@Composable
fun AppBottomBar(
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        TopLevelDestination.entries.forEach { destination ->
            val isSelected = destination == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(destination) },
                icon = {
                    if (destination.isAction) {
                        RecordNavIcon()
                    } else {
                        Icon(if (isSelected) destination.selectedIcon else destination.unselectedIcon, contentDescription = null)
                    }
                },
                label = {
                    Text(
                        destination.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            destination.isAction -> MaterialTheme.colorScheme.primary
                            isSelected -> MaterialTheme.colorScheme.onSurface
                            else -> RidgelineTheme.colors.textSecondary
                        },
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = if (destination.isAction) Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = RidgelineTheme.colors.textSecondary,
                ),
            )
        }
    }
}

@Composable
private fun RecordNavIcon() {
    Box(
        Modifier
            .size(width = RecordPillWidth, height = RecordPillHeight)
            .background(MaterialTheme.colorScheme.primary, PillShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(RecordGlyph)
                .background(MaterialTheme.colorScheme.onPrimary, PillShape),
        )
    }
}
