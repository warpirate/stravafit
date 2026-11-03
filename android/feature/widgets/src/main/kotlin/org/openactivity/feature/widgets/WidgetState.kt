// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.widgets

import org.openactivity.core.location.GpsStatus
import org.openactivity.core.recordingservice.PendingStart
import org.openactivity.core.recordingservice.RecordingUiSnapshot
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.RecordingStatus
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * What the widget shows. The state follows the recording, not the widget size (widgets-spec.md, Shape):
 * Ready → Countdown → (WaitingForGps) → Recording (paused or not) → Done → Ready, plus Interrupted when the
 * app died mid-run.
 */
sealed interface WidgetState {
    /**
     * Nothing recording. [sports] are the start buttons, the first being the main one.
     *
     * @property canRecord location permission is granted; without it a start opens the app's permission flow.
     */
    data class Ready(val sports: List<SportType>, val canRecord: Boolean = true) : WidgetState

    /** A widget start counting down; the countdown ends at [endsAtElapsedRealtime]. [gps] is null until the first reading. */
    data class Countdown(val sport: SportType, val endsAtElapsedRealtime: Long, val gps: GpsStatus? = null) : WidgetState

    /** The countdown ended without a fix: recording starts on the first fix, or now with "Start anyway". */
    data class WaitingForGps(val sport: SportType, val gps: GpsStatus?) : WidgetState

    /**
     * Recording or paused.
     *
     * @property clockBaseElapsedRealtime `Chronometer` base: `elapsedRealtime` at which the clock read 0:00.
     * @property timer the main clock (pauses excluded), for the frozen display and TalkBack.
     * @property currentSpeedMps smoothed current speed; 0 when stopped.
     * @property lapNumber the lap in progress, 1-based.
     * @property finishArmed the first Finish tap happened less than [FINISH_CONFIRM_WINDOW] ago.
     */
    data class Recording(
        val sport: SportType,
        val paused: Boolean,
        val clockBaseElapsedRealtime: Long,
        val timer: Duration,
        val distanceMeters: Double,
        val currentSpeedMps: Double,
        val averageSpeedMps: Double,
        val lapNumber: Int,
        val units: UnitSystem,
        val finishArmed: Boolean,
    ) : WidgetState

    /** Just saved. The widget offers the activity until it is dismissed, 6 h pass, or the day ends. */
    data class Done(val activity: SavedSummary, val units: UnitSystem) : WidgetState

    /** A recording lives only in the journal: the process died and nothing restarted it. */
    data object Interrupted : WidgetState
}

/** The few fields of a saved activity the Done state needs. */
data class SavedSummary(
    val id: Long,
    val sport: SportType,
    val title: String,
    val endTime: Instant,
    val zone: ZoneId,
    val movingTime: Duration,
    val distanceMeters: Double,
    val averageSpeedMps: Double,
)

/** How long after the first Finish tap a second tap saves. */
val FINISH_CONFIRM_WINDOW: Duration = 3.seconds

/** How long a saved activity stays on the widget. */
val DONE_TTL: Duration = 6.hours

/** The default start buttons until the settings screen exists (widgets-spec.md, Customization). */
val DEFAULT_SPORTS: List<SportType> = listOf(SportType.RUN, SportType.RIDE, SportType.WALK)

/**
 * Picks the widget state. Pure, so every transition is unit-tested.
 *
 * Order matters: a countdown wins (the recording is about to start), then a live recording, then an
 * interrupted one (it must be recovered before anything else), then a recent save, then Ready.
 *
 * @param finishArmedUntilMillis wall-clock end of the Finish confirm window, or null when not armed.
 * @param dismissedActivityId the activity the athlete closed with ✕; it is not shown again.
 */
fun resolveWidgetState(
    snapshot: RecordingUiSnapshot,
    pending: PendingStart?,
    interrupted: Boolean,
    lastSaved: SavedSummary?,
    dismissedActivityId: Long?,
    finishArmedUntilMillis: Long?,
    nowMillis: Long,
    elapsedRealtimeNow: Long,
    sports: List<SportType> = DEFAULT_SPORTS,
    canRecord: Boolean = true,
): WidgetState {
    if (pending != null && !snapshot.isActive) {
        return if (pending.waitingForGps) {
            WidgetState.WaitingForGps(pending.sport, pending.gps)
        } else {
            WidgetState.Countdown(pending.sport, pending.endsAtElapsedRealtime, pending.gps)
        }
    }
    if (snapshot.isActive) {
        return WidgetState.Recording(
            sport = snapshot.sport ?: sports.first(),
            paused = snapshot.status == RecordingStatus.PAUSED || snapshot.status == RecordingStatus.AUTO_PAUSED,
            clockBaseElapsedRealtime = elapsedRealtimeNow - snapshot.timerTime.inWholeMilliseconds,
            timer = snapshot.timerTime,
            distanceMeters = snapshot.distanceMeters,
            currentSpeedMps = snapshot.currentSpeedMps,
            averageSpeedMps = snapshot.averageSpeedMps,
            lapNumber = snapshot.laps.size + 1,
            units = snapshot.units,
            finishArmed = finishArmedUntilMillis != null && nowMillis < finishArmedUntilMillis,
        )
    }
    if (interrupted) return WidgetState.Interrupted
    if (lastSaved != null && lastSaved.id != dismissedActivityId && isFresh(lastSaved, nowMillis)) {
        return WidgetState.Done(lastSaved, snapshot.units)
    }
    return WidgetState.Ready(sports, canRecord)
}

/** Saved less than [DONE_TTL] ago and on the same local day (midnight resets the widget). */
internal fun isFresh(saved: SavedSummary, nowMillis: Long): Boolean {
    val now = Instant.ofEpochMilli(nowMillis)
    if (saved.endTime.isAfter(now)) return true
    if (now.toEpochMilli() - saved.endTime.toEpochMilli() >= DONE_TTL.inWholeMilliseconds) return false
    return saved.endTime.atZone(saved.zone).toLocalDate() == now.atZone(saved.zone).toLocalDate()
}
