// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.openactivity.core.designsystem.component.PrimaryButton
import org.openactivity.core.designsystem.component.SecondaryButton
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.recordingservice.InterruptedRecording
import org.openactivity.feature.record.R
import org.openactivity.feature.record.RecordUiState
import org.openactivity.feature.record.RecordingUi
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Record settings (core-screens 3.3): the options this phone app supports today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordSettingsSheet(state: RecordUiState, actions: RecordActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Spacing.xl)) {
            Text(
                stringResource(R.string.record_settings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s),
            )
            SwitchRow(
                title = stringResource(R.string.record_auto_pause),
                body = stringResource(R.string.record_auto_pause_body, state.sport.label),
                checked = state.autoPause,
                onToggle = actions.onToggleAutoPause,
            )
            SwitchRow(
                title = stringResource(R.string.record_audio_cues),
                body = stringResource(R.string.record_audio_cues_body),
                checked = state.audioCues,
                onToggle = actions.onToggleAudioCues,
            )
            if (state.speechWarning) {
                RecordBanner(
                    stringResource(R.string.record_speech_missing),
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
                )
            }
            SwitchRow(
                title = stringResource(R.string.record_countdown_title),
                body = stringResource(R.string.record_countdown_body),
                checked = state.countdown,
                onToggle = { actions.onSetCountdown(!state.countdown) },
            )
            SwitchRow(
                title = stringResource(R.string.record_screen_on_title),
                body = stringResource(R.string.record_screen_on_body),
                checked = state.keepScreenOn,
                onToggle = { actions.onSetKeepScreenOn(!state.keepScreenOn) },
            )
            if (state.sport.usesGps) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.listRowMin)
                        .clickable(onClick = actions.onAllowBackground)
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.record_battery_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.record_battery_settings_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = RidgelineTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.listRowMin)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() })
            .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
        }
        Spacer(Modifier.padding(Spacing.s))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Finish confirmation (core-screens 3.8): Save, Resume, or Discard (confirmed separately). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinishSheet(recording: RecordingUi, actions: RecordActions, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.gutter).padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(R.string.record_finish_title), style = MaterialTheme.typography.titleLarge)
            val m = recording.metrics
            Text(
                listOfNotNull(
                    m.distance.value?.let { "$it ${m.distance.unit}" }.takeIf { recording.sport.usesGps },
                    m.time.value,
                ).joinToString(" · "),
                style = RidgelineTheme.text.statM,
                color = RidgelineTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(Spacing.xs))
            PrimaryButton(stringResource(R.string.record_save), onClick = actions.onSave, modifier = Modifier.fillMaxWidth())
            SecondaryButton(stringResource(R.string.record_resume), onClick = actions.onResume, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = actions.onDiscard, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.record_discard), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** "Discard this activity?" with Discard in red (core-screens 3.8 #2). */
@Composable
internal fun DiscardDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.record_discard_title)) },
        text = { Text(stringResource(R.string.record_discard_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.record_discard), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.record_cancel)) } },
    )
}

/** Offered when the journal holds a recording whose process died (core-screens 3.7). */
@Composable
internal fun InterruptedDialog(interrupted: InterruptedRecording, actions: RecordActions) {
    val started = remember(interrupted.startedAt) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(interrupted.startedAt)
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.record_interrupted_title)) },
        text = { Text(stringResource(R.string.record_interrupted_body, interrupted.sport.label, started)) },
        confirmButton = { TextButton(onClick = actions.onResumeInterrupted) { Text(stringResource(R.string.record_resume)) } },
        dismissButton = {
            Row {
                TextButton(onClick = actions.onDiscardInterrupted) {
                    Text(stringResource(R.string.record_discard), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = actions.onSaveInterrupted) { Text(stringResource(R.string.record_save)) }
            }
        },
    )
}

/** Full-screen 3-2-1 before the timer starts; tapping anywhere cancels. */
@Composable
internal fun CountdownOverlay(secondsLeft: Int, onCancel: () -> Unit) {
    val motion = RidgelineTheme.motion
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = SCRIM_ALPHA))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = secondsLeft,
                transitionSpec = {
                    (scaleIn(tween(motion.enter(motion.medium)), initialScale = 1.6f) + fadeIn(tween(motion.enter(motion.short))))
                        .togetherWith(fadeOut(tween(motion.exit(motion.short))))
                },
                label = "countdown",
            ) { n ->
                Text(
                    n.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = COUNTDOWN_SP.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            Text(
                stringResource(R.string.record_countdown_cancel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

private const val SCRIM_ALPHA = 0.92f
private const val COUNTDOWN_SP = 160
