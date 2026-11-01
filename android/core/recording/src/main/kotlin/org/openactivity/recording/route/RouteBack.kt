// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.route

import org.openactivity.geo.LocalTangentPlane
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import kotlin.math.floor
import kotlin.math.hypot
import java.util.PriorityQueue

/**
 * A way back to the start, from the athlete's own track: no network or routing data needed.
 *
 * @property points from the current position to the start.
 * @property distanceMeters length of [points].
 * @property straightLineMeters as the crow flies.
 */
public data class RouteBackPlan(val points: List<LatLng>, val distanceMeters: Double, val straightLineMeters: Double)

/**
 * Plans "route back to start" over the athlete's own track. The track is a graph: each point links to the
 * next, and to any other point within [JOIN_METERS] (the same street passed twice). The shortest way back
 * through that graph retraces the track but skips loops: a run out along a road and around a park comes
 * back along the road, not around the park again.
 */
public object RouteBack {
    /** Track points closer together than this are thinned out first. */
    private const val THIN_METERS = 10.0

    /** Two parts of the track this close are treated as the same place. */
    public const val JOIN_METERS: Double = 25.0

    /** Returns the plan from the last point of [track] to its first, or null with fewer than two points. */
    public fun plan(track: List<LatLng>): RouteBackPlan? {
        val thin = thin(track)
        if (thin.size < 2) return null
        val n = thin.size
        val plane = LocalTangentPlane(thin.first())
        val xs = DoubleArray(n) { plane.east(thin[it]) }
        val ys = DoubleArray(n) { plane.north(thin[it]) }
        val grid = HashMap<Long, MutableList<Int>>()
        for (i in 0 until n) grid.getOrPut(cell(xs[i], ys[i])) { ArrayList() } += i

        // Dijkstra from the current position (last point) to the start (first point).
        val dist = DoubleArray(n) { Double.POSITIVE_INFINITY }
        val previous = IntArray(n) { -1 }
        val done = BooleanArray(n)
        val queue = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        dist[n - 1] = 0.0
        queue += 0.0 to (n - 1)
        while (queue.isNotEmpty()) {
            val (d, u) = queue.poll()
            if (done[u]) continue
            done[u] = true
            if (u == 0) break
            fun relax(v: Int) {
                if (v < 0 || v >= n || done[v]) return
                val nd = d + hypot(xs[v] - xs[u], ys[v] - ys[u])
                if (nd < dist[v]) {
                    dist[v] = nd
                    previous[v] = u
                    queue += nd to v
                }
            }
            relax(u - 1)
            relax(u + 1)
            val cx = floor(xs[u] / JOIN_METERS).toLong()
            val cy = floor(ys[u] / JOIN_METERS).toLong()
            for (dx in -1L..1L) for (dy in -1L..1L) {
                val bucket = grid[key(cx + dx, cy + dy)] ?: continue
                for (v in bucket) {
                    if (hypot(xs[v] - xs[u], ys[v] - ys[u]) <= JOIN_METERS) relax(v)
                }
            }
        }
        val out = ArrayList<LatLng>()
        var at = 0
        while (at != -1) {
            out += thin[at]
            at = previous[at]
        }
        out.reverse()
        return RouteBackPlan(
            points = out,
            distanceMeters = Spherical.pathLength(out),
            straightLineMeters = Spherical.distance(thin.last(), thin.first()),
        )
    }

    private fun thin(track: List<LatLng>): List<LatLng> {
        val out = ArrayList<LatLng>()
        for (p in track) {
            if (!p.latitude.isFinite() || !p.longitude.isFinite()) continue
            if (out.isEmpty() || Spherical.distance(out.last(), p) >= THIN_METERS) out += p
        }
        // Always end at the athlete's current position.
        val last = track.lastOrNull()
        if (last != null && out.isNotEmpty() && out.last() != last && last.latitude.isFinite() && last.longitude.isFinite()) out += last
        return out
    }

    private fun cell(x: Double, y: Double): Long = key(floor(x / JOIN_METERS).toLong(), floor(y / JOIN_METERS).toLong())

    private fun key(cx: Long, cy: Long): Long = (cx shl 32) xor (cy and 0xFFFFFFFFL)
}
