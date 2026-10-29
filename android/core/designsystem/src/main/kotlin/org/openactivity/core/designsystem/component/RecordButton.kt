// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.Elevation
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/** Pre-start states of the [RecordButton]. */
enum class RecordButtonState {
    /** No GPS fix yet. Start still works; the track is backfilled once a fix arrives. */
    Acquiring,

    /** GPS fix is good enough, or the sport is indoor. */
    Ready,

    /** Start is impossible, e.g. location permission denied for a GPS sport. */
    Disabled,
}

private val RingWidth = 3.dp
private val RingGap = 5.dp
private const val DISABLED_ALPHA = 0.38f

/**
 * The big circular Start button of the Record screen.
 *
 * A [Ready] button carries a solid primary ring; an [Acquiring] one a dashed, muted ring so the
 * state is shown by shape as well as colour.
 *
 * @param diameter diameter of the filled circle; the ring sits outside it. 72 dp by default.
 */
@Composable
fun RecordButton(
    state: RecordButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Start",
    diameter: Dp = 72.dp,
) {
    val enabled = state != RecordButtonState.Disabled
    val ringColor = when (state) {
        RecordButtonState.Ready -> MaterialTheme.colorScheme.primary
        else -> RidgelineTheme.colors.borderStrong
    }
    val outer = diameter + (RingGap + RingWidth) * 2
    Box(
        modifier = modifier
            .size(outer)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$label recording"
                stateDescription = when (state) {
                    RecordButtonState.Acquiring -> "Acquiring GPS"
                    RecordButtonState.Ready -> "Ready"
                    RecordButtonState.Disabled -> "Unavailable"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = RingWidth.toPx()
            val dash = if (state == RecordButtonState.Ready) {
                null
            } else {
                PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
            }
            drawCircle(
                color = ringColor,
                radius = size.minDimension / 2 - stroke / 2,
                style = Stroke(width = stroke, pathEffect = dash),
            )
        }
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(diameter),
            shape = PillShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = Elevation.level4,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = label.uppercase(), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun RecordButtonPreview() {
    RidgelineTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                RecordButton(state = RecordButtonState.Acquiring, onClick = {})
                RecordButton(state = RecordButtonState.Ready, onClick = {})
                RecordButton(state = RecordButtonState.Disabled, onClick = {})
            }
        }
    }
}
