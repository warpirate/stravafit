// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.fileimport

import app.stravafit.lib.fit.FitActivity
import app.stravafit.lib.fit.FitActivityEncoder
import app.stravafit.lib.fit.FitFileId
import app.stravafit.lib.fit.FitRecord
import app.stravafit.lib.fit.FitSession
import app.stravafit.lib.fit.FitSport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.model.SportType
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.zip.GZIPOutputStream

/** Parses the shared `testdata/files` fixtures and generated files. */
class ActivityFileParserTest {
    private val parser = ActivityFileParser()
    private val root = File(requireNotNull(System.getProperty("testdata.dir")) { "testdata.dir not set" })

    private fun parse(path: String) = File(root, path).inputStream().use { parser.parse(File(path).name, it) }

    private fun parseBytes(name: String, bytes: ByteArray) = parser.parse(name, bytes.inputStream())

    private fun failure(name: String, bytes: ByteArray): ImportErrorKind =
        assertThrows(ImportException::class.java) { parseBytes(name, bytes) }.error.kind

    @Test
    fun `reads a garmin-style gpx`() {
        val activity = parse("gpx/garmin-edge-tpx-v1.gpx").single()
        assertEquals(ImportFormat.GPX, activity.format)
        assertEquals("Morning Ride", activity.title)
        assertEquals(SportType.RIDE, activity.sport)
        assertTrue(activity.sportDeclared)
        assertEquals(Instant.parse("2026-05-01T06:00:01Z"), activity.startTime)
        assertEquals(131, activity.points.first().heartRate)
        assertTrue(activity.distanceMeters > 0)
        assertTrue(activity.dedupeKey.startsWith("v1:"))
    }

    @Test
    fun `reads an android-style gpx with namespace prefixes and cdata`() {
        val activity = parse("gpx/android-tracker-cdata-comments.gpx").single()
        assertEquals("Lunch run", activity.title)
        assertEquals(SportType.RUN, activity.sport)
        assertEquals(3, activity.points.size)
    }

    @Test
    fun `reads a multi-lap tcx and an indoor tcx`() {
        val run = parse("tcx/forerunner-run-2laps.tcx").single()
        assertEquals(ImportFormat.TCX, run.format)
        assertEquals(SportType.RUN, run.sport)
        assertTrue(run.hasRoute)

        val indoor = parse("tcx/indoor-bike-no-gps.tcx").single()
        assertEquals(SportType.RIDE, indoor.sport)
        assertFalse(indoor.hasRoute)
        assertTrue(indoor.dedupeKey.startsWith("v1:"))
    }

    @Test
    fun `reads a handcrafted fit file`() {
        val activity = parse("fit/handcrafted-mixed.fit").single()
        assertEquals(ImportFormat.FIT, activity.format)
        assertTrue(activity.points.isNotEmpty())
    }

    @Test
    fun `recovers the readable part of a truncated gpx`() {
        val activity = parse("gpx/malformed-truncated.gpx").single()
        assertTrue(activity.truncated)
        assertEquals(3, activity.points.size)
    }

    @Test
    fun `decompresses gzip files`() {
        val plain = File(root, "gpx/garmin-edge-tpx-v1.gpx").readBytes()
        val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(plain) } }.toByteArray()
        val fromGz = parseBytes("ride.gpx.gz", gz).single()
        val fromPlain = parseBytes("ride.gpx", plain).single()
        assertEquals(fromPlain.points, fromGz.points)
        assertEquals(fromPlain.contentHash, fromGz.contentHash)
    }

    @Test
    fun `detects the format by content, not by extension`() {
        val tcx = File(root, "tcx/forerunner-run-2laps.tcx").readBytes()
        assertEquals(ImportFormat.TCX, parseBytes("download.bin", tcx).single().format)
    }

    @Test
    fun `reports unsupported, malformed, empty and xxe files per kind`() {
        assertEquals(ImportErrorKind.UNSUPPORTED_FORMAT, failure("map.kml", File(root, "gpx/not-gpx.xml").readBytes()))
        assertEquals(ImportErrorKind.UNSUPPORTED_FORMAT, failure("notes.txt", "hello".toByteArray()))
        assertEquals(ImportErrorKind.MALFORMED, failure("broken.gpx", "this is not xml".toByteArray()))
        assertEquals(ImportErrorKind.MALFORMED, failure("xxe.gpx", File(root, "gpx/malformed-xxe.gpx").readBytes()))
        assertEquals(ImportErrorKind.EMPTY, failure("empty.gpx", ByteArray(0)))
        assertEquals(ImportErrorKind.EMPTY, failure("blank.gpx", "<gpx version=\"1.1\"><trk><trkseg/></trk></gpx>".toByteArray()))
        val badFit = File(root, "fit/handcrafted-mixed.fit").readBytes().copyOf(40)
        assertEquals(ImportErrorKind.MALFORMED, failure("cut.fit", badFit))
    }

    @Test
    fun `the same recording as fit and gpx shares a dedupe key`() {
        val start = Instant.parse("2026-06-01T06:30:00Z")
        val records = List(30) { i ->
            FitRecord(timestamp = start.plusSeconds(i.toLong()), latitude = 47.37 + i * 1e-4, longitude = 8.54, altitude = 400.0)
        }
        val fitBytes = FitActivityEncoder().encode(
            FitActivity(
                fileId = FitFileId(timeCreated = start),
                records = records,
                sessions = listOf(
                    FitSession(
                        timestamp = start.plusSeconds(29), startTime = start, sport = FitSport.RUNNING,
                        totalElapsedTime = 29.0, totalTimerTime = 29.0, totalDistance = 320.0,
                    ),
                ),
            ),
        )
        val gpx = buildString {
            append("<gpx version=\"1.1\" creator=\"x\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><type>running</type><trkseg>")
            records.forEach { append("<trkpt lat=\"${it.latitude}\" lon=\"${it.longitude}\"><time>${it.timestamp}</time></trkpt>") }
            append("</trkseg></trk></gpx>")
        }
        val fromFit = parseBytes("watch.fit", fitBytes).single()
        val fromGpx = parseBytes("export.gpx", gpx.toByteArray()).single()
        assertEquals(SportType.RUN, fromFit.sport)
        assertEquals(320.0, fromFit.distanceMeters, 0.01)
        assertEquals(fromFit.dedupeKey, fromGpx.dedupeKey)
        assertNotEquals(fromFit.contentHash, fromGpx.contentHash)
    }

    @Test
    fun `a watch file with samples before the gps fix matches its gpx export`() {
        val start = Instant.parse("2026-06-02T06:30:00Z")
        // The watch logs 5 s without a fix (and declares the session start then); GPX keeps only fixes.
        val records = List(30) { i ->
            val fixed = i >= 5
            FitRecord(
                timestamp = start.plusSeconds(i.toLong()),
                latitude = if (fixed) 47.37 + i * 1e-4 else null,
                longitude = if (fixed) 8.54 else null,
                heartRate = 120,
            )
        }
        val fitBytes = FitActivityEncoder().encode(
            FitActivity(
                fileId = FitFileId(timeCreated = start),
                records = records,
                sessions = listOf(
                    FitSession(
                        timestamp = start.plusSeconds(29), startTime = start, sport = FitSport.RUNNING,
                        totalElapsedTime = 29.0, totalTimerTime = 29.0, totalDistance = 280.0,
                    ),
                ),
            ),
        )
        val gpx = buildString {
            append("<gpx version=\"1.1\" creator=\"x\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg>")
            records.filter { it.latitude != null }.forEach {
                append("<trkpt lat=\"${it.latitude}\" lon=\"${it.longitude}\"><time>${it.timestamp}</time></trkpt>")
            }
            append("</trkseg></trk></gpx>")
        }
        val fromFit = parseBytes("watch.fit", fitBytes).single()
        val fromGpx = parseBytes("export.gpx", gpx.toByteArray()).single()
        assertEquals(start, fromFit.startTime)
        assertEquals(fromFit.dedupeKey, fromGpx.dedupeKey)
    }

    @Test
    fun `time-less routes fall back to a content key`() {
        val activity = parse("gpx/gpx10-route-no-times.gpx").first()
        assertNull(activity.startTime)
        assertTrue(activity.dedupeKey.startsWith("content:"))
    }
}
