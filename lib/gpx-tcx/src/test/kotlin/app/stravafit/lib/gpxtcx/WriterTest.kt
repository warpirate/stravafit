// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WriterTest {
    private val start = Instant.parse("2026-05-10T07:00:00Z")

    /** Deterministic 6-point ride with every sensor channel populated. */
    private val samplePoints: List<TrackPoint> = (0 until 6).map { i ->
        TrackPoint(
            // Integer division yields the double closest to the decimal, matching a parse of the text.
            latitude = (450_000 + i) / 10_000.0,
            longitude = (700_000 + 15 * i) / 100_000.0,
            elevation = (2_500 + 5 * i) / 10.0,
            time = start.plusSeconds(i.toLong()),
            heartRate = 120 + i,
            cadence = 85 + i % 3,
            power = 200 + 5 * i,
            temperature = 18.5,
            speed = (6_000 + 125 * i) / 1_000.0,
            distance = i * 15.25,
        )
    }

    private val sampleGpx = GpxDocument(
        creator = "golden test",
        metadata = GpxMetadata(name = "Golden <ride> & co", time = start),
        tracks = listOf(
            GpxTrack(
                name = "Golden ride",
                type = "cycling",
                segments = listOf(TrackSegment(samplePoints.take(4)), TrackSegment(samplePoints.drop(4))),
            ),
        ),
    )

    @Test
    fun `GPX writer output matches golden (TrackPointExtension v1)`() {
        Fixtures.assertGolden("writer-ride-tpx-v1.gpx", GpxWriter().writeToString(sampleGpx))
    }

    @Test
    fun `GPX writer output matches golden (TrackPointExtension v2)`() {
        val options = GpxWriteOptions(extensionVersion = TrackPointExtensionVersion.V2, coordinateDecimals = 6)
        Fixtures.assertGolden("writer-ride-tpx-v2.gpx", GpxWriter(options).writeToString(sampleGpx))
    }

    @Test
    fun `GPX round trip preserves every written channel`() {
        val text = GpxWriter(GpxWriteOptions(extensionVersion = TrackPointExtensionVersion.V2)).writeToString(sampleGpx)
        val back = GpxReader().read(text.byteInputStream())
        assertEquals("golden test", back.creator)
        assertEquals(sampleGpx.metadata, back.metadata)
        assertEquals(listOf(4, 2), back.tracks.single().segments.map { it.points.size })
        // GPX has no cumulative-distance field in the extensions we write.
        assertEquals(samplePoints.map { it.copy(distance = null) }, back.allTrackPoints)
    }

    @Test
    fun `GPX writer skips position-less points and drops power when disabled`() {
        val doc = GpxDocument(
            tracks = listOf(
                GpxTrack(segments = listOf(TrackSegment(listOf(TrackPoint(null, null, time = start), samplePoints[0])))),
            ),
        )
        val text = GpxWriter(GpxWriteOptions(writePower = false)).writeToString(doc)
        val back = GpxReader().read(text.byteInputStream())
        assertEquals(1, back.allTrackPoints.size)
        assertEquals(null, back.allTrackPoints[0].power)
        assertTrue("creator=\"lib-gpx-tcx\"" in text)
    }

    private val sampleTcx = TcxDocument(
        listOf(
            TcxActivity(
                sport = "running",
                id = start,
                laps = listOf(
                    TcxLap.fromPoints(samplePoints.take(3), triggerMethod = TcxTriggerMethod.DISTANCE)
                        .copy(calories = 12, cadence = 86, averageSpeed = 6.125, averageWatts = 205, maximumWatts = 210),
                    TcxLap.fromPoints(samplePoints.drop(3)),
                ),
                notes = "Golden run",
                creatorName = "golden test",
            ),
        ),
    )

    @Test
    fun `TCX writer output matches golden`() {
        Fixtures.assertGolden("writer-run.tcx", TcxWriter().writeToString(sampleTcx))
    }

    @Test
    fun `TCX round trip preserves laps and trackpoints except temperature`() {
        val back = TcxReader().read(TcxWriter().writeToString(sampleTcx).byteInputStream())
        val activity = back.activities.single()
        assertEquals(TcxSport.RUNNING, activity.sport, "sport is coerced to a schema value")
        val expectedLaps = sampleTcx.activities.single().laps.map { lap ->
            lap.copy(
                calories = lap.calories ?: 0,
                tracks = lap.tracks.map { t -> TrackSegment(t.points.map { it.copy(temperature = null) }) },
            )
        }
        assertEquals(expectedLaps, activity.laps)
        assertEquals("Golden run", activity.notes)
        assertEquals("golden test", activity.creatorName)
    }

    @Test
    fun `TCX biking writes core Cadence and fixture round-trips`() {
        val original = Fixtures.open("tcx/indoor-bike-no-gps.tcx").use { TcxReader().read(it) }
        val text = TcxWriter().writeToString(original)
        assertTrue("<Cadence>88</Cadence>" in text)
        assertEquals(original, TcxReader().read(text.byteInputStream()))
    }

    @Test
    fun `lap summary is derived from points`() {
        val lap = TcxLap.fromPoints(samplePoints)
        assertEquals(5.0, lap.totalTimeSeconds)
        assertEquals(76.25, lap.distanceMeters)
        assertEquals(125, lap.maximumHeartRate)
        assertEquals(123, lap.averageHeartRate) // 122.5 rounds half-up
        assertEquals(6.625, lap.maximumSpeed)
    }

    @Test
    fun `TCX writer requires laps and trackpoint times`() {
        assertFailsWith<IllegalArgumentException> {
            TcxWriter().writeToString(TcxDocument(listOf(TcxActivity(TcxSport.OTHER, start, emptyList()))))
        }
        val lap = TcxLap(start, 1.0, 0.0, tracks = listOf(TrackSegment(listOf(TrackPoint(1.0, 1.0)))))
        assertFailsWith<IllegalArgumentException> {
            TcxWriter().writeToString(TcxDocument(listOf(TcxActivity(TcxSport.OTHER, start, listOf(lap)))))
        }
    }
}
