// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice.audio

import org.openactivity.core.datastore.AudioCueSettings
import org.openactivity.model.LapTrigger
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.RecordingState
import org.openactivity.recording.RecordingStatus
import org.openactivity.recording.route.RouteEvent
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Something worth saying out loud during a recording. */
sealed interface AudioCue {
    data class Started(val sport: SportType) : AudioCue

    data object Paused : AudioCue

    data object Resumed : AudioCue

    data object AutoPaused : AudioCue

    data object AutoResumed : AudioCue

    data object Saved : AudioCue

    /**
     * A distance milestone of the custom interval.
     *
     * @property distanceMeters the milestone reached (a multiple of the interval).
     * @property movingTime total moving time at the milestone.
     * @property intervalPaceSecondsPerUnit average pace over the last interval in seconds per km or mile.
     * @property speed say speed instead of pace (rides).
     */
    data class Milestone(
        val distanceMeters: Double,
        val movingTime: Duration,
        val intervalPaceSecondsPerUnit: Double?,
        val speed: Boolean = false,
    ) : AudioCue

    /**
     * A completed kilometre or mile split.
     *
     * @property number one-based split number (also the distance in km or miles).
     * @property movingTime total moving time at the end of the split.
     * @property paceSecondsPerUnit the split's pace in seconds per km or mile.
     */
    data class Split(
        val number: Int,
        val movingTime: Duration,
        val paceSecondsPerUnit: Double?,
        val speed: Boolean = false,
    ) : AudioCue

    /**
     * A completed lap.
     *
     * @property number one-based lap number.
     * @property automatic closed by distance auto-lap rather than the lap button.
     * @property lapTime the lap's moving time.
     * @property distanceMeters the lap's distance.
     * @property paceSecondsPerUnit the lap's pace in seconds per km or mile.
     */
    data class Lap(
        val number: Int,
        val automatic: Boolean,
        val lapTime: Duration,
        val distanceMeters: Double,
        val paceSecondsPerUnit: Double?,
        val speed: Boolean = false,
    ) : AudioCue

    /**
     * The time interval readout: timer time, distance and average pace so far.
     */
    data class TimeReadout(
        val timerTime: Duration,
        val distanceMeters: Double,
        val averagePaceSecondsPerUnit: Double?,
        val speed: Boolean = false,
    ) : AudioCue

    /** Left the followed route; [distanceMeters] is the way back to it. */
    data class OffRoute(val distanceMeters: Double) : AudioCue

    data object BackOnRoute : AudioCue

    data object RouteCompleted : AudioCue
}

/**
 * Decides which cues to speak from successive engine states (record detail 7.1-7.4, A10).
 *
 * Each kind of cue has its own toggle in [AudioCueSettings], under the master switch. Splits fire for every
 * completed kilometre or mile; the custom distance interval fires every [AudioCueSettings.distanceInterval]
 * units, except where a split cue already covers the same distance. A planner created for a restored
 * recording starts from its current state, so nothing is announced twice.
 *
 * @param sport rides hear speed instead of pace.
 */
class AudioCuePlanner(
    private val settings: AudioCueSettings,
    private val units: UnitSystem,
    sport: SportType,
    initialState: RecordingState? = null,
) {
    private val speed = sport.isRideLike
    private val intervalMeters = settings.distanceInterval * units.splitLengthMeters
    private val timeInterval = settings.timeIntervalMinutes.minutes
    private var lastStatus: RecordingStatus = initialState?.status ?: RecordingStatus.IDLE
    private var nextMilestone: Int = initialState?.let { floor(it.distanceMeters / intervalMeters).toInt() + 1 } ?: 1
    private var lastMilestoneMoving: Duration = initialState?.movingTime ?: Duration.ZERO
    private var splitsSeen: Int = initialState?.splits?.size ?: 0
    private var lapsSeen: Int = initialState?.laps?.size ?: 0
    private var nextTimeMark: Long = initialState?.let { nextMark(it.timerTime) } ?: 1

    /** Whether "started" and "saved" should be spoken. */
    val announcesStatus: Boolean get() = settings.enabled && settings.announceStatus

    /** Cues triggered by moving to [state]. Empty when cues are switched off. */
    fun onState(state: RecordingState): List<AudioCue> {
        val previous = lastStatus
        lastStatus = state.status
        val newSplits = state.splits.drop(splitsSeen)
        splitsSeen = state.splits.size
        val newLaps = state.laps.drop(lapsSeen)
        lapsSeen = state.laps.size
        val milestone = milestone(state)
        val readout = timeReadout(state)
        if (!settings.enabled) return emptyList()

        val cues = mutableListOf<AudioCue>()
        statusCue(previous, state.status)?.let(cues::add)
        if (settings.announceSplits) {
            newSplits.filterNot { it.isPartial }.forEach { split ->
                cues += AudioCue.Split(
                    number = split.index + 1,
                    movingTime = state.splits.take(split.index + 1).fold(Duration.ZERO) { acc, s -> acc + s.movingTime },
                    paceSecondsPerUnit = paceOf(split.movingTime, split.distance.meters),
                    speed = speed,
                )
            }
        }
        if (settings.distanceCues && milestone != null && !(settings.announceSplits && isWholeUnit(milestone.distanceMeters))) {
            cues += milestone
        }
        if (settings.announceLaps) {
            newLaps.forEach { lap ->
                cues += AudioCue.Lap(
                    number = lap.index + 1,
                    automatic = lap.trigger == LapTrigger.DISTANCE,
                    lapTime = lap.movingTime,
                    distanceMeters = lap.distance.meters,
                    paceSecondsPerUnit = paceOf(lap.movingTime, lap.distance.meters),
                    speed = speed,
                )
            }
        }
        readout?.let(cues::add)
        return cues
    }

    /** Cues for route events while following a route. */
    fun onRoute(events: List<RouteEvent>): List<AudioCue> {
        if (!settings.enabled || !settings.announceRoute) return emptyList()
        return events.map { event ->
            when (event) {
                is RouteEvent.OffRoute -> AudioCue.OffRoute(event.distanceMeters)
                RouteEvent.BackOnRoute -> AudioCue.BackOnRoute
                RouteEvent.Completed -> AudioCue.RouteCompleted
            }
        }
    }

    /** Advances the custom-interval counter and returns its cue, if one is due. */
    private fun milestone(state: RecordingState): AudioCue.Milestone? {
        if (!state.status.isActive || state.distanceMeters < nextMilestone * intervalMeters) return null
        val reached = floor(state.distanceMeters / intervalMeters).toInt()
        val intervals = reached - nextMilestone + 1
        val moving = state.movingTime - lastMilestoneMoving
        val unitsCovered = intervals * settings.distanceInterval
        nextMilestone = reached + 1
        lastMilestoneMoving = state.movingTime
        return AudioCue.Milestone(
            distanceMeters = reached * intervalMeters,
            movingTime = state.movingTime,
            intervalPaceSecondsPerUnit = if (moving.isPositive()) moving.inWholeMilliseconds / 1000.0 / unitsCovered else null,
            speed = speed,
        )
    }

    /** Advances the time-interval counter and returns its readout, if one is due. */
    private fun timeReadout(state: RecordingState): AudioCue.TimeReadout? {
        if (!timeInterval.isPositive() || !state.status.isActive) return null
        if (state.timerTime < timeInterval * nextTimeMark.toDouble()) return null
        nextTimeMark = nextMark(state.timerTime)
        return AudioCue.TimeReadout(
            timerTime = state.timerTime,
            distanceMeters = state.distanceMeters,
            averagePaceSecondsPerUnit = paceOf(state.movingTime, state.distanceMeters),
            speed = speed,
        )
    }

    private fun nextMark(timerTime: Duration): Long =
        if (timeInterval.isPositive()) timerTime.inWholeMilliseconds / timeInterval.inWholeMilliseconds + 1 else 1

    private fun isWholeUnit(meters: Double): Boolean {
        val value = meters / units.splitLengthMeters
        return abs(value - value.roundToLong()) < 1e-6
    }

    private fun paceOf(moving: Duration, meters: Double): Double? =
        if (moving.isPositive() && meters > MIN_PACE_METERS) moving.inWholeMilliseconds / 1000.0 / (meters / units.splitLengthMeters) else null

    private fun statusCue(from: RecordingStatus, to: RecordingStatus): AudioCue? = when {
        from == to -> null
        to == RecordingStatus.PAUSED && from.isActive -> AudioCue.Paused.takeIf { settings.announceStatus }
        from == RecordingStatus.PAUSED && to == RecordingStatus.RECORDING -> AudioCue.Resumed.takeIf { settings.announceStatus }
        from == RecordingStatus.RECORDING && to == RecordingStatus.AUTO_PAUSED ->
            AudioCue.AutoPaused.takeIf { settings.announceAutoPause }
        from == RecordingStatus.AUTO_PAUSED && to == RecordingStatus.RECORDING ->
            AudioCue.AutoResumed.takeIf { settings.announceAutoPause }
        else -> null
    }

    private companion object {
        /** Below this a lap or split is too short for a meaningful pace. */
        const val MIN_PACE_METERS = 10.0
    }
}

/** Speaks cues. Implementations must never block the caller. */
interface CueSpeaker {
    fun speak(cue: AudioCue, units: UnitSystem)

    /** Releases the speech engine (called when the recording service stops). */
    fun shutdown()
}
