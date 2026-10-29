// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice

import org.openactivity.core.location.GpsStatus
import org.openactivity.model.Lap
import org.openactivity.model.SportType
import org.openactivity.model.Split
import org.openactivity.model.UnitSystem
import org.openactivity.recording.LapProgress
import org.openactivity.recording.RecordingState
import org.openactivity.recording.RecordingStatus
import org.openactivity.recording.TrackPoint
import java.time.Instant
import kotlin.time.Duration

/**
 * Progress through the split in progress (1 km or 1 mile, following the unit setting).
 *
 * @property index zero-based index of the split in progress.
 * @property distanceMeters distance covered in this split so far.
 * @property movingTime moving time in this split so far.
 */
data class SplitProgress(val index: Int, val distanceMeters: Double, val movingTime: Duration) {
    /** Average pace of this split in seconds per unit (km or mile), or null before any distance. */
    fun paceSecondsPerUnit(units: UnitSystem): Double? =
        if (distanceMeters > MIN_DISTANCE) movingTime.inWholeMilliseconds / 1000.0 / (distanceMeters / units.splitLengthMeters) else null

    private companion object {
        const val MIN_DISTANCE = 10.0
    }
}

/**
 * Everything the Record screen and the notification show, published by [RecordingController].
 *
 * @property status [RecordingStatus.IDLE] when nothing is being recorded.
 * @property sport the sport being recorded, null when idle.
 * @property startedAt wall-clock start, null when idle.
 * @property units unit system used for splits and audio cues of this recording.
 * @property elapsed time since START including pauses.
 * @property movingTime moving time so far.
 * @property currentSpeedMps smoothed current speed (0 while paused).
 * @property averageSpeedMps distance over moving time.
 * @property altitudeMeters current altitude, if known.
 * @property gpsStatus GPS state and accuracy (permission, searching, weak, good).
 * @property track accepted track points so far, for drawing the live line; [TrackPoint.segment] separates
 *   stretches that must not be joined (after a resume or a recovered interruption).
 * @property isMockLocation a mock location was seen; the activity will be flagged.
 * @property recoveredAfterInterruption the recording was restored after the app process died.
 */
data class RecordingUiSnapshot(
    val status: RecordingStatus,
    val sport: SportType?,
    val routeId: Long?,
    val startedAt: Instant?,
    val units: UnitSystem,
    val elapsed: Duration,
    val movingTime: Duration,
    val distanceMeters: Double,
    val currentSpeedMps: Double,
    val averageSpeedMps: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val altitudeMeters: Double?,
    val currentSplit: SplitProgress,
    val splits: List<Split>,
    val laps: List<Lap>,
    val currentLap: LapProgress,
    val gpsStatus: GpsStatus,
    val heartRateBpm: Int?,
    val track: List<TrackPoint>,
    val isMockLocation: Boolean,
    val recoveredAfterInterruption: Boolean,
) {
    /** True while running or paused. */
    val isActive: Boolean get() = status.isActive

    /** Current pace in seconds per km or mile, or null when not moving. */
    fun currentPaceSecondsPerUnit(): Double? = paceOf(currentSpeedMps)

    /** Average pace in seconds per km or mile, or null before any moving distance. */
    fun averagePaceSecondsPerUnit(): Double? = paceOf(averageSpeedMps)

    private fun paceOf(speed: Double): Double? =
        if (speed > 0.0 && speed.isFinite()) units.splitLengthMeters / speed else null

    companion object {
        /** The snapshot while nothing is recorded. */
        fun idle(units: UnitSystem = UnitSystem.METRIC, gpsStatus: GpsStatus = GpsStatus.SEARCHING) = RecordingUiSnapshot(
            status = RecordingStatus.IDLE,
            sport = null,
            routeId = null,
            startedAt = null,
            units = units,
            elapsed = Duration.ZERO,
            movingTime = Duration.ZERO,
            distanceMeters = 0.0,
            currentSpeedMps = 0.0,
            averageSpeedMps = 0.0,
            elevationGainMeters = 0.0,
            elevationLossMeters = 0.0,
            altitudeMeters = null,
            currentSplit = SplitProgress(0, 0.0, Duration.ZERO),
            splits = emptyList(),
            laps = emptyList(),
            currentLap = LapProgress(0, Duration.ZERO, 0.0),
            gpsStatus = gpsStatus,
            heartRateBpm = null,
            track = emptyList(),
            isMockLocation = false,
            recoveredAfterInterruption = false,
        )

        /** Builds a snapshot from the engine's [state]. */
        fun from(
            state: RecordingState,
            routeId: Long?,
            startedAt: Instant?,
            units: UnitSystem,
            gpsStatus: GpsStatus,
            track: List<TrackPoint>,
            recovered: Boolean,
        ): RecordingUiSnapshot {
            val splitDistance = state.splits.sumOf { it.distance.meters }
            val splitMoving = state.splits.fold(Duration.ZERO) { acc, s -> acc + s.movingTime }
            return RecordingUiSnapshot(
                status = state.status,
                sport = state.sport,
                routeId = routeId,
                startedAt = startedAt,
                units = units,
                elapsed = state.elapsed,
                movingTime = state.movingTime,
                distanceMeters = state.distanceMeters,
                currentSpeedMps = state.currentSpeedMps,
                averageSpeedMps = state.averageSpeedMps,
                elevationGainMeters = state.elevation.gainMeters,
                elevationLossMeters = state.elevation.lossMeters,
                altitudeMeters = state.elevation.currentMeters,
                currentSplit = SplitProgress(
                    index = state.splits.size,
                    distanceMeters = (state.distanceMeters - splitDistance).coerceAtLeast(0.0),
                    movingTime = (state.movingTime - splitMoving).coerceAtLeast(Duration.ZERO),
                ),
                splits = state.splits,
                laps = state.laps,
                currentLap = state.currentLap,
                gpsStatus = gpsStatus,
                heartRateBpm = state.heartRateBpm,
                track = track,
                isMockLocation = state.isMockLocation,
                recoveredAfterInterruption = recovered,
            )
        }
    }
}

/**
 * A recording found in the journal that is not running (the process died and nothing restarted it).
 * The app offers Resume / Save now / Discard (recording design §16.2).
 */
data class InterruptedRecording(val sport: SportType, val startedAt: Instant, val lastUpdate: Instant)
