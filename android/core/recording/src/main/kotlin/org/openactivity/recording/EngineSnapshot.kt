// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording

import org.openactivity.metrics.ElevationAccumulator
import org.openactivity.metrics.SplitAccumulator
import org.openactivity.model.Lap
import org.openactivity.model.LatLng

/**
 * The complete state of a [RecordingEngine], made of immutable values only.
 *
 * The recording service persists it (with the points journal) every few seconds; after the process dies,
 * [RecordingEngine.restore] rebuilds an engine that continues where this one stopped. Two snapshots are
 * equal exactly when the engines would behave identically from then on.
 */
public data class EngineSnapshot(
    val config: RecordingConfig,
    val status: RecordingStatus,
    val offsetNanos: Long,
    val clockNanos: Long?,
    val startNanos: Long,
    val finishNanos: Long,
    val wallAnchorNanos: Long?,
    val wallAnchorMillis: Long,
    val timerNanos: Long,
    val movingNanos: Long,
    val openPauseKind: PauseKind?,
    val openPauseStart: Long,
    val lastTimerStart: Long,
    val maxSpeedMps: Double,
    val pauses: List<PauseInterval>,
    val autoPauseCount: Int,
    val distanceMeters: Double,
    val anchor: LatLng?,
    val bridgePending: Boolean,
    val previousFusedSpeed: Double,
    val dopplerTrusted: Boolean,
    val dopplerCheckAnchor: LatLng?,
    val dopplerCheckStart: Long,
    val dopplerCheckDistance: Double,
    val segment: Int,
    val lastFixNanos: Long?,
    val lastAccepted: AcceptedFix?,
    val rejectsInARow: Int,
    val lastAccuracy: Double?,
    val lastAccuracyNanos: Long?,
    val gaps: List<GpsGap>,
    val mockSeen: Boolean,
    val interrupted: Boolean,
    val filter: PositionFilterState?,
    val motion: MotionDetectorState,
    val gpsElevation: ElevationAccumulator.State,
    val baroElevation: ElevationAccumulator.State,
    val hasBarometer: Boolean,
    val verticalAccuracyAvg: Double,
    val carriedGain: Double,
    val carriedLoss: Double,
    val baroAltitude: Double,
    val baroCalibrationSum: Double,
    val baroCalibrationCount: Int,
    val stepSpeed: Double,
    val baroMin: Double,
    val baroMax: Double,
    val gpsAltMin: Double,
    val gpsAltMax: Double,
    val splits: SplitAccumulator.State,
    val laps: List<Lap>,
    val lapStartNanos: Long,
    val lapStartDistance: Double,
    val lapStartMovingNanos: Long,
    val lapStartGain: Double,
    val points: List<TrackPoint>,
    val heartRate: SensorChannelState,
    val power: SensorChannelState,
    val cadence: SensorChannelState,
    val finishedActivity: FinishedActivity?,
)
