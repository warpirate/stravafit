// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.openactivity.core.map.compose.CameraTracking
import org.openactivity.core.map.compose.MapState
import org.openactivity.core.map.compose.rememberMapState
import org.openactivity.feature.record.RecordPhase
import org.openactivity.feature.record.RecordSheet
import org.openactivity.feature.record.RecordUiState

/**
 * The whole Record screen for [state], stateless: pre-start or recording, plus the countdown, sheets and
 * dialogs. [RecordRoute] connects it to the view model; screenshots render it directly.
 *
 * @param initialPage recording page shown first (0 map, 1 stats), for screenshots.
 */
@Composable
fun RecordContent(
    state: RecordUiState,
    actions: RecordActions,
    modifier: Modifier = Modifier,
    mapState: MapState = rememberMapState(initialTracking = CameraTracking.Follow),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    initialPage: Int = 0,
) {
    Box(modifier.fillMaxSize()) {
        val recording = state.recording
        if (recording != null) {
            RecordingContent(state, recording, actions, mapState, initialPage = initialPage)
        } else {
            PreStartContent(state, actions, mapState)
        }

        when (val phase = state.phase) {
            is RecordPhase.Countdown -> CountdownOverlay(phase.secondsLeft, actions.onCancelCountdown)
            RecordPhase.Starting, RecordPhase.Saving -> Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = BUSY_SCRIM)),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            else -> Unit
        }

        SnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars),
        )
    }

    val dismiss = { actions.onShowSheet(RecordSheet.None) }
    when (state.sheet) {
        RecordSheet.SportPicker -> SportPickerSheet(state.sport, state.recentSports, actions.onSelectSport, dismiss)
        RecordSheet.RoutePicker -> RoutePickerSheet(state.routes, state.route?.id, state.units, actions.onSelectRoute, dismiss)
        RecordSheet.Settings -> RecordSettingsSheet(state, actions, dismiss)
        RecordSheet.Finish -> state.recording?.let { FinishSheet(it, actions, dismiss) }
        RecordSheet.DiscardConfirm -> DiscardDialog(
            onConfirm = actions.onConfirmDiscard,
            onCancel = { actions.onShowSheet(RecordSheet.Finish) },
        )
        RecordSheet.None -> Unit
    }
    val interrupted = state.interrupted
    if (interrupted != null && state.recording == null && state.phase == RecordPhase.PreStart) {
        InterruptedDialog(interrupted, actions)
    }
}

private const val BUSY_SCRIM = 0.6f
