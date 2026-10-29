package routeexport

import kotlin.math.max
import kotlin.math.min

/** Per-point values used to colour the route, normalised to [0,1]. */
object Metrics {
    private const val SPEED_WINDOW = 5
    private const val SMOOTH_WINDOW = 7

    fun cumulative(points: List<TrackPoint>): DoubleArray {
        val out = DoubleArray(points.size)
        for (i in 1 until points.size) out[i] = out[i - 1] + Geo.haversine(points[i - 1], points[i])
        return out
    }

    /** Raw series or null when the data is not available. Speed is m/s (higher = faster). */
    fun raw(points: List<TrackPoint>, colorBy: ColorBy): DoubleArray? = when (colorBy) {
        ColorBy.SOLID -> null
        ColorBy.PROGRESS -> cumulative(points)
        ColorBy.PACE -> speed(points)
        ColorBy.HEART_RATE -> carryForward(points.map { it.hr?.toDouble() })
        ColorBy.ELEVATION -> carryForward(points.map { it.ele })
        ColorBy.POWER -> carryForward(points.map { it.power?.toDouble() })
    }

    fun normalized(points: List<TrackPoint>, colorBy: ColorBy): DoubleArray? {
        if (colorBy == ColorBy.SOLID || points.isEmpty()) return null
        val series = raw(points, colorBy) ?: cumulative(points)
        val smoothed = if (colorBy == ColorBy.PROGRESS) series else movingAverage(series, SMOOTH_WINDOW)
        return robustNormalize(smoothed)
    }

    private fun speed(points: List<TrackPoint>): DoubleArray? {
        if (points.size < 2 || points.any { it.time == null }) return null
        val cum = cumulative(points)
        val n = points.size
        return DoubleArray(n) { i ->
            val a = max(0, i - SPEED_WINDOW)
            val b = min(n - 1, i + SPEED_WINDOW)
            val dt = points[b].time!! - points[a].time!!
            if (dt > 0) (cum[b] - cum[a]) / dt else 0.0
        }
    }

    private fun carryForward(values: List<Double?>): DoubleArray? {
        val first = values.firstOrNull { it != null } ?: return null
        var last = first
        return DoubleArray(values.size) { i -> (values[i] ?: last).also { last = it } }
    }

    private fun movingAverage(v: DoubleArray, window: Int): DoubleArray {
        val n = v.size
        val half = window / 2
        return DoubleArray(n) { i ->
            val a = max(0, i - half)
            val b = min(n - 1, i + half)
            var s = 0.0
            for (j in a..b) s += v[j]
            s / (b - a + 1)
        }
    }

    /** Clamp to the 5th–95th percentile so a single GPS spike does not flatten the gradient. */
    private fun robustNormalize(v: DoubleArray): DoubleArray {
        if (v.isEmpty()) return v
        val sorted = v.sortedArray()
        val lo = sorted[((sorted.size - 1) * 0.05).toInt()]
        val hi = sorted[((sorted.size - 1) * 0.95).toInt()]
        if (hi - lo < 1e-9) return DoubleArray(v.size) { 0.5 }
        return DoubleArray(v.size) { ((v[it] - lo) / (hi - lo)).coerceIn(0.0, 1.0) }
    }
}
