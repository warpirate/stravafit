// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.fit

import app.stravafit.lib.fit.protocol.FitFileReader
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FitActivityRoundTripTest {
    private val decoder = FitActivityDecoder()

    @Test
    fun `encoded sample matches the golden file byte for byte`() {
        Fixtures.assertGoldenBytes("encoder-sample-run.fit", FitActivityEncoder().encode(SampleActivity.activity))
    }

    @Test
    fun `round trip reproduces the activity exactly`() {
        val bytes = FitActivityEncoder().encode(SampleActivity.activity)
        assertEquals(SampleActivity.activity, decoder.decode(bytes))
    }

    @Test
    fun `compressed timestamps decode identically and shrink the file`() {
        val plain = FitActivityEncoder().encode(SampleActivity.activity)
        val compressed = FitActivityEncoder(FitEncodeOptions(compressTimestamps = true)).encode(SampleActivity.activity)
        assertEquals(SampleActivity.activity, decoder.decode(compressed))
        // 4-byte timestamp fields replaced by header bits for every record after the first.
        assertTrue(compressed.size < plain.size - 4 * (SampleActivity.RECORD_COUNT - 2), "${compressed.size} vs ${plain.size}")
        Fixtures.assertGoldenBytes("encoder-sample-run-compressed.fit", compressed)
    }

    @Test
    fun `compressed encoding falls back to full timestamps across gaps of 32 s or more`() {
        val start = SampleActivity.START
        val records = listOf(0L, 1L, 40L, 41L, 100L).map { FitRecord(start.plusSeconds(it), heartRate = 100) }
        val activity = FitActivity(FitFileId(timeCreated = start), records)
        val decoded = decoder.decode(FitActivityEncoder(FitEncodeOptions(compressTimestamps = true)).encode(activity))
        assertEquals(records, decoded.records)
    }

    @Test
    fun `values that do not fit their field are written as invalid`() {
        val record = FitRecord(
            timestamp = SampleActivity.START,
            heartRate = 300,
            cadence = -1,
            power = 70_000,
            altitude = 20_000.0, // beyond uint16 range, still fits enhanced_altitude
            speed = 70.0, // beyond uint16 range, still fits enhanced_speed
            temperature = 200,
        )
        val decoded = decoder.decode(FitActivityEncoder().encode(FitActivity(FitFileId(), listOf(record)))).records.single()
        assertNull(decoded.heartRate)
        assertNull(decoded.cadence)
        assertNull(decoded.power)
        assertNull(decoded.temperature)
        assertEquals(20_000.0, decoded.altitude)
        assertEquals(70.0, decoded.speed)
        val raw = FitFileReader().read(FitActivityEncoder().encode(FitActivity(FitFileId(), listOf(record))))
        val message = raw.messages.single { it.globalNumber == FitMessageNumber.RECORD }
        assertNull(message.fields[RecordField.ALTITUDE], "16-bit altitude is invalid")
        assertNull(message.fields[RecordField.SPEED], "16-bit speed is invalid")
    }

    @Test
    fun `channels absent from every record are not defined`() {
        val records = listOf(FitRecord(SampleActivity.START, heartRate = 90))
        val raw = FitFileReader().read(FitActivityEncoder().encode(FitActivity(FitFileId(), records)))
        val record = raw.messages.single { it.globalNumber == FitMessageNumber.RECORD }
        assertEquals(setOf(FitCommonField.TIMESTAMP, RecordField.HEART_RATE), record.fields.keys)
    }

    @Test
    fun `events are interleaved with records chronologically`() {
        val t = SampleActivity.START
        val activity = FitActivity(
            FitFileId(),
            records = listOf(FitRecord(t, heartRate = 1), FitRecord(t.plusSeconds(10), heartRate = 2)),
            events = listOf(
                FitEvent(t.plusSeconds(10), FitEventCode.TIMER, FitEventType.STOP_ALL),
                FitEvent(t, FitEventCode.TIMER, FitEventType.START),
                FitEvent(t.plusSeconds(5), FitEventCode.LAP, FitEventType.MARKER),
            ),
        )
        val order = FitFileReader().read(FitActivityEncoder().encode(activity)).messages.drop(1).map {
            if (it.globalNumber == FitMessageNumber.RECORD) "r${it.int(RecordField.HEART_RATE)}" else "e${it.int(EventField.EVENT_TYPE)}"
        }
        assertEquals(listOf("e0", "r1", "e3", "r2", "e4"), order)
    }

    @Test
    fun `unit conversions`() {
        assertEquals(Instant.parse("1989-12-31T00:00:00Z"), FitUnits.fromFitTime(0))
        assertEquals(1_000_000_000L, FitUnits.toFitTime(FitUnits.fromFitTime(1_000_000_000L)))
        assertEquals(1 shl 30, FitUnits.toSemicircles(90.0))
        assertEquals(-(1 shl 30), FitUnits.toSemicircles(-90.0))
        assertEquals(Int.MAX_VALUE - 1, FitUnits.toSemicircles(180.0), "clamped below the invalid sentinel")
        assertEquals(45.0, FitUnits.fromSemicircles(536_870_912L))
    }
}
