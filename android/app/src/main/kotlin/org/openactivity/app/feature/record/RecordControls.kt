// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.record

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openactivity.app.model.Sport
import org.openactivity.app.ui.common.icon
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

private val SideButtonSize = 56.dp

/** Auto-pause, audio cues and sensors shortcuts above the Start row. */
@Composable
internal fun QuickSettingsRow(state: RecordUiState, actions: RecordActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        ToggleTile(
            icon = Icons.Rounded.PauseCircle,
            label = "Auto-pause",
            checked = state.autoPause,
            onToggle = actions.onToggleAutoPause,
            modifier = Modifier.weight(1f),
        )
        ToggleTile(
            icon = Icons.Rounded.RecordVoiceOver,
            label = "Audio cues",
            checked = state.audioCues,
            onToggle = actions.onToggleAudioCues,
            modifier = Modifier.weight(1f),
        )
        ActionTile(
            icon = Icons.Rounded.Bluetooth,
            label = "Sensors",
            value = "None paired",
            onClick = actions.onOpenSensors,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ToggleTile(icon: ImageVector, label: String, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() }),
        shape = MaterialTheme.shapes.medium,
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else RidgelineTheme.colors.surfaceSunken,
        border = if (checked) null else BorderStroke(1.dp, RidgelineTheme.colors.hairline),
    ) {
        TileContent(
            icon = icon,
            label = label,
            value = if (checked) "On" else "Off",
            accent = checked,
        )
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = RidgelineTheme.colors.surfaceSunken,
        border = BorderStroke(1.dp, RidgelineTheme.colors.hairline),
    ) {
        TileContent(icon = icon, label = label, value = value, accent = false)
    }
}

@Composable
private fun TileContent(icon: ImageVector, label: String, value: String, accent: Boolean) {
    val content = if (accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val secondary = if (accent) MaterialTheme.colorScheme.onPrimaryContainer else RidgelineTheme.colors.textMuted
    Column(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m)) {
        Icon(icon, contentDescription = null, tint = if (accent) MaterialTheme.colorScheme.primary else RidgelineTheme.colors.textSecondary)
        Spacer(Modifier.height(Spacing.s))
        Text(label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = RidgelineTheme.text.caption, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Current sport glyph beside Start; opens the sport picker (record detail 2.1). */
@Composable
internal fun SportButton(sport: Sport, onClick: () -> Unit) {
    SideButton(
        onClick = onClick,
        description = "Sport: ${sport.label}. Change sport",
        filled = true,
        icon = sport.icon,
        label = sport.label,
        trailing = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, Modifier.size(18.dp)) },
    )
}

/** Loads a saved route to follow (record detail 2.6). Disabled for indoor sports. */
@Composable
internal fun RouteButton(enabled: Boolean, onClick: () -> Unit) {
    SideButton(
        onClick = onClick,
        description = "Add a route",
        filled = false,
        icon = Icons.Rounded.Route,
        label = "Route",
        enabled = enabled,
    )
}

@Composable
private fun SideButton(
    onClick: () -> Unit,
    description: String,
    filled: Boolean,
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = PillShape,
            color = if (filled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (filled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            border = if (filled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.size(SideButtonSize),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            trailing()
        }
    }
}

private const val DISABLED_ALPHA = 0.38f
