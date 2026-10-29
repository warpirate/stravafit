// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.fileimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.core.fileimport.ImportFormat
import org.openactivity.core.fileimport.ImportedActivity
import org.openactivity.core.fileimport.ImportedPoint
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.AltitudeSource
import java.time.Instant

class ImportedActivityConverterTest {
    private val start = Instant.parse("2025-05-04T07:00:00Z")

    /** 1 Hz samples at [speed] m/s heading east, with a 60 s stop in the middle. */
    private fun run(seconds: Int = 1800, speed: Double = 3.0, withTime: Boolean = true, deviceDistance: Boolean = false): ImportedActivity {
        var p = LatLng(47.0, 8.0)
        var d = 0.0
        val points = (0..seconds).map { s ->
            val stopped = s in 900..959
            if (s > 0 && !stopped) {
                p = Spherical.destination(p, 90.0, speed)
                d += speed
            }
            ImportedPoint(
                latitude = p.latitude, longitude = p.longitude, elevation = 400.0 + s / 60.0,
                time = if (withTime) start.plusSeconds(s.toLong()) else null, heartRate = 150,
                distance = if (deviceDistance) d * 1.02 else null,
            )
        }
        return ImportedActivity(
            sourceName = "run.gpx", format = ImportFormat.GPX, title = null, sport = SportType.RUN, sportDeclared = true,
            startTime = start, points = points, distanceMeters = d, elapsedSeconds = seconds.toLong(), deviceName = null,
            dedupeKey = "k", contentHash = "h",
        )
    }

    @Test
    fun `summary comes from the shared metrics`() {
        val finished = ImportedActivityConverter.convert(run(), UnitSystem.METRIC)
        assertEquals(start.toEpochMilli(), finished.startEpochMillis)
        assertEquals(1800.0, finished.elapsedTime.inWholeMilliseconds / 1000.0, 1e-6)
        // 1740 s moving at 3 m/s.
        assertEquals(5220.0, finished.distanceMeters, 5.0)
        assertTrue(finished.movingTime.inWholeSeconds in 1720..1760)
        assertEquals(3.0, finished.averageSpeedMps, 0.05)
        // Five full kilometres and a partial one of about 220 m.
        assertEquals(6, finished.splits.size)
        assertTrue(finished.splits.last().isPartial)
        assertEquals(150.0, finished.averageHeartRate!!, 1e-9)
        assertEquals(AltitudeSource.GPS, finished.altitudeSource)
        assertTrue(finished.elevationGainMeters in 20.0..31.0)
        assertTrue(finished.bestEfforts.any { it.targetMeters == 5000.0 })
        assertEquals(1801, finished.points.size)
    }

    @Test
    fun `device distance wins over GPS distance`() {
        val finished = ImportedActivityConverter.convert(run(deviceDistance = true), UnitSystem.METRIC)
        assertEquals(5220.0 * 1.02, finished.distanceMeters, 5.0)
    }

    @Test(expected = UnusableActivityException::class)
    fun `files without timestamps are rejected`() {
        ImportedActivityConverter.convert(run(withTime = false), UnitSystem.METRIC)
    }

    @Test
    fun `indoor samples keep the summary but store no track`() {
        val points = (0..600).map { s -> ImportedPoint(null, null, time = start.plusSeconds(s.toLong()), heartRate = 130, distance = s * 5.0) }
        val indoor = run().copy(sport = SportType.VIRTUAL_RIDE, points = points)
        val finished = ImportedActivityConverter.convert(indoor, UnitSystem.METRIC)
        assertEquals(3000.0, finished.distanceMeters, 1e-6)
        assertTrue(finished.points.isEmpty())
        assertEquals(AltitudeSource.NONE, finished.altitudeSource)
        assertNull(finished.averageGradeAdjustedSpeedMps)
    }
}
