// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.chart

import androidx.compose.runtime.Immutable
import org.openactivity.core.data.model.ActivityPoint
import org.openactivity.metrics.MovingTimeRule
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** What a chart plots. */
enum class ChartMetric {
    /** Speed in m/s; shown as pace for pace sports (faster is always up). */
    SPEED,

    /** Altitude in metres. */
    ELEVATION,

    /** Heart rate in bpm. */
    HEART_RATE,
}

/** Whether the shared x axis is distance (metres) or time since the start (seconds). */
enum class ChartAxis { DISTANCE, TIME }

/**
 * One plotted series over the shared x samples of [ChartData]. Gaps (stops, sensor dropouts) are NaN.
 *
 * @property average time-weighted average over the plotted samples, for the average line.
 * @property min smallest finite value; [max] the largest. Both are NaN when the series is empty.
 */
@Immutable
class ChartSeries(
    val metric: ChartMetric,
    val values: DoubleArray,
    val min: Double,
    val max: Double,
    val average: Double?,
) {
    /** True when at least two values can be drawn. */
    val isDrawable: Boolean get() = values.count { it.isFinite() } >= 2 && max.isFinite()
}

/**
 * Downsampled chart data shared by every chart of one activity, so scrubbing any chart addresses the
 * same sample [x] index on all of them and on the map.
 *
 * @property x sample positions on [axis] (metres or seconds), increasing.
 * @property positions the map position of each sample, or null where it has none.
 */
@Immutable
class ChartData(
    val axis: ChartAxis,
    val x: DoubleArray,
    val series: List<ChartSeries>,
    val positions: List<LatLng?>,
) {
    /** Series for [metric] when it can be drawn. */
    fun seriesFor(metric: ChartMetric): ChartSeries? = series.firstOrNull { it.metric == metric && it.isDrawable }

    /** Largest x value. */
    val xMax: Double get() = if (x.isEmpty()) 0.0 else x[x.size - 1]
}

/** Pure chart maths: smoothing, downsampling, scrub lookups and axis ticks. Shared by UI and tests. */
object ChartMath {
    /** Default number of plotted samples: about one per 1.5 dp on a phone-width chart. */
    const val DEFAULT_SAMPLES: Int = 300

    /** Default width of the centred speed smoothing window. */
    const val DEFAULT_SPEED_WINDOW_SECONDS: Double = 15.0

    /**
     * Builds chart data from stored [points] of a [sport] activity, or null when there are fewer than two
     * samples. The x axis is distance unless the activity hardly moved (indoor sessions), then time.
     */
    fun build(
        points: List<ActivityPoint>,
        sport: SportType,
        maxSamples: Int = DEFAULT_SAMPLES,
        speedWindowSeconds: Double = DEFAULT_SPEED_WINDOW_SECONDS,
    ): ChartData? {
        if (points.size < 2) return null
        val n = points.size
        val time = DoubleArray(n) { (points[it].offsetMillis - points[0].offsetMillis) / 1000.0 }
        val distance = DoubleArray(n) { points[it].distanceMeters }
        val axis = if (distance[n - 1] - distance[0] >= MIN_DISTANCE_AXIS_METERS) ChartAxis.DISTANCE else ChartAxis.TIME
        val xs = if (axis == ChartAxis.DISTANCE) DoubleArray(n) { distance[it] - distance[0] } else time

        val stopBelow = MovingTimeRule.forSport(sport).thresholdMps
        val rawSpeed = DoubleArray(n) { i -> if (points[i].paused) Double.NaN else points[i].speedMps }
        val smoothed = movingAverage(time, rawSpeed, speedWindowSeconds)
        val speed = DoubleArray(n) { i -> if (smoothed[i] >= stopBelow) smoothed[i] else Double.NaN }
        val elevation = DoubleArray(n) { points[it].elevationMeters ?: Double.NaN }
        val heartRate = DoubleArray(n) { points[it].heartRate?.toDouble()?.takeIf { hr -> hr in PLAUSIBLE_HR } ?: Double.NaN }

        val buckets = buckets(n, maxSamples)
        val x = DoubleArray(buckets.size) { b -> mean(xs, buckets[b]).let { if (it.isNaN()) xs[buckets[b].first] else it } }
        val positions = buckets.map { range ->
            val p = points[(range.first + range.last) / 2]
            LatLng(p.latitude, p.longitude)
        }
        val series = listOf(
            ChartMetric.SPEED to speed,
            ChartMetric.ELEVATION to elevation,
            ChartMetric.HEART_RATE to heartRate,
        ).map { (metric, values) ->
            val down = DoubleArray(buckets.size) { b -> mean(values, buckets[b]) }
            ChartSeries(metric, down, minFinite(down), maxFinite(down), timeWeightedAverage(time, values))
        }
        return ChartData(axis, x, series, positions)
    }

    /**
     * Centred moving average of [values] over a window of [windowSeconds] on [time]. NaN samples are
     * skipped and stay NaN, so gaps are kept. Runs in O(n) with two pointers.
     */
    fun movingAverage(time: DoubleArray, values: DoubleArray, windowSeconds: Double): DoubleArray {
        require(time.size == values.size) { "values must match time" }
        val n = values.size
        val out = DoubleArray(n)
        if (windowSeconds <= 0.0) return values.copyOf()
        val half = windowSeconds / 2
        var lo = 0
        var hi = 0
        var sum = 0.0
        var count = 0
        for (i in 0 until n) {
            while (hi < n && time[hi] <= time[i] + half) {
                if (values[hi].isFinite()) {
                    sum += values[hi]
                    count++
                }
                hi++
            }
            while (time[lo] < time[i] - half) {
                if (values[lo].isFinite()) {
                    sum -= values[lo]
                    count--
                }
                lo++
            }
            out[i] = if (values[i].isFinite() && count > 0) sum / count else Double.NaN
        }
        return out
    }

    /** Splits [n] samples into at most [maxBuckets] consecutive ranges of (nearly) equal size. */
    fun buckets(n: Int, maxBuckets: Int): List<IntRange> {
        require(maxBuckets > 0) { "need at least one bucket" }
        if (n <= maxBuckets) return List(n) { it..it }
        return List(maxBuckets) { b ->
            val start = (b.toLong() * n / maxBuckets).toInt()
            val end = ((b + 1).toLong() * n / maxBuckets).toInt() - 1
            start..end
        }
    }

    /**
     * Index of the sample in [x] (increasing) nearest to [value]; values outside the range clamp to the
     * first or last sample. -1 for an empty array.
     */
    fun nearestIndex(x: DoubleArray, value: Double): Int {
        if (x.isEmpty()) return -1
        var lo = 0
        var hi = x.size - 1
        if (value <= x[lo]) return lo
        if (value >= x[hi]) return hi
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (x[mid] <= value) lo = mid else hi = mid
        }
        return if (value - x[lo] <= x[hi] - value) lo else hi
    }

    /** Sample index under a touch at [touchX] pixels on a chart [widthPx] wide covering 0..[ChartData.xMax]. */
    fun indexAtTouch(data: ChartData, touchX: Float, widthPx: Float): Int {
        if (widthPx <= 0f || data.x.isEmpty()) return -1
        val fraction = (touchX / widthPx).coerceIn(0f, 1f)
        return nearestIndex(data.x, fraction * data.xMax)
    }

    /**
     * "Nice" axis bounds and tick values (steps of 1, 2, 2.5 or 5 times a power of ten) covering
     * [min]..[max] with at most [maxTicks] ticks.
     */
    fun niceTicks(min: Double, max: Double, maxTicks: Int = 4): List<Double> {
        require(maxTicks >= 2) { "need at least two ticks" }
        if (!min.isFinite() || !max.isFinite()) return emptyList()
        val span = if (max - min > 1e-9) max - min else max(abs(max), 1.0)
        val rough = span / (maxTicks - 1)
        val magnitude = 10.0.pow(floor(log10(rough)))
        val step = NICE_STEPS.map { it * magnitude }.first { it >= rough - 1e-12 }
        val first = floor(min / step) * step
        val last = ceil(max / step) * step
        val ticks = ArrayList<Double>()
        var v = first
        while (v <= last + step * 1e-6 && ticks.size <= maxTicks + 1) {
            ticks += if (abs(v) < step * 1e-9) 0.0 else v
            v += step
        }
        return ticks
    }

    /** [min]..[max] widened by [fraction] of the span on each side (and never empty). */
    fun padded(min: Double, max: Double, fraction: Double = 0.08): ClosedFloatingPointRange<Double> {
        val span = max - min
        val pad = if (span > 1e-9) span * fraction else max(abs(max) * 0.1, 1.0)
        return (min - pad)..(max + pad)
    }

    private fun mean(values: DoubleArray, range: IntRange): Double {
        var sum = 0.0
        var count = 0
        for (i in range) {
            val v = values[i]
            if (v.isFinite()) {
                sum += v
                count++
            }
        }
        return if (count == 0) Double.NaN else sum / count
    }

    private fun minFinite(values: DoubleArray): Double = values.filter { it.isFinite() }.minOrNull() ?: Double.NaN

    private fun maxFinite(values: DoubleArray): Double = values.filter { it.isFinite() }.maxOrNull() ?: Double.NaN

    private fun timeWeightedAverage(time: DoubleArray, values: DoubleArray): Double? {
        var sum = 0.0
        var weight = 0.0
        for (i in 1 until time.size) {
            val dt = time[i] - time[i - 1]
            val v = values[i - 1]
            if (dt <= 0.0 || dt > MAX_GAP_SECONDS || !v.isFinite()) continue
            sum += v * dt
            weight += dt
        }
        return if (weight > 0.0) sum / weight else null
    }

    private val NICE_STEPS = listOf(1.0, 2.0, 2.5, 5.0, 10.0)
    private val PLAUSIBLE_HR = 30.0..230.0
    private const val MAX_GAP_SECONDS = 30.0

    /** Below this total distance the x axis switches to time. */
    const val MIN_DISTANCE_AXIS_METERS: Double = 100.0
}
