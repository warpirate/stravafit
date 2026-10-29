// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.SheetTopShape
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.feature.record.R
import org.openactivity.feature.record.RecordingUi

/**
 * Controls under the recording (core-screens 3.4, 3.6). Running: Lap · Pause · Map/Stats. Paused:
 * Resume · Finish, where Finish needs a press-and-hold so a pocket tap can never end the activity
 * (record detail 4.6). Every action gives a short haptic.
 */
@Composable
internal fun RecordingControls(
    recording: RecordingUi,
    actions: RecordActions,
    showingStats: Boolean,
    onTogglePage: (() -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    fun withHaptic(action: () -> Unit): () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        action()
    }
    Surface(shape = SheetTopShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Spacing.gutter, vertical = Spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            if (recording.isPaused) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    PillAction(
                        label = stringResource(R.string.record_resume),
                        icon = Icons.Rounded.PlayArrow,
                        filled = true,
                        onClick = withHaptic(actions.onResume),
                        modifier = Modifier.weight(1f),
                    )
                    HoldToFinishButton(
                        onFinish = withHaptic(actions.onFinish),
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        RoundAction(stringResource(R.string.record_lap), Icons.Rounded.Flag, withHaptic(actions.onLap))
                    }
                    PauseButton(withHaptic(actions.onPause))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (onTogglePage != null) {
                            RoundAction(
                                label = stringResource(if (showingStats) R.string.record_map else R.string.record_stats),
                                icon = if (showingStats) Icons.Rounded.Map else Icons.Rounded.QueryStats,
                                onClick = onTogglePage,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PauseButton(onClick: () -> Unit) {
    val description = stringResource(R.string.record_pause)
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 4.dp,
        modifier = Modifier.size(PAUSE_DIAMETER).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun RoundAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = PillShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.size(SIDE_DIAMETER).semantics { contentDescription = label },
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PillAction(label: String, icon: ImageVector, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = if (filled) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.height(PILL_HEIGHT),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(Spacing.s))
            Text(label.uppercase(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Finish needs a press-and-hold: the pill fills while held and fires when full. A short tap only shows
 * "Hold to finish". TalkBack users get a long-click action that finishes directly.
 */
@Composable
internal fun HoldToFinishButton(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnFinish by rememberUpdatedState(onFinish)
    var hinted by remember { mutableStateOf(false) }
    val label = stringResource(R.string.record_finish)
    val hint = stringResource(R.string.record_hold_to_finish)
    val reduced = RidgelineTheme.motion.reduced
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.error,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
        modifier = modifier
            .height(PILL_HEIGHT)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$label. $hint"
                onClick(label = hint) { hinted = true; true }
                onLongClick(label = label) { currentOnFinish(); true }
            }
            .pointerInput(reduced) {
                detectTapGestures(
                    onPress = {
                        val fill = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(if (reduced) HOLD_MS_REDUCED else HOLD_MS, easing = LinearEasing))
                            currentOnFinish()
                        }
                        val released = tryAwaitRelease()
                        if (fill.isActive) {
                            fill.cancel()
                            if (released) hinted = true
                            scope.launch { progress.animateTo(0f, tween(RESET_MS)) }
                        } else {
                            progress.snapTo(0f)
                        }
                    },
                )
            },
    ) {
        Box(contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.value)
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Stop, contentDescription = null)
                Spacer(Modifier.width(Spacing.s))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label.uppercase(), style = MaterialTheme.typography.labelLarge)
                    // Always say how Finish works; after a short tap the cue is emphasised.
                    Text(
                        hint,
                        style = RidgelineTheme.text.caption,
                        color = if (hinted) MaterialTheme.colorScheme.error else RidgelineTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

private val PAUSE_DIAMETER = 80.dp
private val SIDE_DIAMETER = 56.dp
private val PILL_HEIGHT = 64.dp
private const val HOLD_MS = 900
private const val HOLD_MS_REDUCED = 600
private const val RESET_MS = 150
