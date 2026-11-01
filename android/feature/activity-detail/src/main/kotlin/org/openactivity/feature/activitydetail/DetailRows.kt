// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail

import androidx.compose.runtime.Immutable
import org.openactivity.core.data.model.ActivityDetail
import org.openactivity.core.data.model.ActivityPoint
import org.openactivity.core.data.model.EffortMedal
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.repository.EffortRanking
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.feature.activitydetail.format.DetailFormat
import org.openactivity.feature.activitydetail.format.Measure
import org.openactivity.metrics.GradeAdjustedPace
import org.openactivity.model.SpeedDisplay
import org.openactivity.model.SpeedDisplays
import org.openactivity.model.SportType
import org.openactivity.model.Split
import org.openactivity.model.UnitSystem
import org.openactivity.recording.AltitudeSource
import kotlin.math.roundToInt

/**
 * One row of the splits table.
 *
 * @property label split number, or the real distance of a final partial split ("0.43").
 * @property barFraction bar length relative to the fastest split (0..1).
 * @property heartRate average heart rate in the split, or null when it has none (shown as a dash).
 * @property gap grade-adjusted pace (or speed) of the split, for runs with elevation; null otherwise.
 * @property gapDiffers true when [gap] reads differently from [speed], i.e. the split was hilly.
 */
@Immutable
data class SplitRow(
    val label: String,
    val speed: Measure?,
    val gap: Measure? = null,
    val gapDiffers: Boolean = false,
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
 * @property rank its medal: place 1 to 3 among your efforts as of this activity, or null without one.
 * @property yearly true when the place is within [year] only (the all-time podium was out of reach).
 */
@Immutable
data class EffortRow(
    val name: String,
    val result: String,
    val rank: Int? = null,
    val yearly: Boolean = false,
    val year: Int = 0,
) {
    /** An all-time first place. */
    val isPersonalBest: Boolean get() = rank == 1 && !yearly

    /** "PR", "2nd best", "Best of 2026"..., or null without a medal. */
    val medalLabel: String?
        get() = when {
            rank == null -> null
            !yearly -> if (rank == 1) "PR" else "${ordinal(rank)} best"
            rank == 1 -> "Best of $year"
            else -> "${ordinal(rank)} of $year"
        }

    private fun ordinal(n: Int): String = when (n) {
        1 -> "1st"
        2 -> "2nd"
        3 -> "3rd"
        else -> "${n}th"
    }
}

/** Builds the detail screen's display rows from stored data. Pure, so it is unit-tested directly. */
object DetailRows {
    /**
     * Stats grid: distance, pace/speed, the headline time, elevation gain, the other time, grade-adjusted
     * pace (runs), heart rate and sensors. The headline time is the moving time, or the elapsed time for
     * sports that headline it ([SportType.headlinesElapsedTime], activity-detail 1.1); both are always shown.
     * Rows are always full (three per row, as in the mockup): a partial last row is completed with Avg
     * Heart Rate (a dash without a sensor), Elev Loss and the maximum pace or speed.
     *
     * @param display how pace or speed is shown (the athlete's per-sport choice).
     */
    fun stats(
        activity: SavedActivity,
        units: UnitSystem,
        hasElevation: Boolean,
        display: SpeedDisplay = SpeedDisplays.resolve(activity.sport, units),
    ): List<Stat> {
        val base = baseStats(activity, units, hasElevation, display)
        if (base.size % STAT_COLUMNS == 0) return base
        val labels = base.map { it.label }.toSet()
        val fillers = buildList {
            if ("Avg Heart Rate" !in labels) add(Stat("Avg Heart Rate", "\u2014", null, "no heart rate sensor"))
            if (hasElevation) {
                val loss = DetailFormat.elevation(activity.elevationLossMeters, units)
                add(Stat("Elev Loss", loss.value, loss.unit, loss.spokenUnit))
            }
            val max = DetailFormat.speed(activity.maxSpeedMps, display, units)
            val maxLabel = DetailFormat.speedLabel(display, "Max")
            if (max != null && maxLabel !in labels) add(Stat(maxLabel, max.value, max.unit, max.spokenUnit))
        }
        val missing = STAT_COLUMNS - base.size % STAT_COLUMNS
        return base + fillers.take(missing)
    }

    private const val STAT_COLUMNS = 3

    private fun baseStats(activity: SavedActivity, units: UnitSystem, hasElevation: Boolean, display: SpeedDisplay): List<Stat> = buildList {
        val distance = DetailFormat.distance(activity.distanceMeters, units)
        add(Stat("Distance", distance.value, distance.unit, distance.spokenUnit))
        val speed = DetailFormat.speed(activity.averageSpeedMps, display, units)
        add(Stat(DetailFormat.speedLabel(display, "Avg"), speed?.value, speed?.unit, speed?.spokenUnit))
        val moving = Stat("Moving Time", DetailFormat.duration(activity.movingTime))
        val elapsed = Stat("Elapsed Time", DetailFormat.duration(activity.elapsedTime))
        val elapsedFirst = activity.sport.headlinesElapsedTime
        add(if (elapsedFirst) elapsed else moving)
        if (hasElevation) {
            val gain = DetailFormat.elevation(activity.elevationGainMeters, units)
            add(Stat("Elev Gain", gain.value, gain.unit, gain.spokenUnit))
        }
        add(if (elapsedFirst) moving else elapsed)
        gradeAdjusted(activity, display, units)?.let { add(Stat("Avg GAP", it.value, it.unit, it.spokenUnit)) }
        if (display == SpeedDisplay.SPEED) {
            val max = DetailFormat.speed(activity.maxSpeedMps, display, units)
            if (max != null) add(Stat("Max Speed", max.value, max.unit, max.spokenUnit))
        }
        activity.averageHeartRate?.let { val hr = DetailFormat.heartRate(it); add(Stat("Avg Heart Rate", hr.value, hr.unit, hr.spokenUnit)) }
        activity.maxHeartRate?.let { val hr = DetailFormat.heartRate(it.toDouble()); add(Stat("Max Heart Rate", hr.value, hr.unit, hr.spokenUnit)) }
        activity.averagePower?.let { add(Stat("Avg Power", it.roundToInt().toString(), "W", "watts")) }
        activity.averageCadence?.let { add(Stat("Cadence", it.roundToInt().toString(), DetailFormat.cadenceUnit(activity.sport))) }
    }

    /** Average grade-adjusted pace of a run with elevation data, in [display]; null for other activities. */
    fun gradeAdjusted(activity: SavedActivity, display: SpeedDisplay, units: UnitSystem): Measure? {
        if (!activity.sport.isRunLike) return null
        val gap = activity.averageGradeAdjustedSpeedMps ?: return null
        return DetailFormat.speed(gap, display, units)
    }

    /**
     * Splits table rows for [units]; partial final splits show their real distance. Runs with elevation
     * also get each split's grade-adjusted pace.
     */
    fun splits(
        detail: ActivityDetail,
        units: UnitSystem,
        display: SpeedDisplay = SpeedDisplays.resolve(detail.activity.sport, units),
    ): List<SplitRow> {
        val splits = detail.splits(units)
        if (splits.isEmpty()) return emptyList()
        val sport = detail.activity.sport
        val gapStream = if (sport.isRunLike) GapStream.of(detail.points) else null
        val speeds = splits.map { it.averageSpeed.metersPerSecond }
        val full = splits.indices.filter { !splits[it].isPartial && speeds[it] > 0 }
        val fastest = full.maxByOrNull { speeds[it] }
        val slowest = full.minByOrNull { speeds[it] }.takeIf { full.size >= 3 }
        val maxSpeed = speeds.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        var start = 0.0
        return splits.mapIndexed { i, split ->
            val end = start + split.distance.meters
            val speed = DetailFormat.speed(speeds[i], display, units)
            val gap = gapStream?.averageSpeed(start, end)?.let { DetailFormat.speed(it, display, units) }
            val row = SplitRow(
                label = if (split.isPartial) partialLabel(split, units) else (split.index + 1).toString(),
                speed = speed,
                gap = gap,
                gapDiffers = gap != null && speed != null && gap.value != speed.value,
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

    /**
     * True when the splits table shows a GAP column: a run where at least one split's grade-adjusted pace
     * reads differently from its pace (activity-detail 2.2, improved: shown whenever hills change it).
     */
    fun showsSplitGap(rows: List<SplitRow>): Boolean = rows.any { it.gapDiffers }

    /** Laps table rows; empty unless there are at least two laps worth comparing. */
    fun laps(
        detail: ActivityDetail,
        units: UnitSystem,
        display: SpeedDisplay = SpeedDisplays.resolve(detail.activity.sport, units),
    ): List<LapRow> {
        val laps = detail.laps.filterNot { it.isZeroLength }
        if (laps.size < 2) return emptyList()
        val maxSpeed = laps.maxOf { it.averageSpeed.metersPerSecond }.takeIf { it > 0 } ?: 1.0
        return laps.mapIndexed { i, lap ->
            LapRow(
                number = i + 1,
                distance = DetailFormat.distance(lap.distance.meters, units),
                time = DetailFormat.duration(lap.movingTime),
                speed = DetailFormat.speed(lap.averageSpeed.metersPerSecond, display, units),
                barFraction = (lap.averageSpeed.metersPerSecond / maxSpeed).toFloat().coerceIn(MIN_BAR, 1f),
            )
        }
    }

    /**
     * Best efforts of this activity with the medals they earned when it was recorded: places 1 to 3
     * all-time, else within that year ([medals], from `EffortRanking.medalsOf`).
     */
    fun efforts(detail: ActivityDetail, medals: List<EffortMedal>, units: UnitSystem): List<EffortRow> {
        val distance = detail.bestEfforts.map { e ->
            val medal = medals.firstOrNull { EffortRanking.sameTarget(it.target, e.targetMeters) }
            EffortRow(
                name = DetailFormat.effortName(e.targetMeters),
                result = DetailFormat.clock(e.elapsedSeconds),
                rank = medal?.rank,
                yearly = medal?.isYearly == true,
                year = medal?.year ?: 0,
            )
        }
        val duration = detail.durationEfforts.map { e ->
            val d = DetailFormat.distance(e.distanceMeters, units)
            EffortRow("Best ${DetailFormat.effortDuration(e.durationSeconds)}", "${d.value} ${d.unit}")
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

/**
 * Grade-adjusted distance along a stored stream, for per-split GAP. Grade is smoothed over
 * [GradeAdjustedPace.GRADE_WINDOW_M], the same model as the stored average.
 */
internal class GapStream private constructor(
    private val distance: DoubleArray,
    private val time: DoubleArray,
    private val grade: DoubleArray,
    private val moving: BooleanArray,
) {
    /**
     * Grade-adjusted speed (m/s) over the moving intervals whose middle lies between [startMeters] and
     * [endMeters] from the start; null when that stretch has no moving time.
     */
    fun averageSpeed(startMeters: Double, endMeters: Double): Double? =
        GradeAdjustedPace.averageSpeed(distance, time, grade, moving) { i ->
            val mid = (distance[i] + distance[i - 1]) / 2
            mid >= startMeters && mid < endMeters
        }

    companion object {

        /** The stream of [points], or null without at least two altitude samples. */
        fun of(points: List<ActivityPoint>): GapStream? {
            if (points.size < 2 || points.count { it.elevationMeters != null } < 2) return null
            val origin = points.first().distanceMeters
            val distance = DoubleArray(points.size) { points[it].distanceMeters - origin }
            val altitude = DoubleArray(points.size) { points[it].elevationMeters ?: Double.NaN }
            val time = DoubleArray(points.size) { (points[it].offsetMillis - points[0].offsetMillis) / 1000.0 }
            val moving = BooleanArray(points.size) { !points[it].paused }
            return GapStream(distance, time, GradeAdjustedPace.smoothedGrade(distance, altitude), moving)
        }
    }
}
