// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.openactivity.core.recordingservice.permission.RecordingPermissionSheet
import org.openactivity.core.recordingservice.permission.appSettingsIntent
import org.openactivity.core.recordingservice.permission.rememberRecordingPermissionRequester
import org.openactivity.feature.record.GpsIndicator
import org.openactivity.feature.record.RecordEvent
import org.openactivity.feature.record.RecordPhase
import org.openactivity.feature.record.RecordViewModel
import org.openactivity.feature.record.R
import org.openactivity.feature.record.system.batteryExemptionIntent

/**
 * The Record screen connected to [RecordViewModel]: permissions in context on the first Start (location,
 * then notifications, with a rationale and a settings deep link), screen-on and lock-screen handling, and
 * navigation once an activity is saved.
 *
 * @param onClose closes Record when idle and minimises it while recording; the recording carries on in
 *   the foreground service either way.
 * @param onActivitySaved called with the new activity id after Save.
 */
@Composable
fun RecordRoute(
    onClose: () -> Unit,
    onActivitySaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val currentOnSaved by rememberUpdatedState(onActivitySaved)

    // The permission flow ends in onReady; remember whether it was started by Start or only to fix GPS.
    var startWhenReady by remember { mutableStateOf(false) }
    val permissions = rememberRecordingPermissionRequester(onReady = {
        if (startWhenReady) viewModel.start()
        startWhenReady = false
    })

    val discardedText = stringResource(R.string.record_discarded)
    val undoLabel = stringResource(R.string.record_undo)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RecordEvent.ActivitySaved -> currentOnSaved(event.activityId)
                // Snackbars show in their own coroutine so a long one (Undo) never holds back later events
                // such as navigation after Save.
                RecordEvent.Discarded -> {
                    snackbar.currentSnackbarData?.dismiss()
                    launch {
                        // Long matches the view model's undo window (record detail 4.4: at least 5 s).
                        val result = snackbar.showSnackbar(
                            discardedText,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undoDiscard()
                    }
                }
                is RecordEvent.Message -> {
                    snackbar.currentSnackbarData?.dismiss()
                    launch { snackbar.showSnackbar(event.text) }
                }
            }
        }
    }
    // Android 12+ refuses to start the foreground service from the background, so never let a countdown
    // finish while the screen is not visible.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.cancelCountdown() }
    // The exemption may have been granted in the system dialog or in settings meanwhile.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshBatteryExemption() }
    BackHandler(enabled = state.phase is RecordPhase.Countdown || state.phase == RecordPhase.WaitingForGps) { viewModel.cancelCountdown() }
    KeepScreenOn(enabled = state.isRecording && state.keepScreenOn)
    ShowOverLockScreen(enabled = state.isRecording)

    val actions = remember(viewModel, permissions, onClose) {
        RecordActions(
            onClose = onClose,
            onShowSheet = viewModel::showSheet,
            onSelectSport = viewModel::selectSport,
            onSelectRoute = viewModel::selectRoute,
            onToggleAutoPause = viewModel::toggleAutoPause,
            onToggleAudioCues = viewModel::toggleAudioCues,
            onSetCountdown = viewModel::setCountdown,
            onSetKeepScreenOn = viewModel::setKeepScreenOn,
            onFixLocation = {
                when (viewModel.uiState.value.gps.indicator) {
                    GpsIndicator.LocationOff -> context.startActivity(
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    GpsIndicator.ApproximateOnly -> if (permissions.hasLocation) {
                        context.startActivity(appSettingsIntent(context))
                    } else {
                        startWhenReady = false
                        permissions.request()
                    }
                    else -> {
                        startWhenReady = false
                        permissions.request()
                    }
                }
            },
            onStart = {
                startWhenReady = true
                permissions.request()
            },
            onCancelCountdown = viewModel::cancelCountdown,
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onLap = viewModel::lap,
            onFinish = viewModel::finish,
            onSave = viewModel::save,
            onDiscard = viewModel::requestDiscard,
            onConfirmDiscard = viewModel::confirmDiscard,
            onCyclePaceMode = viewModel::cyclePaceMode,
            onResumeInterrupted = viewModel::resumeInterrupted,
            onSaveInterrupted = viewModel::saveInterrupted,
            onDiscardInterrupted = viewModel::discardInterrupted,
            onAllowBackground = {
                runCatching { context.startActivity(batteryExemptionIntent(context)) }
                viewModel.allowBackground()
            },
            onDismissBatteryPrompt = viewModel::dismissBatteryPrompt,
        )
    }
    RecordContent(state = state, actions = actions, modifier = modifier, snackbarHostState = snackbar)
    RecordingPermissionSheet(permissions)
}
