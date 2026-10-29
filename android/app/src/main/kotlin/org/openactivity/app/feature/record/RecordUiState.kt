// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.record

import org.openactivity.app.model.Sport
import org.openactivity.core.designsystem.component.RecordButtonState

/** GPS readiness shown by the status pill and the location halo. */
sealed interface GpsState {
    /** Searching; [accuracyMeters] is null before the first fix. */
    data class Acquiring(val accuracyMeters: Float?) : GpsState

    /** Accuracy is within [GPS_READY_METERS]. */
    data class Ready(val accuracyMeters: Float) : GpsState

    /** Indoor sport: GPS is not used. */
    data object Indoor : GpsState

    companion object {
        /** Record checklist 2.4: "GPS ready" at 20 m or better. */
        const val GPS_READY_METERS = 20f
    }
}

/** State of the Record pre-start screen. Recording itself arrives with `:core:recording`. */
data class RecordUiState(
    val sport: Sport = Sport.Run,
    val recentSports: List<Sport> = listOf(Sport.Run, Sport.Ride, Sport.Walk, Sport.Hike),
    val gps: GpsState = GpsState.Acquiring(null),
    /** Record checklist 2.5: shown after 20 s without a fix. */
    val showSlowFixHint: Boolean = false,
    val autoPause: Boolean = false,
    val audioCues: Boolean = true,
    val isSportPickerOpen: Boolean = false,
) {
    /** Start works before a fix (the track is backfilled); only the ring style changes. */
    val startButtonState: RecordButtonState
        get() = if (gps is GpsState.Acquiring) RecordButtonState.Acquiring else RecordButtonState.Ready
}
