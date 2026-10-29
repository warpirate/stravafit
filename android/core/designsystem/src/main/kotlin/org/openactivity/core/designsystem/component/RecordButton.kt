// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    /** No GPS fix yet. Start waits for a fix (a second tap starts anyway). */
    Acquiring,

    /** GPS fix is good enough, or the sport is indoor. */
    Ready,

    /** Start is impossible, e.g. location permission denied for a GPS sport. */
    Disabled,
}

private const val DISABLED_ALPHA = 0.38f
private const val ACQUIRING_ALPHA = 0.72f

/**
 * The big circular Start button of the Record screen: a flat orange circle with a sentence-case label and
 * no ring (strava direction). While GPS is still [RecordButtonState.Acquiring] it is slightly faded and a
 * thin progress arc runs inside it, so the state does not rely on colour alone.
 *
 * @param diameter diameter of the circle. 96 dp in the strava spec.
 */
@Composable
fun RecordButton(
    state: RecordButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Start",
    diameter: Dp = 96.dp,
) {
    val enabled = state != RecordButtonState.Disabled
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(diameter)
            .alpha(
                when (state) {
                    RecordButtonState.Disabled -> DISABLED_ALPHA
                    RecordButtonState.Acquiring -> ACQUIRING_ALPHA
                    RecordButtonState.Ready -> 1f
                },
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$label recording"
                stateDescription = when (state) {
                    RecordButtonState.Acquiring -> "Acquiring GPS"
                    RecordButtonState.Ready -> "Ready"
                    RecordButtonState.Disabled -> "Unavailable"
                }
            },
        shape = PillShape,
        color = RidgelineTheme.colors.accent,
        contentColor = RidgelineTheme.colors.onAccent,
        shadowElevation = Elevation.recordButton,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (state == RecordButtonState.Acquiring) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    strokeWidth = 2.dp,
                    color = RidgelineTheme.colors.onAccent,
                    trackColor = RidgelineTheme.colors.onAccent.copy(alpha = 0.25f),
                )
            }
            Text(text = label, style = RidgelineTheme.text.buttonHero)
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
