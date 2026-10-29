package routeexport

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class XY(val x: Double, val y: Double)

data class Box(val x: Double, val y: Double, val w: Double, val h: Double)

object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8
    private const val MAX_LAT = 85.05112878

    fun haversine(a: TrackPoint, b: TrackPoint): Double {
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(b.lat)
        val dp = p2 - p1
        val dl = Math.toRadians(b.lon - a.lon)
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(min(1.0, h)))
    }

    /** Web Mercator in unit square: x,y in [0,1], y grows southwards (screen orientation). */
    fun mercator(p: TrackPoint): XY {
        val lat = Math.toRadians(p.lat.coerceIn(-MAX_LAT, MAX_LAT))
        val x = (p.lon + 180.0) / 360.0
        val y = (1.0 - ln(tan(lat) + 1.0 / cos(lat)) / PI) / 2.0
        return XY(x, y)
    }
}

/** Uniform-scale fit of Mercator coordinates into a box, centred, aspect preserved. */
class Projector private constructor(
    private val scale: Double,
    private val offX: Double,
    private val offY: Double,
) {
    fun map(m: XY) = XY(m.x * scale + offX, m.y * scale + offY)

    fun project(p: TrackPoint) = map(Geo.mercator(p))

    companion object {
        fun fit(tracks: List<List<TrackPoint>>, box: Box): Projector {
            val merc = tracks.flatten().map(Geo::mercator)
            if (merc.isEmpty()) return Projector(1.0, box.x, box.y)
            var minX = Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE
            for (m in merc) {
                minX = min(minX, m.x); maxX = max(maxX, m.x)
                minY = min(minY, m.y); maxY = max(maxY, m.y)
            }
            val bw = max(maxX - minX, 1e-12)
            val bh = max(maxY - minY, 1e-12)
            val s = min(box.w / bw, box.h / bh)
            return Projector(
                s,
                box.x + (box.w - (maxX - minX) * s) / 2 - minX * s,
                box.y + (box.h - (maxY - minY) * s) / 2 - minY * s,
            )
        }
    }
}
