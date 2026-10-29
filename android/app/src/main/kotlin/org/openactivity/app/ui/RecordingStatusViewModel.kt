// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.openactivity.core.recordingservice.RecordingController
import org.openactivity.feature.record.format.RecordFormat
import org.openactivity.recording.RecordingStatus
import org.openactivity.app.ui.common.label
import javax.inject.Inject

/** What the in-progress bar shows while a recording runs behind the tabs. */
data class ActiveRecording(val time: String, val paused: Boolean, val sport: String)

/** Exposes the running recording, if any, to the app shell. */
@HiltViewModel
class RecordingStatusViewModel @Inject constructor(controller: RecordingController) : ViewModel() {
    val active: StateFlow<ActiveRecording?> = controller.state
        .map { s ->
            if (!s.isActive) {
                null
            } else {
                ActiveRecording(
                    time = RecordFormat.duration(s.timerTime),
                    paused = s.status == RecordingStatus.PAUSED || s.status == RecordingStatus.AUTO_PAUSED,
                    sport = s.sport?.label ?: "",
                )
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
