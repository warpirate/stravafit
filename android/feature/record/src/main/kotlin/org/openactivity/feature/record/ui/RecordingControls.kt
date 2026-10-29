// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import org.openactivity.core.designsystem.theme.Elevation
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
 * Controls at the bottom of the recording panel. Running: Lap, Stop (pauses), Lock. Paused: Resume and
 * Finish, where Finish needs a press-and-hold so a pocket tap can never end the activity; the hold is the
 * confirmation, so the Save screen opens straight after. Locked: Lap and Stop are disabled and the lock
 * button needs a hold to unlock. Every action gives a short haptic.
 */
@Composable
internal fun RecordingControls(
    recording: RecordingUi,
    actions: RecordActions,
    locked: Boolean,
    onLockChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    fun withHaptic(action: () -> Unit): () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        action()
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (recording.isPaused) Arrangement.spacedBy(Spacing.x3, Alignment.CenterHorizontally) else Arrangement.Start,
    ) {
        if (recording.isPaused) {
            BigRoundButton(
                label = stringResource(R.string.record_resume),
                filled = false,
                onClick = withHaptic(actions.onResume),
            )
            HoldToFinishButton(onFinish = withHaptic(actions.onFinish))
        } else {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                RoundAction(stringResource(R.string.record_lap), Icons.Outlined.Flag, withHaptic(actions.onLap), enabled = !locked)
            }
            StopButton(withHaptic(actions.onPause), enabled = !locked)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (locked) {
                    HoldToUnlock(onUnlock = withHaptic { onLockChange(false) })
                } else {
                    RoundAction(stringResource(R.string.record_lock), Icons.Outlined.Lock, withHaptic { onLockChange(true) })
                }
            }
        }
    }
}

/** The orange Stop button with a white square: it pauses the recording (Resume or Finish follow). */
@Composable
private fun StopButton(onClick: () -> Unit, enabled: Boolean) {
    val description = stringResource(R.string.record_pause)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        color = RidgelineTheme.colors.accent,
        contentColor = RidgelineTheme.colors.onAccent,
        shadowElevation = Elevation.recordButton,
        modifier = Modifier
            .size(STOP_DIAMETER)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(28.dp).background(RidgelineTheme.colors.onAccent, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun RoundAction(label: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA)) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = PillShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.5.dp, RidgelineTheme.colors.borderStrong),
            modifier = Modifier.size(SIDE_DIAMETER).semantics { contentDescription = label },
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Resume: a 96 dp circle outlined in the accent, label in accent text. */
@Composable
private fun BigRoundButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = if (filled) RidgelineTheme.colors.accent else MaterialTheme.colorScheme.surface,
        contentColor = if (filled) RidgelineTheme.colors.onAccent else RidgelineTheme.colors.accentText,
        border = if (filled) null else BorderStroke(2.dp, RidgelineTheme.colors.accent),
        modifier = Modifier.size(BIG_DIAMETER),
    ) {
        Box(contentAlignment = Alignment.Center) { Text(label, style = RidgelineTheme.text.buttonHero) }
    }
}

/** While locked: hold the lock to unlock, so a pocket cannot lap or pause the recording. */
@Composable
private fun HoldToUnlock(onUnlock: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(onUnlock)
    val label = stringResource(R.string.record_unlock)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(SIDE_DIAMETER)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = label
                    onLongClick(label = label) { current(); true }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            val fill = scope.launch {
                                progress.snapTo(0f)
                                progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                                current()
                            }
                            tryAwaitRelease()
                            if (fill.isActive) {
                                fill.cancel()
                                scope.launch { progress.animateTo(0f, tween(RESET_MS)) }
                            } else {
                                progress.snapTo(0f)
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxSize(),
                color = RidgelineTheme.colors.accent,
                trackColor = RidgelineTheme.colors.borderStrong,
                strokeWidth = 2.dp,
            )
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = RidgelineTheme.colors.accent)
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
        color = RidgelineTheme.colors.accent,
        contentColor = RidgelineTheme.colors.onAccent,
        shadowElevation = Elevation.recordButton,
        modifier = modifier
            .size(BIG_DIAMETER)
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
        Box(contentAlignment = Alignment.Center) {
            // The ring fills while held; after a short tap the label says how Finish works.
            CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxSize().padding(4.dp),
                color = RidgelineTheme.colors.onAccent,
                trackColor = Color.Transparent,
                strokeWidth = 3.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = RidgelineTheme.text.buttonHero)
                if (hinted) Text(hint, style = RidgelineTheme.text.caption)
            }
        }
    }
}

private val STOP_DIAMETER = 84.dp
private val BIG_DIAMETER = 96.dp
private val SIDE_DIAMETER = 56.dp
private const val DISABLED_ALPHA = 0.38f
private const val HOLD_MS = 900
private const val HOLD_MS_REDUCED = 600
private const val RESET_MS = 150
