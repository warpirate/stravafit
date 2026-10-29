// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail

import androidx.compose.runtime.Immutable
import org.openactivity.core.data.model.ActivityDetail
import org.openactivity.core.data.model.ActivityPoint
import org.openactivity.core.data.model.PersonalBest
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.feature.activitydetail.format.DetailFormat
import org.openactivity.feature.activitydetail.format.Measure
import org.openactivity.model.SportType
import org.openactivity.model.Split
import org.openactivity.model.UnitSystem
import org.openactivity.recording.AltitudeSource
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One row of the splits table.
 *
 * @property label split number, or the real distance of a final partial split ("0.43").
 * @property barFraction bar length relative to the fastest split (0..1).
 * @property heartRate average heart rate in the split, or null when it has none (shown as a dash).
 */
@Immutable
data class SplitRow(
    val label: String,
    val speed: Measure?,
    val barFraction: Float,
    val elevationDelta: String?,
    val heartRate: String?,
    val isFastest: Boolean,
    val isSlowest: Boolean,
    val isPartial: Boolean,
    val startMeters: Double,
    val endMeters: Double,
)

/** One row of the laps table. */
@Immutable
data class LapRow(
    val number: Int,
    val distance: Measure,
    val time: String,
    val speed: Measure?,
    val barFraction: Float,
)

/**
 * A best effort achieved in this activity.
 *
 * @property isPersonalBest true when no other activity of the sport has beaten it.
 */
@Immutable
data class EffortRow(val name: String, val result: String, val isPersonalBest: Boolean)

/** Builds the detail screen's display rows from stored data. Pure, so it is unit-tested directly. */
object DetailRows {
    /**
     * Stats grid: distance, pace/speed, moving time, elevation gain, elapsed time, heart rate and sensors.
     * Rows are always full (three per row, as in the mockup): a partial last row is completed with Avg
     * Heart Rate (a dash without a sensor), Elev Loss and the maximum pace or speed.
     */
    fun stats(activity: SavedActivity, units: UnitSystem, hasElevation: Boolean): List<Stat> {
        val base = baseStats(activity, units, hasElevation)
        if (base.size % STAT_COLUMNS == 0) return base
        val labels = base.map { it.label }.toSet()
        val fillers = buildList {
            if ("Avg Heart Rate" !in labels) add(Stat("Avg Heart Rate", "\u2014", null, "no heart rate sensor"))
            if (hasElevation) {
                val loss = DetailFormat.elevation(activity.elevationLossMeters, units)
                add(Stat("Elev Loss", loss.value, loss.unit, loss.spokenUnit))
            }
            val max = DetailFormat.speed(activity.maxSpeedMps, activity.sport, units)
            val maxLabel = if (activity.sport.displaysPace) "Max Pace" else "Max Speed"
            if (max != null && maxLabel !in labels) add(Stat(maxLabel, max.value, max.unit, max.spokenUnit))
        }
        val missing = STAT_COLUMNS - base.size % STAT_COLUMNS
        return base + fillers.take(missing)
    }

    private const val STAT_COLUMNS = 3

    private fun baseStats(activity: SavedActivity, units: UnitSystem, hasElevation: Boolean): List<Stat> = buildList {
        val distance = DetailFormat.distance(activity.distanceMeters, units)
        add(Stat("Distance", distance.value, distance.unit, distance.spokenUnit))
        val speed = DetailFormat.speed(activity.averageSpeedMps, activity.sport, units)
        add(Stat(if (activity.sport.displaysPace) "Avg Pace" else "Avg Speed", speed?.value, speed?.unit, speed?.spokenUnit))
        add(Stat("Moving Time", DetailFormat.duration(activity.movingTime)))
        if (hasElevation) {
            val gain = DetailFormat.elevation(activity.elevationGainMeters, units)
            add(Stat("Elev Gain", gain.value, gain.unit, gain.spokenUnit))
        }
        add(Stat("Elapsed Time", DetailFormat.duration(activity.elapsedTime)))
        if (!activity.sport.displaysPace) {
            val max = DetailFormat.speed(activity.maxSpeedMps, activity.sport, units)
            if (max != null) add(Stat("Max Speed", max.value, max.unit, max.spokenUnit))
        }
        activity.averageHeartRate?.let { val hr = DetailFormat.heartRate(it); add(Stat("Avg Heart Rate", hr.value, hr.unit, hr.spokenUnit)) }
        activity.maxHeartRate?.let { val hr = DetailFormat.heartRate(it.toDouble()); add(Stat("Max Heart Rate", hr.value, hr.unit, hr.spokenUnit)) }
        activity.averagePower?.let { add(Stat("Avg Power", it.roundToInt().toString(), "W", "watts")) }
        activity.averageCadence?.let { add(Stat("Cadence", it.roundToInt().toString(), if (activity.sport.isRideLike) "rpm" else "spm")) }
    }

    /** Splits table rows for [units]; partial final splits show their real distance. */
    fun splits(detail: ActivityDetail, units: UnitSystem): List<SplitRow> {
        val splits = detail.splits(units)
        if (splits.isEmpty()) return emptyList()
        val sport = detail.activity.sport
        val speeds = splits.map { it.averageSpeed.metersPerSecond }
        val full = splits.indices.filter { !splits[it].isPartial && speeds[it] > 0 }
        val fastest = full.maxByOrNull { speeds[it] }
        val slowest = full.minByOrNull { speeds[it] }.takeIf { full.size >= 3 }
        val maxSpeed = speeds.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        var start = 0.0
        return splits.mapIndexed { i, split ->
            val end = start + split.distance.meters
            val row = SplitRow(
                label = if (split.isPartial) partialLabel(split, units) else (split.index + 1).toString(),
                speed = DetailFormat.speed(speeds[i], sport, units),
                barFraction = (speeds[i] / maxSpeed).toFloat().coerceIn(MIN_BAR, 1f),
                elevationDelta = split.elevationDelta?.let { DetailFormat.elevationDelta(it.meters, units) },
                heartRate = averageHeartRate(detail.points, start, end)?.let { it.roundToInt().toString() },
                isFastest = i == fastest && full.size >= 2,
                isSlowest = i == slowest,
                isPartial = split.isPartial,
                startMeters = start,
                endMeters = end,
            )
            start = end
            row
        }
    }

    /** Laps table rows; empty unless there are at least two laps worth comparing. */
    fun laps(detail: ActivityDetail, units: UnitSystem): List<LapRow> {
        val laps = detail.laps.filterNot { it.isZeroLength }
        if (laps.size < 2) return emptyList()
        val maxSpeed = laps.maxOf { it.averageSpeed.metersPerSecond }.takeIf { it > 0 } ?: 1.0
        return laps.mapIndexed { i, lap ->
            LapRow(
                number = i + 1,
                distance = DetailFormat.distance(lap.distance.meters, units),
                time = DetailFormat.duration(lap.movingTime),
                speed = DetailFormat.speed(lap.averageSpeed.metersPerSecond, detail.activity.sport, units),
                barFraction = (lap.averageSpeed.metersPerSecond / maxSpeed).toFloat().coerceIn(MIN_BAR, 1f),
            )
        }
    }

    /** Best efforts of this activity, flagged when they are the athlete's all-time best for the sport. */
    fun efforts(detail: ActivityDetail, bests: List<PersonalBest>, units: UnitSystem): List<EffortRow> {
        val id = detail.activity.id
        val distance = detail.bestEfforts.map { e ->
            val pb = bests.firstOrNull { abs(it.target - e.targetMeters) < 0.5 }
            EffortRow(
                name = DetailFormat.effortName(e.targetMeters),
                result = DetailFormat.clock(e.elapsedSeconds),
                isPersonalBest = pb != null && (pb.activityId == id || e.elapsedSeconds <= pb.value),
            )
        }
        val duration = detail.durationEfforts.map { e ->
            val d = DetailFormat.distance(e.distanceMeters, units)
            EffortRow("Best ${DetailFormat.effortDuration(e.durationSeconds)}", "${d.value} ${d.unit}", isPersonalBest = false)
        }
        return distance + duration
    }

    /** Where the elevation figures come from, shown under the stats (activity-detail 1.5). */
    fun elevationSource(source: AltitudeSource): String? = when (source) {
        AltitudeSource.BAROMETER -> "Elevation from the barometer"
        AltitudeSource.GPS -> "Elevation from GPS"
        AltitudeSource.NONE -> null
    }

    /** True when the sport should list best efforts. */
    fun hasEfforts(sport: SportType): Boolean = sport.isRunLike || sport.isRideLike

    private fun partialLabel(split: Split, units: UnitSystem): String = DetailFormat.distance(split.distance.meters, units).value

    /** Mean heart rate of the moving samples between [startMeters] and [endMeters] (cumulative distance). */
    internal fun averageHeartRate(points: List<ActivityPoint>, startMeters: Double, endMeters: Double): Double? {
        if (points.isEmpty()) return null
        val origin = points.first().distanceMeters
        var sum = 0.0
        var count = 0
        for (p in points) {
            val d = p.distanceMeters - origin
            if (d < startMeters || d >= endMeters || p.paused) continue
            val hr = p.heartRate ?: continue
            if (hr !in 30..230) continue
            sum += hr
            count++
        }
        return if (count == 0) null else sum / count
    }

    private const val MIN_BAR = 0.12f
}
