// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TcxReaderTest {
    private fun read(path: String) = Fixtures.open(path).use { TcxReader().read(it) }

    @Test
    fun `reads laps, trackpoints and ActivityExtension values despite leading whitespace`() {
        val activity = read("tcx/forerunner-run-2laps.tcx").activities.single()
        assertEquals(TcxSport.RUNNING, activity.sport)
        assertEquals(Instant.parse("2026-05-08T06:30:00Z"), activity.id)
        assertEquals("Tempo session", activity.notes)
        assertEquals("Handcrafted Forerunner-style watch", activity.creatorName)
        assertEquals(2, activity.laps.size)

        val lap1 = activity.laps[0]
        assertEquals(300.0, lap1.totalTimeSeconds)
        assertEquals(1000.0, lap1.distanceMeters)
        assertEquals(3.9, lap1.maximumSpeed)
        assertEquals(70, lap1.calories)
        assertEquals(142, lap1.averageHeartRate)
        assertEquals(155, lap1.maximumHeartRate)
        assertEquals(TcxTriggerMethod.DISTANCE, lap1.triggerMethod)
        assertEquals(3.333, lap1.averageSpeed)
        assertEquals(86, lap1.cadence, "AvgRunCadence")
        assertEquals(270, lap1.averageWatts)
        assertEquals(
            TrackPoint(
                latitude = 37.7839, longitude = -122.4194, elevation = 18.4,
                time = Instant.parse("2026-05-08T06:35:00Z"),
                heartRate = 150, cadence = 88, power = 280, speed = 3.4, distance = 1000.0,
            ),
            lap1.trackpoints[1],
        )

        val lap2 = activity.laps[1]
        assertEquals(TcxIntensity.RESTING, lap2.intensity)
        assertEquals(310.5, lap2.totalTimeSeconds)
        assertEquals(Instant.parse("2026-05-08T06:40:10.500Z"), lap2.trackpoints.single().time)
        assertNull(lap2.trackpoints.single().elevation)
    }

    @Test
    fun `indoor activity keeps position-less trackpoints and derives missing lap totals`() {
        val activity = read("tcx/indoor-bike-no-gps.tcx").activities.single()
        assertEquals(TcxSport.BIKING, activity.sport)
        val lap = activity.laps.single()
        assertEquals(activity.id, lap.startTime, "missing StartTime falls back")
        assertEquals(60.0, lap.totalTimeSeconds)
        assertEquals(495.5, lap.distanceMeters)
        assertEquals(90, lap.cadence)
        val points = lap.trackpoints
        assertFalse(points.any { it.hasPosition })
        assertEquals(listOf(180, 195), points.map { it.power })
        assertEquals(listOf(88, 92), points.map { it.cadence })
        assertEquals(listOf(8.1, 8.4), points.map { it.speed })
    }

    @Test
    fun `derived lap distances use the activity-cumulative trackpoint distances`() {
        fun lap(start: String, vararg points: Pair<String, Double>) =
            "<Lap StartTime=\"$start\"><Track>" +
                points.joinToString("") { (t, d) -> "<Trackpoint><Time>$t</Time><DistanceMeters>$d</DistanceMeters></Trackpoint>" } +
                "</Track></Lap>"
        val xml = "<TrainingCenterDatabase><Activities><Activity Sport=\"Running\"><Id>2026-05-08T06:30:00Z</Id>" +
            lap("2026-05-08T06:30:00Z", "2026-05-08T06:30:00Z" to 0.0, "2026-05-08T06:35:00Z" to 1000.0) +
            lap("2026-05-08T06:35:00Z", "2026-05-08T06:35:05Z" to 1012.0, "2026-05-08T06:40:00Z" to 1950.0) +
            lap("2026-05-08T06:40:00Z") +
            lap("2026-05-08T06:40:00Z", "2026-05-08T06:44:00Z" to 2750.0) +
            "</Activity></Activities></TrainingCenterDatabase>"
        val laps = TcxReader().read(xml.byteInputStream()).activities.single().laps
        assertEquals(listOf(1000.0, 950.0, 0.0, 800.0), laps.map { it.distanceMeters })
        assertEquals(listOf(300.0, 300.0, 0.0, 240.0), laps.map { it.totalTimeSeconds })
    }

    @Test
    fun `rejects non-TCX and malformed input`() {
        assertFailsWith<TrackFileFormatException> { read("gpx/garmin-edge-tpx-v1.gpx") }
        assertFailsWith<TrackFileFormatException> { read("gpx/malformed-xxe.gpx") }
        assertFailsWith<TrackFileFormatException> {
            TcxReader().read("<TrainingCenterDatabase><Activities><Activity>".byteInputStream())
        }
    }

    @Test
    fun `activity without id or lap times is an error`() {
        val xml = "<TrainingCenterDatabase><Activities><Activity Sport=\"Other\"><Lap/></Activity></Activities></TrainingCenterDatabase>"
        assertFailsWith<TrackFileFormatException> { TcxReader().read(xml.byteInputStream()) }
    }
}
