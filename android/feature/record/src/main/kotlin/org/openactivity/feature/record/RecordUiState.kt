// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record

import androidx.compose.runtime.Immutable
import org.openactivity.core.designsystem.component.RecordButtonState
import org.openactivity.core.map.MapLocation
import org.openactivity.core.recordingservice.InterruptedRecording
import org.openactivity.feature.record.data.PaceMode
import org.openactivity.feature.record.format.LiveMetrics
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.RecordingStatus

/** Where the Record screen is in its flow (core-screens 3.9). */
sealed interface RecordPhase {
    /** Choosing a sport and waiting for GPS. */
    data object PreStart : RecordPhase

    /** Start was tapped before GPS had a fix; recording begins once it has one (or on a second tap). */
    data object WaitingForGps : RecordPhase

    /** The optional 3-2-1 before the timer starts. */
    data class Countdown(val secondsLeft: Int) : RecordPhase

    /** Start was tapped; the recording service is starting. */
    data object Starting : RecordPhase

    /** A recording is running or paused. */
    data object Recording : RecordPhase

    /** Finish was confirmed; the activity is being saved. */
    data object Saving : RecordPhase
}

/** What the GPS status pill says (core-screens 3.1 #4). */
enum class GpsIndicator {
    /** No location permission yet: "Allow location". */
    NoPermission,

    /** Only approximate location granted. */
    ApproximateOnly,

    /** Location switched off in system settings. */
    LocationOff,

    /** Waiting for a fix. */
    Acquiring,

    /** Fixes arrive but accuracy is worse than 20 m. */
    Weak,

    /** Accuracy is 20 m or better. */
    Ready,

    /** Indoor sport: GPS is not used. */
    Indoor,
}

/** GPS status shown on the pill and by the halo. */
@Immutable
data class GpsUi(val indicator: GpsIndicator, val accuracyMeters: Float? = null) {
    /** True when tapping the pill should fix something (permission or location setting). */
    val needsAction: Boolean
        get() = indicator == GpsIndicator.NoPermission ||
            indicator == GpsIndicator.ApproximateOnly ||
            indicator == GpsIndicator.LocationOff
}

/** A saved route in the picker. */
@Immutable
data class RouteSummary(val id: Long, val name: String, val distanceMeters: Double, val sport: SportType?)

/** The route drawn on the record map and followed by the recording. */
@Immutable
data class RouteChoice(val id: Long, val name: String, val distanceMeters: Double, val points: List<LatLng>)

/** Sheets and dialogs on top of the Record screen; at most one shows at a time. */
enum class RecordSheet {
    None,
    SportPicker,
    RoutePicker,
    Settings,
    Finish,
    DiscardConfirm,
}

/**
 * The live recording, ready to draw.
 *
 * @property track accepted points of the live line.
 * @property gpsLost a GPS sport has no fresh fix: time still counts, distance does not (core-screens 3.7).
 */
@Immutable
data class RecordingUi(
    val status: RecordingStatus,
    val sport: SportType,
    val metrics: LiveMetrics,
    val track: List<LatLng>,
    val location: MapLocation?,
    val gpsLost: Boolean,
    val recovered: Boolean,
    val mockLocation: Boolean,
) {
    /** Paused by the athlete (only they can resume). */
    val isPaused: Boolean get() = status == RecordingStatus.PAUSED

    /** Paused because the athlete stopped moving. */
    val isAutoPaused: Boolean get() = status == RecordingStatus.AUTO_PAUSED
}

/**
 * Everything the Record screen shows.
 *
 * @property sport selected sport before start; the recorded sport once recording.
 * @property recentSports for the picker's chip row, most recent first.
 * @property location the user's position for the pre-start map.
 * @property showSlowFixHint shown after 20 s without a fix (record detail 2.5).
 * @property routes saved routes for the picker; null until loaded.
 * @property recording non-null while a recording is running or paused.
 * @property interrupted a recording found in the journal after the app process died.
 * @property showBatteryPrompt offer the battery optimisation exemption (record detail 1.4).
 * @property speechWarning audio cues are on but the phone cannot speak them (record detail 7.5).
 */
@Immutable
data class RecordUiState(
    val phase: RecordPhase = RecordPhase.PreStart,
    val sport: SportType = SportType.RUN,
    val recentSports: List<SportType> = emptyList(),
    val units: UnitSystem = UnitSystem.METRIC,
    val autoPause: Boolean = false,
    val audioCues: Boolean = true,
    val countdown: Boolean = false,
    val keepScreenOn: Boolean = false,
    val paceMode: PaceMode = PaceMode.SPLIT,
    val gps: GpsUi = GpsUi(GpsIndicator.Acquiring),
    val location: MapLocation? = null,
    val showSlowFixHint: Boolean = false,
    val route: RouteChoice? = null,
    val routes: List<RouteSummary>? = null,
    val sheet: RecordSheet = RecordSheet.None,
    val recording: RecordingUi? = null,
    val interrupted: InterruptedRecording? = null,
    val showBatteryPrompt: Boolean = false,
    val speechWarning: Boolean = false,
) {
    /** Start works before a fix (the track fills in once GPS locks); only the ring style changes. */
    val startButtonState: RecordButtonState
        get() = when {
            phase == RecordPhase.WaitingForGps -> RecordButtonState.Acquiring
            phase != RecordPhase.PreStart -> RecordButtonState.Disabled
            gps.indicator == GpsIndicator.Ready || gps.indicator == GpsIndicator.Indoor -> RecordButtonState.Ready
            else -> RecordButtonState.Acquiring
        }

    /** True while recording or paused. */
    val isRecording: Boolean get() = recording != null
}

/** One-shot effects for the screen. */
sealed interface RecordEvent {
    /** The activity was saved; open it (the save screen). */
    data class ActivitySaved(val activityId: Long) : RecordEvent

    /** The recording was discarded and can be brought back for a few seconds (record detail 4.4). */
    data object Discarded : RecordEvent

    /** A short message, e.g. a lap summary or an error. */
    data class Message(val text: String) : RecordEvent
}
