package routeexport

import kotlin.math.max
import kotlin.math.min

/** Builds SVG path `d` strings. */
object PathData {
    fun polyline(pts: List<XY>, from: Int = 0, to: Int = pts.size - 1): String = buildString {
        append("M").append(pts[from].x.f()).append(' ').append(pts[from].y.f())
        for (i in from + 1..to) append(" L").append(pts[i].x.f()).append(' ').append(pts[i].y.f())
    }

    /**
     * Catmull–Rom spline through the points, emitted as cubic Béziers. Neighbours are taken from
     * the full list so adjacent chunks join without kinks.
     */
    fun smooth(pts: List<XY>, from: Int = 0, to: Int = pts.size - 1): String = buildString {
        val n = pts.size
        append("M").append(pts[from].x.f()).append(' ').append(pts[from].y.f())
        for (i in from until to) {
            val p0 = pts[max(i - 1, 0)]
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val p3 = pts[min(i + 2, n - 1)]
            val c1x = p1.x + (p2.x - p0.x) / 6
            val c1y = p1.y + (p2.y - p0.y) / 6
            val c2x = p2.x - (p3.x - p1.x) / 6
            val c2y = p2.y - (p3.y - p1.y) / 6
            append(" C").append(c1x.f()).append(' ').append(c1y.f())
                .append(' ').append(c2x.f()).append(' ').append(c2y.f())
                .append(' ').append(p2.x.f()).append(' ').append(p2.y.f())
        }
    }
}
