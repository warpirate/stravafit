// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.fileimport

import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import java.time.Instant
import java.util.Locale

/** Generated GPX documents for import tests. */
object Gpx {
    const val SAMPLES: Int = 1201
    private val START: Instant = Instant.parse("2025-05-04T07:00:00Z")

    /** A 20-minute run at 3 m/s heading east, typed "running", at 1 Hz. */
    fun run(): String {
        var p = LatLng(46.95, 7.44)
        val points = (0 until SAMPLES).map { s ->
            if (s > 0) p = Spherical.destination(p, 90.0, 3.0)
            Triple(p.latitude, p.longitude, START.plusSeconds(s.toLong()).toEpochMilli())
        }
        return fromPoints(points, type = "running", elevation = true)
    }

    /** A GPX track of the given (latitude, longitude, epoch millis) samples. */
    fun fromPoints(points: List<Triple<Double, Double, Long>>, type: String = "running", elevation: Boolean = false): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1"><trk><type>$type</type><trkseg>""")
        points.forEachIndexed { i, (lat, lon, t) ->
            append(String.format(Locale.ROOT, """<trkpt lat="%.7f" lon="%.7f">""", lat, lon))
            if (elevation) append(String.format(Locale.ROOT, "<ele>%.1f</ele>", 500.0 + i * 0.02))
            append("<time>${Instant.ofEpochMilli(t)}</time></trkpt>")
        }
        append("</trkseg></trk></gpx>")
    }

    /** A planned route: positions without times. */
    fun route(): String = buildString {
        append("""<?xml version="1.0"?><gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1"><trk><trkseg>""")
        repeat(20) { append(String.format(Locale.ROOT, """<trkpt lat="%.5f" lon="7.44"></trkpt>""", 46.95 + it * 0.001)) }
        append("</trkseg></trk></gpx>")
    }
}
