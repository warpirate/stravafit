// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.core.export.model.ExportActivity
import org.openactivity.core.export.model.ExportPoint
import org.openactivity.core.export.model.ExportPrivacy
import org.openactivity.core.export.model.ExportPrivacyZone
import org.openactivity.core.export.model.ExportStats
import org.openactivity.core.export.model.privacyTrimmed
import org.openactivity.geo.PrivacyZone
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import java.time.Instant

/**
 * Privacy zones in the share/export path: positions inside a zone at either end of the route are removed
 * from anything shared, the cut lands on the first point outside the zone, and a route that would show
 * nothing useful is hidden altogether. Sensor data is never removed.
 */
class PrivacyZoneTrimTest {
    private val origin = LatLng(18.5204, 73.8567)

    /** A straight track east from [from], one sample every [step] metres over [lengthMeters]. */
    private fun line(from: LatLng = origin, lengthMeters: Double = 5_000.0, step: Double = 10.0, bearing: Double = 90.0): List<LatLng> =
        (0..(lengthMeters / step).toInt()).map { Spherical.destination(from, bearing, it * step) }

    private fun activity(track: List<LatLng>, privacy: ExportPrivacy) = ExportActivity(
        id = "a",
        title = "Run",
        sport = SportType.RUN,
        startTime = Instant.EPOCH,
        points = track.mapIndexed { i, p -> ExportPoint(p.latitude, p.longitude, heartRate = 140, time = Instant.EPOCH.plusSeconds(i.toLong())) },
        stats = ExportStats(distanceMeters = Spherical.pathLength(track), movingTimeSeconds = track.size.toLong()),
        privacy = privacy,
    )

    private fun zonesOnly(vararg zones: Pair<LatLng, Double>) =
        ExportPrivacy(hideStartMeters = 0.0, hideEndMeters = 0.0, zones = zones.map { (c, r) -> ExportPrivacyZone(c.latitude, c.longitude, r) })

    private fun ExportPoint.latLng() = LatLng(latitude!!, longitude!!)

    @Test
    fun `a point is in a zone up to its radius`() {
        val zone = PrivacyZone(origin, 400.0)
        assertTrue(zone.contains(origin))
        assertTrue(zone.contains(Spherical.destination(origin, 45.0, 399.0)))
        assertFalse(zone.contains(Spherical.destination(origin, 45.0, 401.0)))
    }

    @Test
    fun `a route leaving a zone is visible from the first point outside it`() {
        val track = line()
        val trimmed = activity(track, zonesOnly(origin to 400.0)).privacyTrimmed()
        val zone = PrivacyZone(origin, 400.0)
        val firstVisible = trimmed.activity.points.indexOfFirst { it.hasPosition }

        assertFalse(trimmed.routeHidden)
        assertFalse(zone.contains(trimmed.activity.points[firstVisible].latLng()))
        assertTrue(zone.contains(track[firstVisible - 1]))
        // Nothing inside the zone is left, and nothing outside it was taken at this end.
        assertTrue(trimmed.activity.points.filter { it.hasPosition }.none { zone.contains(it.latLng()) })
        assertEquals(firstVisible, trimmed.hiddenPoints)
        // Heart rate and time stay on every sample.
        assertTrue(trimmed.activity.points.all { it.heartRate == 140 && it.time != null })
    }

    @Test
    fun `a route entering a zone is visible up to the last point before it`() {
        val track = line()
        val end = track.last()
        val zone = PrivacyZone(end, 800.0)
        val trimmed = activity(track, zonesOnly(end to 800.0)).privacyTrimmed()
        val lastVisible = trimmed.activity.points.indexOfLast { it.hasPosition }

        assertFalse(trimmed.routeHidden)
        assertFalse(zone.contains(trimmed.activity.points[lastVisible].latLng()))
        assertTrue(zone.contains(track[lastVisible + 1]))
        assertTrue(trimmed.activity.points[0].hasPosition)
    }

    @Test
    fun `a loop that starts and ends at home hides both ends`() {
        // Out 3 km and back along the same road.
        val out = line(lengthMeters = 3_000.0)
        val track = out + out.reversed().drop(1)
        val zone = PrivacyZone(origin, 200.0)
        val trimmed = activity(track, zonesOnly(origin to 200.0)).privacyTrimmed()
        val visible = trimmed.activity.points.filter { it.hasPosition }

        assertFalse(trimmed.routeHidden)
        assertFalse(trimmed.activity.points.first().hasPosition)
        assertFalse(trimmed.activity.points.last().hasPosition)
        assertTrue(visible.none { zone.contains(it.latLng()) })
        assertEquals(track.size - visible.size, trimmed.hiddenPoints)
    }

    @Test
    fun `a route wholly inside a zone shows no map at all`() {
        val track = line(lengthMeters = 600.0)
        val trimmed = activity(track, zonesOnly(Spherical.destination(origin, 90.0, 300.0) to 1600.0)).privacyTrimmed()
        assertTrue(trimmed.routeHidden)
        assertTrue(trimmed.activity.points.none { it.hasPosition })
        assertEquals(track.size, trimmed.hiddenPoints)
    }

    @Test
    fun `a zone passed in the middle of a long route is not revealed as an end`() {
        val track = line(lengthMeters = 10_000.0)
        val middle = track[track.size / 2]
        val trimmed = activity(track, zonesOnly(middle to 200.0)).privacyTrimmed()
        // As on Strava, a zone only hides a start or a finish; passing by does not mark it as a place you stop.
        assertEquals(0, trimmed.hiddenPoints)
    }

    @Test
    fun `zones and hide start and end both apply, the larger cut wins`() {
        val track = line()
        val privacy = ExportPrivacy(hideStartMeters = 200.0, hideEndMeters = 200.0, zones = listOf(ExportPrivacyZone(origin.latitude, origin.longitude, 800.0)))
        val trimmed = activity(track, privacy).privacyTrimmed()
        val firstVisible = trimmed.activity.points.indexOfFirst { it.hasPosition }
        val lastVisible = trimmed.activity.points.indexOfLast { it.hasPosition }

        // The 800 m zone hides more than 200 m at the start; at the end only the 200 m applies.
        assertTrue(Spherical.distance(origin, track[firstVisible]) > 800.0)
        assertEquals(200.0, Spherical.pathLength(track.subList(lastVisible, track.size)), 10.0)
    }
}
