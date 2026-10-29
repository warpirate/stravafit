package routeexport

import kotlin.math.cos
import kotlin.math.hypot
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
     *
     * Two guards keep the curve on the route: at a sharp turn (more than [CORNER_DEGREES]) the tangent
     * is zero, so the corner stays a corner; and every handle is at most a third of its segment, so a
     * short segment next to a long one cannot throw a stub past the corner.
     */
    fun smooth(pts: List<XY>, from: Int = 0, to: Int = pts.size - 1): String = buildString {
        val n = pts.size
        append("M").append(pts[from].x.f()).append(' ').append(pts[from].y.f())
        for (i in from until to) {
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val seg = hypot(p2.x - p1.x, p2.y - p1.y)
            val t1 = tangent(pts, i, n)
            val t2 = tangent(pts, i + 1, n)
            val c1 = handle(p1, t1, seg, 1.0)
            val c2 = handle(p2, t2, seg, -1.0)
            append(" C").append(c1.x.f()).append(' ').append(c1.y.f())
                .append(' ').append(c2.x.f()).append(' ').append(c2.y.f())
                .append(' ').append(p2.x.f()).append(' ').append(p2.y.f())
        }
    }

    /** Catmull–Rom tangent at [i] divided by 6 (the Bézier handle), or zero at a sharp corner. */
    private fun tangent(pts: List<XY>, i: Int, n: Int): XY {
        val p0 = pts[max(i - 1, 0)]
        val p1 = pts[i]
        val p2 = pts[min(i + 1, n - 1)]
        val ax = p1.x - p0.x
        val ay = p1.y - p0.y
        val bx = p2.x - p1.x
        val by = p2.y - p1.y
        val la = hypot(ax, ay)
        val lb = hypot(bx, by)
        if (la > 0 && lb > 0) {
            val cos = (ax * bx + ay * by) / (la * lb)
            if (cos < CORNER_COS) return XY(0.0, 0.0)
        }
        return XY((p2.x - p0.x) / 6, (p2.y - p0.y) / 6)
    }

    private fun handle(p: XY, t: XY, segment: Double, sign: Double): XY {
        val len = hypot(t.x, t.y)
        val limit = segment / 3
        val k = if (len > limit && len > 0) limit / len else 1.0
        return XY(p.x + sign * t.x * k, p.y + sign * t.y * k)
    }

    private const val CORNER_DEGREES = 60.0
    private val CORNER_COS = cos(Math.toRadians(CORNER_DEGREES))
}
