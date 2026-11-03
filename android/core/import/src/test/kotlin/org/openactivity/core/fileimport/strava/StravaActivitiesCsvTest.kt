// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.fileimport.strava

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.model.SportType
import java.io.StringReader
import java.time.Instant

class StravaActivitiesCsvTest {

    /** The header of a 2024-2026 export, with Strava's repeated "Elapsed Time", "Distance" and "Max Heart Rate". */
    private val currentHeader = "Activity ID,Activity Date,Activity Name,Activity Type,Activity Description,Elapsed Time," +
        "Distance,Max Heart Rate,Relative Effort,Commute,Activity Private Note,Activity Gear,Filename,Athlete Weight," +
        "Bike Weight,Elapsed Time,Moving Time,Distance,Max Speed,Average Speed,Elevation Gain,Elevation Loss," +
        "Max Heart Rate,Average Heart Rate,Calories,Media"

    private fun parse(text: String) = StravaActivitiesCsv.parse(StringReader(text))

    @Test
    fun `reads a current export row by column name`() {
        val csv = "﻿$currentHeader\r\n" +
            "11223344556,\"Oct 3, 2026, 1:15:00 AM\",Morning Run,Run,\"Easy loop, then\nstrides\",2465,10.21,171,88,false,,Pegasus 41," +
            "activities/11223344556.fit.gz,64.0,,2465.0,2390.0,10213.7,4.6,4.27,58.0,57.2,171.0,152.4,712,media/A1.jpg|media/B2.jpg\r\n"
        val row = parse(csv).single()
        assertEquals("11223344556", row.id)
        assertEquals(Instant.parse("2026-10-03T01:15:00Z"), row.start)
        assertEquals("Morning Run", row.name)
        assertEquals(SportType.RUN, row.sport)
        assertEquals("Easy loop, then\nstrides", row.description)
        assertEquals("activities/11223344556.fit.gz", row.fileName)
        assertEquals(listOf("media/A1.jpg", "media/B2.jpg"), row.media)
        // The second "Distance" is metres; the first is kilometres.
        assertEquals(10_213.7, row.distanceMeters!!, 1e-6)
        assertEquals(2465L, row.elapsedSeconds)
        assertEquals(2390L, row.movingSeconds)
        assertEquals(58.0, row.elevationGainMeters!!, 1e-9)
        assertEquals(152.4, row.averageHeartRate!!, 1e-9)
        assertEquals(171, row.maxHeartRate)
    }

    @Test
    fun `tolerates an old export with fewer columns in another order`() {
        val csv = "Activity ID,Activity Name,Activity Date,Activity Type,Distance,Elapsed Time,Filename\n" +
            "998877,Lunch Ride,2015-06-01 11:30:00,Ride,32.5,4500,activities/998877.gpx\n"
        val row = parse(csv).single()
        assertEquals(Instant.parse("2015-06-01T11:30:00Z"), row.start)
        assertEquals(SportType.RIDE, row.sport)
        assertEquals(32_500.0, row.distanceMeters!!, 1e-6)
        assertEquals(4500L, row.elapsedSeconds)
        assertEquals("", row.description)
        assertTrue(row.media.isEmpty())
        assertNull(row.movingSeconds)
        assertNull(row.averageHeartRate)
    }

    @Test
    fun `a manual entry has no file and keeps its summary`() {
        val csv = "$currentHeader\n" +
            "5550001,\"Jan 9, 2025, 6:00:00 PM\",Gym,Weight Training,,3600,0,,,false,,,,,,3600.0,3600.0,0.0,,,0,,,,,\n"
        val row = parse(csv).single()
        assertNull(row.fileName)
        assertEquals(SportType.WEIGHT_TRAINING, row.sport)
        assertEquals(Instant.parse("2025-01-09T18:00:00Z"), row.start)
        assertEquals(0.0, row.distanceMeters!!, 0.0)
    }

    @Test
    fun `quotes, escaped quotes, blank lines and rows without an id`() {
        val csv = "Activity ID,Activity Name,Activity Type\n" +
            "1,\"Say \"\"hi\"\", then go\",Walk\n" +
            "\n" +
            ",Nameless,Run\n" +
            "2,Short row\n"
        val rows = parse(csv)
        assertEquals(listOf("1", "2"), rows.map { it.id })
        assertEquals("Say \"hi\", then go", rows[0].name)
        assertEquals("", rows[1].typeName)
        assertEquals(SportType.WORKOUT, rows[1].sport)
    }

    @Test
    fun `dates in every format strava has used`() {
        assertEquals(Instant.parse("2021-03-15T07:12:45Z"), StravaActivitiesCsv.parseDate("Mar 15, 2021, 7:12:45 AM"))
        assertEquals(Instant.parse("2021-03-15T19:12:45Z"), StravaActivitiesCsv.parseDate("Mar 15, 2021, 7:12:45 PM"))
        assertEquals(Instant.parse("2021-03-15T00:05:00Z"), StravaActivitiesCsv.parseDate("Mar 15, 2021, 12:05:00 AM"))
        assertEquals(Instant.parse("2014-08-02T05:59:01Z"), StravaActivitiesCsv.parseDate("2014-08-02 05:59:01"))
        assertEquals(Instant.parse("2014-08-02T05:59:01Z"), StravaActivitiesCsv.parseDate("2014-08-02T05:59:01Z"))
        assertNull(StravaActivitiesCsv.parseDate("yesterday"))
    }

    @Test
    fun `an empty file has no rows`() {
        assertTrue(parse("").isEmpty())
        assertTrue(parse(currentHeader).isEmpty())
    }
}
