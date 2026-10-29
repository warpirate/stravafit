package routeexport

import kotlin.math.roundToLong

/** Google encoded polyline (precision 1e5 by default) — the format used by most activity APIs. */
object Polyline {
    fun decode(encoded: String, precision: Int = 5): List<TrackPoint> {
        val factor = Math.pow(10.0, precision.toDouble())
        val out = ArrayList<TrackPoint>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            var result = 0L
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1L != 0L) (result shr 1).inv() else result shr 1

            result = 0L
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20)
            lon += if (result and 1L != 0L) (result shr 1).inv() else result shr 1

            out += TrackPoint(lat / factor, lon / factor)
        }
        return out
    }

    fun encode(points: List<TrackPoint>, precision: Int = 5): String {
        val factor = Math.pow(10.0, precision.toDouble())
        val sb = StringBuilder()
        var prevLat = 0L
        var prevLon = 0L
        for (p in points) {
            val lat = (p.lat * factor).roundToLong()
            val lon = (p.lon * factor).roundToLong()
            encodeValue(lat - prevLat, sb)
            encodeValue(lon - prevLon, sb)
            prevLat = lat
            prevLon = lon
        }
        return sb.toString()
    }

    private fun encodeValue(v: Long, sb: StringBuilder) {
        var value = if (v < 0) (v shl 1).inv() else v shl 1
        while (value >= 0x20) {
            sb.append(((0x20L or (value and 0x1f)) + 63).toInt().toChar())
            value = value shr 5
        }
        sb.append((value + 63).toInt().toChar())
    }
}
