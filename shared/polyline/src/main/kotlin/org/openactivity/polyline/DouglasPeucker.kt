// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.polyline

import org.openactivity.geo.LocalTangentPlane
import org.openactivity.geo.PlanarGeometry
import org.openactivity.model.LatLng

/**
 * Ramer–Douglas–Peucker line simplification with a tolerance in metres.
 *
 * Points are projected once into a tangent plane at the track's first point, which keeps the work
 * O(n log n) on typical tracks with no trigonometry in the inner loop. The implementation is iterative,
 * so very long tracks cannot overflow the stack. First and last points are always kept.
 */
public object DouglasPeucker {
    /** Returns the indices of [points] kept at [toleranceMeters], in ascending order. */
    public fun simplifyIndices(points: List<LatLng>, toleranceMeters: Double): IntArray {
        require(toleranceMeters >= 0.0) { "tolerance must not be negative: $toleranceMeters" }
        val n = points.size
        if (n <= 2) return IntArray(n) { it }
        val plane = LocalTangentPlane(points[0])
        val xs = DoubleArray(n) { plane.east(points[it]) }
        val ys = DoubleArray(n) { plane.north(points[it]) }
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true
        val stack = ArrayDeque<Long>()
        stack.addLast(pack(0, n - 1))
        while (stack.isNotEmpty()) {
            val range = stack.removeLast()
            val first = (range ushr 32).toInt()
            val last = range.toInt()
            var maxDist = -1.0
            var maxIndex = -1
            for (i in first + 1 until last) {
                val d = PlanarGeometry.pointToSegmentDistance(xs[i], ys[i], xs[first], ys[first], xs[last], ys[last])
                if (d > maxDist) {
                    maxDist = d
                    maxIndex = i
                }
            }
            if (maxIndex >= 0 && maxDist > toleranceMeters) {
                keep[maxIndex] = true
                stack.addLast(pack(first, maxIndex))
                stack.addLast(pack(maxIndex, last))
            }
        }
        var count = 0
        for (k in keep) if (k) count++
        val out = IntArray(count)
        var j = 0
        for (i in 0 until n) if (keep[i]) out[j++] = i
        return out
    }

    /** Returns the simplified list of points at [toleranceMeters]. */
    public fun simplify(points: List<LatLng>, toleranceMeters: Double): List<LatLng> =
        simplifyIndices(points, toleranceMeters).map { points[it] }

    private fun pack(a: Int, b: Int): Long = (a.toLong() shl 32) or (b.toLong() and 0xffffffffL)
}
