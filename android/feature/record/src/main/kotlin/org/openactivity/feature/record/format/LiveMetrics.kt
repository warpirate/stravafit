// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.format

import org.openactivity.core.designsystem.component.Stat
import org.openactivity.core.recordingservice.RecordingUiSnapshot
import org.openactivity.feature.record.data.PaceMode
import org.openactivity.model.SportType
import org.openactivity.recording.RecordingStatus

/**
 * The numbers the recording screens show, already formatted. Built from a [RecordingUiSnapshot] by
 * [LiveMetrics.from]; pure, so the sport and pause rules are unit-tested.
 *
 * @property time timer time (elapsed minus pauses), the hero number; it stands still while paused.
 * @property speed pace or speed field, following the sport and [PaceMode] (record detail 3.3, 3.4).
 * @property average average pace or speed.
 * @property elevation current altitude; [elevationGain] total climb.
 * @property split the split in progress ("Split 3" with its pace or speed).
 * @property lap the lap in progress ("Lap 2" with its time).
 * @property splits completed splits, oldest first.
 * @property heartRate latest heart rate ("--" without a sensor).
 * @property splitNumber "Km 3" or "Mi 3": the split in progress.
 * @property splitProgress how far through the split in progress, 0..1.
 * @property toSplit distance left to the next split, with its unit ("590 m", "0.37 mi").
 */
data class LiveMetrics(
    val time: Stat,
    val movingTime: Stat,
    val distance: Stat,
    val speed: Stat,
    val average: Stat,
    val elevation: Stat,
    val elevationGain: Stat,
    val split: Stat,
    val lap: Stat,
    val splits: List<SplitRow>,
    val heartRate: Stat = Stat("Heart Rate", null, "bpm"),
    val splitNumber: String = "",
    val splitProgress: Float = 0f,
    val toSplit: String = "",
) {
    /** One completed split in the live splits list. */
    data class SplitRow(val number: Int, val value: String?, val unit: String, val partial: Boolean)

    companion object {
        /** Formats [snapshot] for [paceMode]. Sports without a pace show speed and ignore [paceMode]. */
        fun from(snapshot: RecordingUiSnapshot, paceMode: PaceMode): LiveMetrics {
            val units = snapshot.units
            val sport = snapshot.sport ?: SportType.RUN
            val display = sport.speedDisplay
            val paused = snapshot.status == RecordingStatus.PAUSED || snapshot.status == RecordingStatus.AUTO_PAUSED

            fun speedStat(label: String, mps: Double) = if (sport.displaysPace) {
                Stat(
                    label,
                    RecordFormat.pace(mps, display, units),
                    RecordFormat.paceUnit(display, units),
                    RecordFormat.paceUnitSpoken(display, units),
                )
            } else {
                Stat(label, RecordFormat.speed(mps, units), RecordFormat.speedUnit(units), RecordFormat.speedUnitSpoken(units))
            }

            val splitMillis = snapshot.currentSplit.movingTime.inWholeMilliseconds
            val splitSpeed = if (splitMillis > 0) snapshot.currentSplit.distanceMeters / (splitMillis / MILLIS) else 0.0
            val speed = when {
                sport.displaysPace -> when (paceMode) {
                    PaceMode.SPLIT -> speedStat("Split Avg", splitSpeed)
                    PaceMode.CURRENT -> speedStat("Pace", if (paused) 0.0 else snapshot.currentSpeedMps)
                    PaceMode.AVERAGE -> speedStat("Avg Pace", snapshot.averageSpeedMps)
                }
                // Rides show average speed while paused and current speed while moving (record detail 3.3).
                paused -> speedStat("Avg Speed", snapshot.averageSpeedMps)
                else -> speedStat("Speed", snapshot.currentSpeedMps)
            }

            val elevationUnit = RecordFormat.elevationUnit(units)
            val elevationSpoken = RecordFormat.elevationUnitSpoken(units)
            return LiveMetrics(
                time = Stat("Time", RecordFormat.duration(snapshot.timerTime)),
                movingTime = Stat("Moving Time", RecordFormat.duration(snapshot.movingTime)),
                distance = Stat(
                    "Distance",
                    RecordFormat.distance(snapshot.distanceMeters, units),
                    RecordFormat.distanceUnit(units),
                    RecordFormat.distanceUnitSpoken(units),
                ),
                speed = speed,
                average = speedStat(if (sport.displaysPace) "Avg Pace" else "Avg Speed", snapshot.averageSpeedMps),
                elevation = Stat(
                    "Elevation",
                    snapshot.altitudeMeters?.let { RecordFormat.elevation(it, units) },
                    elevationUnit,
                    elevationSpoken,
                ),
                elevationGain = Stat(
                    "Elev Gain",
                    RecordFormat.elevation(snapshot.elevationGainMeters, units),
                    elevationUnit,
                    elevationSpoken,
                ),
                split = speedStat("Split ${snapshot.currentSplit.index + 1}", splitSpeed),
                lap = Stat("Lap ${snapshot.currentLap.index + 1}", RecordFormat.duration(snapshot.currentLap.elapsed)),
                heartRate = Stat("Heart Rate", snapshot.heartRateBpm?.toString(), "bpm", "beats per minute"),
                splitNumber = "${if (units == org.openactivity.model.UnitSystem.METRIC) "Km" else "Mi"} ${snapshot.currentSplit.index + 1}",
                splitProgress = (snapshot.currentSplit.distanceMeters / units.splitLengthMeters).toFloat().coerceIn(0f, 1f),
                toSplit = remaining(units.splitLengthMeters - snapshot.currentSplit.distanceMeters, units),
                splits = snapshot.splits.map { split ->
                    val mps = split.averageSpeed.metersPerSecond
                    SplitRow(
                        number = split.index + 1,
                        value = if (sport.displaysPace) RecordFormat.pace(mps, display, units) else RecordFormat.speed(mps, units),
                        unit = if (sport.displaysPace) RecordFormat.paceUnit(display, units) else RecordFormat.speedUnit(units),
                        partial = split.isPartial,
                    )
                },
            )
        }

        private const val MILLIS = 1000.0

        private fun remaining(meters: Double, units: org.openactivity.model.UnitSystem): String {
            val m = meters.coerceAtLeast(0.0)
            return if (units == org.openactivity.model.UnitSystem.METRIC) {
                if (m < 1000.0) "${(m / 10).toInt() * 10} m" else "${RecordFormat.distance(m, units)} km"
            } else {
                "${RecordFormat.distance(m, units)} mi"
            }
        }
    }
}
