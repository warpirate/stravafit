// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/**
 * Bottom navigation (strava direction): Home · Maps · Record · Groups · You, five equal slots with a 24 dp
 * icon over an 11 dp label. The active tab shows a filled orange glyph and an orange label; there is no
 * pill indicator. Record is an outlined record-circle glyph like the other tabs. In landscape the bar is
 * compact (icon beside nothing but its label, 48 dp) so it does not eat a quarter of the height.
 */
@Composable
fun AppBottomBar(
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
            HorizontalDivider(color = RidgelineTheme.colors.hairline)
            Row(Modifier.fillMaxWidth().height(if (landscape) COMPACT_HEIGHT else BAR_HEIGHT)) {
                TopLevelDestination.entries.forEach { destination ->
                    val isSelected = destination == selected
                    NavSlot(destination, isSelected, compact = landscape, onClick = { onSelect(destination) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NavSlot(
    destination: TopLevelDestination,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val tint = if (selected) RidgelineTheme.colors.accent else RidgelineTheme.colors.textMuted
    val labelColor = if (selected) RidgelineTheme.colors.accentText else RidgelineTheme.colors.textMuted
    val icon = if (selected) destination.selectedIcon else destination.unselectedIcon
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        if (compact) Spacer(Modifier.width(Spacing.s)) else Spacer(Modifier.height(2.dp))
        Text(destination.label, style = RidgelineTheme.text.nav, color = labelColor, maxLines = 1)
    }
    val base = modifier
        .fillMaxHeight()
        .selectable(selected = selected, role = Role.Tab, onClick = onClick)
        .semantics { contentDescription = destination.label }
    if (compact) {
        Row(base, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { content() }
    } else {
        Column(base, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { content() }
    }
}

/**
 * "● Recording · 4:38" bar shown above the bottom navigation while a recording runs in the background
 * (after Back or minimise), so the athlete always sees it and can get back to it with one tap.
 */
@Composable
fun ActiveRecordingBar(time: String, paused: Boolean, sport: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (paused) "Paused" else "Recording"
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = if (paused) RidgelineTheme.colors.paused else RidgelineTheme.colors.accent,
        contentColor = if (paused) RidgelineTheme.colors.onPaused else RidgelineTheme.colors.onAccent,
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        if (paused) RidgelineTheme.colors.pausedDot else RidgelineTheme.colors.onAccent,
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(Spacing.s))
            Text("$label · $sport", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(time, style = RidgelineTheme.text.statS)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        }
    }
}

private val BAR_HEIGHT = 60.dp
private val COMPACT_HEIGHT = 48.dp
