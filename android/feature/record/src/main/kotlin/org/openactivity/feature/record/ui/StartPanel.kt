// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.component.RecordButton
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.SheetTopShape
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.feature.record.R
import org.openactivity.feature.record.RecordSheet
import org.openactivity.feature.record.RecordUiState
import org.openactivity.feature.record.RouteChoice
import org.openactivity.feature.record.format.RecordFormat
import org.openactivity.model.SportType

/** Bottom panel of the pre-start screen: chosen route, quick toggles, then Sport · START · Route. */
@Composable
internal fun StartPanel(state: RecordUiState, actions: RecordActions) {
    Surface(shape = SheetTopShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Spacing.gutter)
                .padding(top = Spacing.s, bottom = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(width = 32.dp, height = 4.dp).background(RidgelineTheme.colors.hairline, PillShape))
            Spacer(Modifier.height(Spacing.l))
            val route = state.route
            if (route != null && state.sport.usesGps) {
                RouteRow(route, state, onRemove = { actions.onSelectRoute(null) })
                Spacer(Modifier.height(Spacing.m))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                ToggleTile(
                    icon = Icons.Rounded.PauseCircle,
                    label = stringResource(R.string.record_auto_pause),
                    checked = state.autoPause,
                    onToggle = actions.onToggleAutoPause,
                    modifier = Modifier.weight(1f),
                )
                ToggleTile(
                    icon = Icons.Rounded.RecordVoiceOver,
                    label = stringResource(R.string.record_audio_cues),
                    checked = state.audioCues,
                    onToggle = actions.onToggleAudioCues,
                    modifier = Modifier.weight(1f),
                )
                ToggleTile(
                    icon = Icons.Rounded.HourglassTop,
                    label = stringResource(R.string.record_countdown),
                    checked = state.countdown,
                    onToggle = { actions.onSetCountdown(!state.countdown) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Spacing.xl))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SportButton(state.sport) { actions.onShowSheet(RecordSheet.SportPicker) }
                }
                RecordButton(
                    state = state.startButtonState,
                    onClick = actions.onStart,
                    label = stringResource(R.string.record_start),
                    diameter = 80.dp,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SideButton(
                        onClick = { actions.onShowSheet(RecordSheet.RoutePicker) },
                        description = stringResource(if (route == null) R.string.record_add_route else R.string.record_change_route),
                        filled = route != null,
                        icon = Icons.Rounded.Route,
                        label = stringResource(R.string.record_route),
                        enabled = state.sport.usesGps,
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteRow(route: RouteChoice, state: RecordUiState, onRemove: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = RidgelineTheme.colors.surfaceSunken, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Route, contentDescription = null, tint = RidgelineTheme.colors.info)
            Spacer(Modifier.width(Spacing.s))
            Column(Modifier.weight(1f)) {
                Text(route.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${RecordFormat.distance(route.distanceMeters, state.units)} ${RecordFormat.distanceUnit(state.units)}",
                    style = RidgelineTheme.text.caption,
                    color = RidgelineTheme.colors.textSecondary,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.record_remove_route))
            }
        }
    }
}

@Composable
private fun ToggleTile(icon: ImageVector, label: String, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val accent = checked
    Surface(
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() }),
        shape = MaterialTheme.shapes.medium,
        color = if (accent) MaterialTheme.colorScheme.primaryContainer else RidgelineTheme.colors.surfaceSunken,
        border = if (accent) null else BorderStroke(1.dp, RidgelineTheme.colors.hairline),
    ) {
        val content = if (accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        Column(Modifier.padding(Spacing.m)) {
            Icon(icon, contentDescription = null, tint = if (accent) MaterialTheme.colorScheme.primary else RidgelineTheme.colors.textSecondary)
            Spacer(Modifier.height(Spacing.s))
            Text(label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(if (checked) R.string.record_on else R.string.record_off),
                style = RidgelineTheme.text.caption,
                color = if (accent) content else RidgelineTheme.colors.textMuted,
            )
        }
    }
}

/** Current sport glyph beside Start; opens the sport picker (record detail 2.1). */
@Composable
private fun SportButton(sport: SportType, onClick: () -> Unit) {
    SideButton(
        onClick = onClick,
        description = stringResource(R.string.record_sport_change, sport.label),
        filled = true,
        icon = sport.icon,
        label = sport.label,
        trailing = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, Modifier.size(18.dp)) },
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
            modifier = Modifier.size(SIDE_BUTTON_DP.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            trailing()
        }
    }
}

private const val DISABLED_ALPHA = 0.38f
private const val SIDE_BUTTON_DP = 56
