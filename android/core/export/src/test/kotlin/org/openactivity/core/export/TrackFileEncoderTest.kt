// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export

import app.stravafit.lib.fit.FitActivityDecoder
import app.stravafit.lib.fit.FitSport
import app.stravafit.lib.geojson.Feature
import app.stravafit.lib.geojson.GeoJsonReader
import app.stravafit.lib.geojson.LineString
import app.stravafit.lib.geojson.MultiLineString
import app.stravafit.lib.gpxtcx.GpxReader
import app.stravafit.lib.gpxtcx.TcxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.core.export.file.FormatUnavailableReason
import org.openactivity.core.export.file.TrackFileEncoder
import org.openactivity.core.export.model.privacyTrimmed

/** Writes every data format and reads it back with the project's own parsers. */
class TrackFileEncoderTest {
    private val encoder = TrackFileEncoder()
    private val run = TestActivities.lakeRun()

    @Test
    fun `gpx round-trips positions, times and heart rate`() {
        val doc = GpxReader().read(encoder.encode(run, ExportFormat.GPX).inputStream())
        assertEquals("Lake loop & café", doc.metadata.name)
        assertEquals("running", doc.tracks.single().type)
        val points = doc.allTrackPoints
        assertEquals(run.points.size, points.size)
        assertEquals(run.points[10].latitude!!, points[10].latitude!!, 1e-7)
        assertEquals(run.points[10].time, points[10].time)
        assertEquals(run.points[10].heartRate, points[10].heartRate)
    }

    @Test
    fun `trimmed gpx contains no position near the start or end`() {
        val trimmed = run.privacyTrimmed().activity
        val points = GpxReader().read(encoder.encode(trimmed, ExportFormat.GPX).inputStream()).allTrackPoints
        assertEquals(trimmed.positionedPoints.size, points.size)
        assertTrue(points.size < run.points.size)
    }

    @Test
    fun `tcx round-trips one lap with all samples`() {
        val doc = TcxReader().read(encoder.encode(run, ExportFormat.TCX).inputStream())
        val activity = doc.activities.single()
        assertEquals("Running", activity.sport)
        assertEquals(TestActivities.START, activity.id)
        val lap = activity.laps.single()
        assertEquals(4980.0, lap.distanceMeters, 0.01)
        assertEquals(run.points.size, lap.trackpoints.size)
        assertEquals(run.points.last().heartRate, lap.trackpoints.last().heartRate)
    }

    @Test
    fun `fit round-trips records and session summary`() {
        val fit = FitActivityDecoder().decode(encoder.encode(run, ExportFormat.FIT))
        assertEquals(run.points.size, fit.records.size)
        val session = fit.sessions.single()
        assertEquals(FitSport.RUNNING, session.sport)
        assertEquals(4980.0, session.totalDistance!!, 0.01)
        assertEquals(1198.0, session.totalTimerTime, 0.001)
        assertEquals(run.points[5].latitude!!, fit.records[5].latitude!!, 1e-6)
    }

    @Test
    fun `geojson writes a line string, or a multi line string once trimmed in the middle`() {
        val feature = GeoJsonReader().read(encoder.encode(run, ExportFormat.GEOJSON).toString(Charsets.UTF_8)) as Feature
        assertTrue(feature.geometry is LineString)
        assertEquals("Run", feature.properties!!["sport"])

        val gap = run.copy(points = run.points.mapIndexed { i, p -> if (i in 100..110) p.copy(latitude = null, longitude = null) else p })
        val split = GeoJsonReader().read(encoder.encode(gap, ExportFormat.GEOJSON).toString(Charsets.UTF_8)) as Feature
        assertEquals(2, (split.geometry as MultiLineString).coordinates.size)
    }

    @Test
    fun `indoor activities support time-based formats only`() {
        val ride = TestActivities.indoorRide()
        assertEquals(FormatUnavailableReason.NEEDS_ROUTE, encoder.unavailableReason(ride, ExportFormat.GPX))
        assertEquals(FormatUnavailableReason.NEEDS_ROUTE, encoder.unavailableReason(ride, ExportFormat.GEOJSON))
        assertNull(encoder.unavailableReason(ride, ExportFormat.TCX))
        assertNull(encoder.unavailableReason(ride, ExportFormat.FIT))
        val fit = FitActivityDecoder().decode(encoder.encode(ride, ExportFormat.FIT))
        assertEquals(FitSport.CYCLING, fit.sessions.single().sport)
        assertEquals(200, fit.records.first().power)
    }
}
