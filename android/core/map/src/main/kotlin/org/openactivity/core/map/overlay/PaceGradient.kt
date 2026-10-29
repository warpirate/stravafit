// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.overlay

import androidx.annotation.ColorInt
import androidx.compose.runtime.Immutable
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import kotlin.math.roundToInt

/** One colour stop along a line, [progress] in [0, 1] of its length. */
@Immutable
data class GradientStop(val progress: Double, @ColorInt val color: Int)

/**
 * Turns per-point speeds into `line-gradient` stops (slow to fast along a colour ramp).
 *
 * Progress is measured by distance, matching MapLibre's `line-progress`. Speeds are scaled between
 * the 10th and 90th percentile so a single sprint or a traffic-light stop does not flatten the
 * rest of the ramp, and the line is split into at most [DEFAULT_MAX_STOPS] buckets so the style
 * expression stays small for long activities.
 */
object PaceGradient {
    /** Upper bound on buckets; MapLibre handles more, but beyond this the eye cannot tell. */
    const val DEFAULT_MAX_STOPS: Int = 48

    /**
     * Gradient stops for [points] coloured by [speeds] along [ramp], or null when there is nothing
     * to colour (fewer than two points, no speeds, or a zero-length line). Stops are strictly
     * increasing and always start at 0 and end at 1.
     */
    fun stops(
        points: List<LatLng>,
        speeds: List<Double?>,
        ramp: List<Int>,
        maxStops: Int = DEFAULT_MAX_STOPS,
    ): List<GradientStop>? {
        require(speeds.size == points.size) { "speeds must match points" }
        require(ramp.size >= 2) { "ramp needs at least two colours" }
        require(maxStops >= 1) { "maxStops must be positive" }
        if (points.size < 2) return null
        val filled = fillGaps(speeds) ?: return null

        val cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) cumulative[i] = cumulative[i - 1] + Spherical.distance(points[i - 1], points[i])
        val total = cumulative.last()
        if (total <= 0.0) return null

        val sorted = filled.sorted()
        val lo = percentile(sorted, 0.10)
        val hi = percentile(sorted, 0.90)
        if (hi - lo < MIN_RANGE) {
            val c = ColorMath.sample(ramp, 0.5)
            return listOf(GradientStop(0.0, c), GradientStop(1.0, c))
        }

        // Distance-weighted mean speed per bucket; each segment goes to the bucket holding its midpoint.
        val buckets = minOf(maxStops, points.size - 1)
        val weighted = DoubleArray(buckets)
        val lengths = DoubleArray(buckets)
        for (i in 0 until points.size - 1) {
            val length = cumulative[i + 1] - cumulative[i]
            if (length <= 0.0) continue
            val mid = (cumulative[i] + cumulative[i + 1]) / 2 / total
            val b = (mid * buckets).toInt().coerceIn(0, buckets - 1)
            weighted[b] += (filled[i] + filled[i + 1]) / 2 * length
            lengths[b] += length
        }
        val values = DoubleArray(buckets) { if (lengths[it] > 0) weighted[it] / lengths[it] else Double.NaN }
        carryOver(values)

        val colors = values.map { ColorMath.sample(ramp, ((it - lo) / (hi - lo)).coerceIn(0.0, 1.0)) }
        return buildList {
            add(GradientStop(0.0, colors.first()))
            for (b in 0 until buckets) add(GradientStop((b + 0.5) / buckets, colors[b]))
            add(GradientStop(1.0, colors.last()))
        }
    }

    /** Replaces nulls with the nearest earlier value (or the first later one); null if all are null. */
    private fun fillGaps(speeds: List<Double?>): DoubleArray? {
        val firstKnown = speeds.firstOrNull { it != null && it.isFinite() } ?: return null
        val out = DoubleArray(speeds.size)
        var last = firstKnown
        for (i in speeds.indices) {
            val s = speeds[i]
            if (s != null && s.isFinite()) last = s.coerceAtLeast(0.0)
            out[i] = last
        }
        return out
    }

    /** Fills NaN buckets (no segment midpoint landed there) from neighbours. */
    private fun carryOver(values: DoubleArray) {
        var last = values.firstOrNull { !it.isNaN() } ?: return
        for (i in values.indices) if (values[i].isNaN()) values[i] = last else last = values[i]
    }

    private fun percentile(sorted: List<Double>, q: Double): Double =
        sorted[((sorted.size - 1) * q).roundToInt().coerceIn(0, sorted.size - 1)]

    private const val MIN_RANGE = 1e-6
}

/** Colour helpers on ARGB ints. */
object ColorMath {
    /** Linear interpolation between [a] and [b] per channel, [t] in [0, 1]. */
    @ColorInt
    fun lerp(@ColorInt a: Int, @ColorInt b: Int, t: Double): Int {
        val u = t.coerceIn(0.0, 1.0)
        fun ch(shift: Int): Int {
            val ca = (a ushr shift) and 0xFF
            val cb = (b ushr shift) and 0xFF
            return (ca + (cb - ca) * u).roundToInt() and 0xFF
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** The colour at [t] in [0, 1] along a piecewise-linear [ramp]. */
    @ColorInt
    fun sample(ramp: List<Int>, t: Double): Int {
        require(ramp.isNotEmpty()) { "empty ramp" }
        if (ramp.size == 1) return ramp[0]
        val x = t.coerceIn(0.0, 1.0) * (ramp.size - 1)
        val i = x.toInt().coerceAtMost(ramp.size - 2)
        return lerp(ramp[i], ramp[i + 1], x - i)
    }
}
