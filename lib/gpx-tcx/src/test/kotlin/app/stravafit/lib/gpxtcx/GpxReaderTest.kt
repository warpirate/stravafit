// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GpxReaderTest {
    private fun read(path: String, recover: Boolean = false) =
        Fixtures.open(path).use { GpxReader(recoverPartial = recover).read(it) }

    @Test
    fun `reads Garmin TrackPointExtension v1 with power and multiple segments`() {
        val doc = read("gpx/garmin-edge-tpx-v1.gpx")
        assertEquals("Handcrafted Edge-style recorder", doc.creator)
        assertEquals(Instant.parse("2026-05-01T06:00:00Z"), doc.metadata.time)
        val track = doc.tracks.single()
        assertEquals("Morning Ride", track.name)
        assertEquals("cycling", track.type)
        assertEquals(listOf(2, 1), track.segments.map { it.points.size })
        assertEquals(
            TrackPoint(
                latitude = 52.52, longitude = 13.405, elevation = 34.2,
                time = Instant.parse("2026-05-01T06:00:01Z"),
                heartRate = 131, cadence = 84, power = 210, temperature = 14.0,
            ),
            track.segments[0].points[0],
        )
        assertNull(track.segments[1].points[0].power)
        assertTrue(doc.routes.isEmpty(), "waypoints are not routes")
        assertFalse(doc.truncated)
    }

    @Test
    fun `reads TrackPointExtension v2 speed, PowerExtension and offset timestamps`() {
        val doc = read("gpx/watch-tpx-v2-powerext.gpx")
        assertEquals("Two loops & a hill", doc.metadata.description)
        val (first, second) = doc.allTrackPoints
        assertEquals(141, first.heartRate)
        assertEquals(3.215, first.speed)
        assertEquals(305, first.power)
        assertEquals(22.0, first.temperature)
        assertEquals(Instant.parse("2026-05-02T12:00:00.500Z"), first.time)
        assertEquals(Instant.parse("2026-05-02T12:00:01.500Z"), second.time, "+10:00 offset normalised to UTC")
        assertEquals(310, second.power)
    }

    @Test
    fun `reads Cluetrust gpxdata extensions`() {
        val points = read("gpx/cluetrust-gpxdata.gpx").allTrackPoints
        assertEquals(listOf(99, 104), points.map { it.heartRate })
        assertEquals(listOf(70, 72), points.map { it.cadence })
        assertEquals(listOf(18.5, 18.5), points.map { it.temperature })
        assertEquals(listOf(0.0, 27.4), points.map { it.distance })
    }

    @Test
    fun `reads generic un-namespaced extensions and zone-less times as UTC`() {
        val doc = read("gpx/phone-app-generic-ext.gpx")
        assertEquals("Handcrafted phone tracker", doc.creator)
        val (first, second) = doc.allTrackPoints
        assertEquals(88, first.heartRate)
        assertEquals(52, first.cadence)
        assertEquals(0, first.power)
        assertEquals(Instant.parse("2026-05-04T18:00:00Z"), first.time)
        assertEquals(Instant.parse("2026-05-04T18:00:03.123456Z"), second.time)
        assertNull(second.elevation)
    }

    @Test
    fun `reads GPX 1_0 routes and time-less tracks`() {
        val doc = read("gpx/gpx10-route-no-times.gpx")
        val route = doc.routes.single()
        assertEquals("Lakeside loop", route.name)
        assertEquals(3, route.points.size)
        assertEquals(listOf(408.0, 406.0, null), route.points.map { it.elevation })
        val trackPoints = doc.allTrackPoints
        assertTrue(trackPoints.all { it.time == null })
        assertEquals(4.2, trackPoints[0].speed)
    }

    @Test
    fun `tolerates BOM, leading whitespace and garbage values`() {
        val points = read("gpx/messy-values-bom.gpx").allTrackPoints
        // Out-of-range latitude and missing latitude are dropped; garbage numbers/times become null.
        assertEquals(2, points.size)
        assertEquals(TrackPoint(latitude = 51.5007, longitude = -0.1246), points[0])
        assertEquals(21.5, points[1].elevation)
        assertNull(points[1].heartRate, "NaN heart rate is rejected")
        assertEquals(80, points[1].cadence)
    }

    @Test
    fun `truncated file fails strictly but can be partially recovered`() {
        val error = assertFailsWith<TrackFileFormatException> { read("gpx/malformed-truncated.gpx") }
        assertTrue(error.line > 0, "error carries a location: ${error.message}")

        val doc = read("gpx/malformed-truncated.gpx", recover = true)
        assertTrue(doc.truncated)
        assertEquals("Crashed recording", doc.tracks.single().name)
        assertEquals(
            listOf(40.0, 40.5, 41.0),
            doc.allTrackPoints.map { it.elevation },
        )
    }

    @Test
    fun `rejects DTDs and external entities`() {
        assertFailsWith<TrackFileFormatException> { read("gpx/malformed-xxe.gpx") }
        assertFailsWith<TrackFileFormatException> { read("gpx/malformed-xxe.gpx", recover = true) }
    }

    @Test
    fun `rejects documents that are not GPX`() {
        val error = assertFailsWith<TrackFileFormatException> { read("gpx/not-gpx.xml") }
        assertTrue("kml" in error.message!!)
        assertFailsWith<TrackFileFormatException> { GpxReader().read("".byteInputStream()) }
    }
}
