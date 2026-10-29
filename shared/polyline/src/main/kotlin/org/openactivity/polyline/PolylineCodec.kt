// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.polyline

import org.openactivity.model.LatLng
import kotlin.math.floor

/**
 * Encoded Polyline Algorithm Format (the format popularised by Google Maps).
 *
 * Precision 5 (1e-5 degrees, about 1.1 m) is the common interchange default; precision 6 is used by
 * OSRM/Valhalla-style routing engines. Values are rounded half-up, matching the reference implementation,
 * so encodings are byte-identical with other implementations.
 */
public object PolylineCodec {
    /** Default precision (decimal places) of the format. */
    public const val DEFAULT_PRECISION: Int = 5

    /** Encodes [points] with [precision] decimal places (1..7). */
    public fun encode(points: List<LatLng>, precision: Int = DEFAULT_PRECISION): String {
        val factor = factor(precision)
        val out = StringBuilder(points.size * 8)
        var prevLat = 0L
        var prevLon = 0L
        for (p in points) {
            val lat = round(p.latitude * factor)
            val lon = round(p.longitude * factor)
            encodeValue(lat - prevLat, out)
            encodeValue(lon - prevLon, out)
            prevLat = lat
            prevLon = lon
        }
        return out.toString()
    }

    /**
     * Decodes a polyline encoded with [precision] decimal places.
     *
     * @throws IllegalArgumentException when [encoded] is truncated or contains characters outside the format.
     */
    public fun decode(encoded: String, precision: Int = DEFAULT_PRECISION): List<LatLng> {
        val factor = factor(precision)
        val result = ArrayList<LatLng>(encoded.length / 4)
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            val dLat = decodeValue(encoded, index)
            index = dLat.second
            require(index < encoded.length) { "truncated polyline: latitude without longitude" }
            val dLon = decodeValue(encoded, index)
            index = dLon.second
            lat += dLat.first
            lon += dLon.first
            result += LatLng(lat / factor, lon / factor)
        }
        return result
    }

    private fun factor(precision: Int): Double {
        require(precision in 1..7) { "precision must be within 1..7, was $precision" }
        var f = 1.0
        repeat(precision) { f *= 10.0 }
        return f
    }

    private fun round(v: Double): Long = floor(v + 0.5).toLong()

    private fun encodeValue(value: Long, out: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            out.append(((0x20L or (v and 0x1f)) + 63).toInt().toChar())
            v = v shr 5
        }
        out.append((v + 63).toInt().toChar())
    }

    /** Returns the decoded signed value and the index just after it. */
    private fun decodeValue(s: String, start: Int): Pair<Long, Int> {
        var index = start
        var shift = 0
        var result = 0L
        while (true) {
            require(index < s.length) { "truncated polyline at index $index" }
            val b = s[index++].code - 63
            require(b in 0..63) { "invalid polyline character '${s[index - 1]}' at index ${index - 1}" }
            require(shift <= 60) { "polyline value too long at index $start" }
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        val value = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return value to index
    }
}
