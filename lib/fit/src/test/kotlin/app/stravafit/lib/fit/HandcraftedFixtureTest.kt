// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.fit

import app.stravafit.lib.fit.protocol.FitFileReader
import app.stravafit.lib.fit.protocol.FitValue
import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Decodes `testdata/files/fit/handcrafted-mixed.fit`, assembled byte by byte by an independent
 * Python script (big-endian definitions, developer fields, compressed timestamps with roll-over,
 * odd field sizes, an unknown message).
 */
class HandcraftedFixtureTest {
    private val bytes = Fixtures.bytes("fit/handcrafted-mixed.fit")
    private val t0 = Instant.parse("2026-05-11T06:00:30Z")

    @Test
    fun `generic reader exposes every message including unknown ones and developer fields`() {
        val file = FitFileReader().read(bytes)
        assertEquals(0x20, file.headers.single().protocolVersion)
        assertEquals(
            listOf(0, 207, 206, 21, 20, 20, 20, 20, 0xFF00, 21, 19, 18, 34),
            file.messages.map { it.globalNumber },
        )
        val bigEndianRecord = file.messages[4]
        assertEquals(listOf<Byte>(0, 7), bigEndianRecord.developerFields.single().bytes)
        val compressed = file.messages[5]
        assertEquals(42L, compressed.long(250))
        assertEquals(FitValue.Bytes(byteArrayOf(1, 2, 3)), compressed.fields[251])
        assertEquals("doughnuts", file.messages[2].text(3))
        assertEquals(0xBEEFL, file.messages[8].long(0))
    }

    @Test
    fun `activity decoder maps records, laps, session and activity`() {
        val activity = FitActivityDecoder().decode(bytes)
        assertEquals(FitManufacturer.GARMIN, activity.fileId.manufacturer)
        assertEquals(3121, activity.fileId.product)
        assertEquals(123_456_789L, activity.fileId.serialNumber)
        assertEquals("Hand Watch", activity.fileId.productName)
        assertEquals(t0, activity.fileId.timeCreated)

        val records = activity.records
        assertEquals(listOf(0L, 1L, 2L, 5L), records.map { it.timestamp.epochSecond - t0.epochSecond }, "roll-over resolved")
        assertEquals(46.5, records[0].latitude!!, 1e-7)
        assertEquals(6.6, records[0].longitude!!, 1e-7)
        assertEquals(372.0, records[0].altitude)
        assertEquals(2.5, records[0].speed)
        assertEquals(180, records[0].power)
        assertEquals(46.50002, records[2].latitude!!, 1e-7)
        assertEquals(373.4, records[2].altitude!!, 1e-9)
        assertNull(records[2].heartRate, "0xFF sentinel")
        assertEquals(listOf(null, 2.5, 5.2, 12.75), records.map { it.distance })
        assertEquals(2.75, records[3].speed)

        assertEquals(listOf(FitEventType.START, FitEventType.STOP_ALL), activity.events.map { it.eventType })
        val lap = activity.laps.single()
        assertEquals(t0, lap.startTime)
        assertEquals(5.0, lap.totalElapsedTime)
        assertEquals(FitLapTrigger.SESSION_END, lap.lapTrigger)
        val session = activity.sessions.single()
        assertEquals(FitSport.RUNNING, session.sport)
        assertEquals(FitSubSport.TRAIL, session.subSport)
        assertEquals(12.75, session.totalDistance)
        val summary = activity.activity!!
        assertEquals(LocalDateTime.parse("2026-05-11T08:00:35"), summary.localTimestamp)
        assertEquals(5.0, summary.totalTimerTime)
    }
}
