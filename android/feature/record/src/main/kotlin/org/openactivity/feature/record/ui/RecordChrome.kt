// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.LocationDisabled
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.component.StatusPill
import org.openactivity.core.designsystem.theme.Elevation
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.feature.record.GpsIndicator
import org.openactivity.feature.record.GpsUi
import org.openactivity.feature.record.R

/**
 * GPS status pill (core-screens 3.1 #4): a spinner while searching, a check when ready, a warning with a
 * fix action when permission or the location switch is missing. The icon carries the meaning with the colour.
 */
@Composable
internal fun GpsStatusPill(gps: GpsUi, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RidgelineTheme.colors
    // Pills float over the map: strava `float` colour with the float shadow, readable on any tiles.
    val surface = colors.float
    val onSurface = colors.onFloat
    val accuracy = gps.accuracyMeters?.toInt()
    val live = modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when (gps.indicator) {
        GpsIndicator.Acquiring -> StatusPill(
            text = stringResource(R.string.record_gps_acquiring),
            containerColor = surface,
            contentColor = onSurface,
            modifier = live,
            icon = {
                CircularProgressIndicator(
                    Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = colors.textSecondary,
                    trackColor = colors.hairline,
                )
            },
        )
        GpsIndicator.Weak -> StatusPill(
            text = if (accuracy != null) stringResource(R.string.record_gps_weak_accuracy, accuracy) else stringResource(R.string.record_gps_weak),
            containerColor = colors.warningContainer,
            contentColor = colors.onWarningContainer,
            modifier = live,
            icon = { PillIcon(Icons.Rounded.GpsNotFixed, colors.onWarningContainer) },
        )
        GpsIndicator.Ready -> StatusPill(
            text = stringResource(R.string.record_gps_ready),
            containerColor = surface,
            contentColor = onSurface,
            modifier = live.semantics {
                if (accuracy != null) stateDescription = "±$accuracy m"
            },
            icon = { Box(Modifier.size(10.dp).background(colors.success, CircleShape)) },
        )
        GpsIndicator.Indoor -> StatusPill(
            text = stringResource(R.string.record_gps_indoor),
            containerColor = surface,
            contentColor = onSurface,
            modifier = live,
            icon = { PillIcon(Icons.Rounded.GpsOff, colors.textSecondary) },
        )
        GpsIndicator.NoPermission, GpsIndicator.ApproximateOnly, GpsIndicator.LocationOff -> {
            val text = when (gps.indicator) {
                GpsIndicator.NoPermission -> R.string.record_gps_no_permission
                GpsIndicator.ApproximateOnly -> R.string.record_gps_approximate
                else -> R.string.record_gps_off
            }
            Surface(
                onClick = onFix,
                modifier = live,
                shape = PillShape,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Row(Modifier.padding(horizontal = Spacing.m, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillIcon(Icons.Rounded.LocationDisabled, MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(text), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(Spacing.s))
                    Text(
                        stringResource(R.string.record_gps_fix),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun PillIcon(icon: ImageVector, tint: Color) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
}

/** 48 dp round button floating over the map, with a shadow so it reads on any tiles. */
@Composable
internal fun MapIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = RidgelineTheme.colors.float,
        contentColor = RidgelineTheme.colors.onFloat,
        shadowElevation = Elevation.float,
    ) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = contentDescription) }
    }
}

/** A full-width notice over the map: warning tone for problems, neutral for information. */
@Composable
internal fun RecordBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Warning,
    warning: Boolean = true,
) {
    val colors = RidgelineTheme.colors
    val container = if (warning) colors.warningContainer else MaterialTheme.colorScheme.surface
    val content = if (warning) colors.onWarningContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.s))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Offers the battery optimisation exemption before the first recording (record detail 1.4): some phones
 * stop recordings with the screen off otherwise. Hidden for good once granted or dismissed.
 */
@Composable
internal fun BatteryPrompt(onAllow: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // Compact so it never covers the map, on the raised colour with a strong edge so it stands out on dark
    // tiles too. Shown at most until the athlete answers once (Allow or Not now).
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = RidgelineTheme.colors.float,
        contentColor = RidgelineTheme.colors.onFloat,
        border = BorderStroke(1.dp, RidgelineTheme.colors.borderStrong),
        shadowElevation = Elevation.float,
    ) {
        Row(Modifier.padding(start = Spacing.m, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.BatteryAlert,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = RidgelineTheme.colors.accent,
            )
            Spacer(Modifier.width(Spacing.s))
            Column(Modifier.weight(1f).padding(vertical = Spacing.s)) {
                Text(stringResource(R.string.record_battery_title), style = MaterialTheme.typography.labelLarge, maxLines = 2)
                Text(
                    stringResource(R.string.record_battery_body),
                    style = RidgelineTheme.text.caption,
                    color = RidgelineTheme.colors.textSecondary,
                    maxLines = 2,
                )
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.record_not_now), color = RidgelineTheme.colors.textSecondary) }
            TextButton(onClick = onAllow) { Text(stringResource(R.string.record_battery_allow), color = RidgelineTheme.colors.accentText) }
        }
    }
}
