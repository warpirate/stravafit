// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.overlay

import org.openactivity.core.map.style.RasterStyleJson
import org.openactivity.model.LatLng
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Minimal, allocation-light GeoJSON writer for overlay sources. Coordinates are written with six
 * decimals (about 0.1 m), independent of the device locale.
 */
object GeoJson {
    /** An empty FeatureCollection. */
    const val EMPTY: String = """{"type":"FeatureCollection","features":[]}"""

    /**
     * A FeatureCollection holding one LineString through [points], or [EMPTY] for fewer than two.
     *
     * Longitudes are unwrapped (a step of more than 180° is taken the short way, so values may leave
     * [-180, 180]); MapLibre then draws a track across the antimeridian as a short line instead of
     * one that circles the globe.
     */
    fun lineString(points: List<LatLng>): String {
        if (points.size < 2) return EMPTY
        val lons = unwrapLongitudes(points)
        val sb = StringBuilder(points.size * 24 + 96)
        sb.append("""{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},""")
        sb.append(""""geometry":{"type":"LineString","coordinates":[""")
        for (i in points.indices) {
            if (i > 0) sb.append(',')
            sb.append('[')
            appendFixed6(sb, lons[i])
            sb.append(',')
            appendFixed6(sb, points[i].latitude)
            sb.append(']')
        }
        sb.append("]}}]}")
        return sb.toString()
    }

    /** A FeatureCollection of Points, each with a single string property `role`. */
    fun rolePoints(points: List<Pair<LatLng, String>>): String {
        if (points.isEmpty()) return EMPTY
        val sb = StringBuilder(points.size * 96 + 48)
        sb.append("""{"type":"FeatureCollection","features":[""")
        points.forEachIndexed { i, (p, role) ->
            if (i > 0) sb.append(',')
            sb.append("""{"type":"Feature","properties":{"role":""")
            sb.append(RasterStyleJson.quote(role))
            sb.append("""},"geometry":{"type":"Point","coordinates":[""")
            appendFixed6(sb, p.longitude)
            sb.append(',')
            appendFixed6(sb, p.latitude)
            sb.append("]}}")
        }
        sb.append("]}")
        return sb.toString()
    }

    /** Longitudes of [points] with jumps over 180° removed (see [lineString]). */
    fun unwrapLongitudes(points: List<LatLng>): DoubleArray {
        val out = DoubleArray(points.size)
        if (points.isEmpty()) return out
        out[0] = points[0].longitude
        for (i in 1 until points.size) {
            var delta = points[i].longitude - points[i - 1].longitude
            if (delta > 180.0) delta -= 360.0 else if (delta < -180.0) delta += 360.0
            out[i] = out[i - 1] + delta
        }
        return out
    }

    /** Appends [v] with exactly six decimals and a '.' separator. */
    internal fun appendFixed6(sb: StringBuilder, v: Double) {
        val micro = (v * 1_000_000.0).roundToLong()
        if (micro < 0) sb.append('-')
        val a = abs(micro)
        sb.append(a / 1_000_000)
        sb.append('.')
        val frac = (a % 1_000_000).toString()
        repeat(6 - frac.length) { sb.append('0') }
        sb.append(frac)
    }
}
