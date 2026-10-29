// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PauseCircleOutline
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.component.RecordButton
import org.openactivity.core.designsystem.theme.Elevation
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.SheetTopShape
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.feature.record.R
import org.openactivity.feature.record.RecordPhase
import org.openactivity.feature.record.RecordSheet
import org.openactivity.feature.record.RecordUiState
import org.openactivity.feature.record.RouteChoice
import org.openactivity.feature.record.format.RecordFormat
import org.openactivity.model.UnitSystem

/**
 * Bottom panel of the pre-start screen: sport and route pills, Auto-pause · Start · Audio cues, and a
 * one-line summary of the recording settings (or the "waiting for GPS" note after an early Start).
 */
@Composable
internal fun StartPanel(state: RecordUiState, actions: RecordActions, modifier: Modifier = Modifier, sideBySide: Boolean = false) {
    Surface(
        modifier = modifier,
        shape = if (sideBySide) MaterialTheme.shapes.extraSmall else SheetTopShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = Elevation.sheet,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (sideBySide) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Spacing.gutter)
                .padding(top = Spacing.l, bottom = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val route = state.route
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SelectorPill(
                    icon = state.sport.icon,
                    label = state.sport.label,
                    trailing = Icons.Rounded.KeyboardArrowDown,
                    description = stringResource(R.string.record_sport_change, state.sport.label),
                    onClick = { actions.onShowSheet(RecordSheet.SportPicker) },
                    modifier = Modifier.weight(1f),
                )
                SelectorPill(
                    icon = Icons.Rounded.Route,
                    label = route?.name ?: stringResource(R.string.record_add_route_short),
                    trailing = Icons.Rounded.ChevronRight,
                    description = stringResource(if (route == null) R.string.record_add_route else R.string.record_change_route),
                    onClick = { actions.onShowSheet(RecordSheet.RoutePicker) },
                    enabled = state.sport.usesGps,
                    modifier = Modifier.weight(1f),
                )
            }
            if (route != null && state.sport.usesGps) {
                Spacer(Modifier.height(Spacing.s))
                RouteRow(route, state.units, onRemove = { actions.onSelectRoute(null) })
            }
            Spacer(Modifier.height(Spacing.xl))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    RoundToggle(
                        icon = Icons.Rounded.PauseCircleOutline,
                        label = stringResource(R.string.record_auto_pause),
                        checked = state.autoPause,
                        onToggle = actions.onToggleAutoPause,
                    )
                }
                RecordButton(
                    state = state.startButtonState,
                    onClick = actions.onStart,
                    label = stringResource(R.string.record_start),
                    diameter = START_DIAMETER,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    RoundToggle(
                        icon = Icons.Rounded.RecordVoiceOver,
                        label = stringResource(R.string.record_audio_cues),
                        checked = state.audioCues,
                        onToggle = actions.onToggleAudioCues,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.l))
            if (state.phase == RecordPhase.WaitingForGps) {
                Text(
                    stringResource(R.string.record_waiting_gps),
                    style = MaterialTheme.typography.bodySmall,
                    color = RidgelineTheme.colors.accentText,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    settingsSummary(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = RidgelineTheme.colors.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun settingsSummary(state: RecordUiState): String {
    val split = if (state.units == UnitSystem.METRIC) "1 km" else "1 mi"
    return buildList {
        add(stringResource(if (state.autoPause) R.string.record_summary_auto_pause_on else R.string.record_summary_auto_pause_off))
        add(stringResource(R.string.record_summary_splits, split))
        if (state.audioCues) add(stringResource(R.string.record_summary_cues_on))
        if (state.countdown) add(stringResource(R.string.record_summary_countdown))
    }.joinToString(" · ")
}

/** 40 dp outlined pill selector ("Run ▾", "Add route ›"). */
@Composable
private fun SelectorPill(
    icon: ImageVector,
    label: String,
    trailing: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, RidgelineTheme.colors.borderStrong),
        modifier = modifier
            .height(SELECTOR_HEIGHT)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(Modifier.padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.m))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(trailing, contentDescription = null, modifier = Modifier.size(20.dp), tint = RidgelineTheme.colors.textMuted)
        }
    }
}

@Composable
private fun RouteRow(route: RouteChoice, units: UnitSystem, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${RecordFormat.distance(route.distanceMeters, units)} ${RecordFormat.distanceUnit(units)} · ${route.name}",
            style = RidgelineTheme.text.caption,
            color = RidgelineTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.record_remove_route))
        }
    }
}

/** 56 dp outlined round toggle with a label below; the icon turns orange when on. */
@Composable
private fun RoundToggle(icon: ImageVector, label: String, checked: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (checked) R.string.record_on else R.string.record_off)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) {
            role = Role.Switch
            stateDescription = state
        },
    ) {
        Surface(
            onClick = onToggle,
            shape = PillShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = if (checked) RidgelineTheme.colors.accent else MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.5.dp, if (checked) RidgelineTheme.colors.accent else RidgelineTheme.colors.borderStrong),
            modifier = Modifier.size(SIDE_BUTTON_DP),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

private const val DISABLED_ALPHA = 0.38f
private val SIDE_BUTTON_DP = 56.dp
private val SELECTOR_HEIGHT = 44.dp
private val START_DIAMETER = 96.dp
