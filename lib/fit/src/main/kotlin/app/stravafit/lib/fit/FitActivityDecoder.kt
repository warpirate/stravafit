// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.fit

import app.stravafit.lib.fit.FitUnits.fromFitTime
import app.stravafit.lib.fit.protocol.FitFileReader
import app.stravafit.lib.fit.protocol.FitFormatException
import app.stravafit.lib.fit.protocol.FitMessage
import java.io.InputStream

/**
 * Decodes FIT activity files into [FitActivity]: `file_id`, `record`, `event`, `lap`, `session` and
 * `activity` messages. Other messages (device info, HRV, developer data...) are ignored; use
 * [FitFileReader] for generic access.
 *
 * Mapping rules: invalid values become `null`; `enhanced_altitude`/`enhanced_speed` take precedence
 * over the 16-bit fields; a position needs both latitude and longitude; records without a
 * timestamp are dropped; a lap or session without `start_time` starts at its end timestamp minus
 * its elapsed time.
 */
public class FitActivityDecoder(private val reader: FitFileReader = FitFileReader()) {

    /** Decodes [input] fully. The stream is not closed. */
    public fun decode(input: InputStream): FitActivity = decode(input.readBytes())

    /**
     * Decodes [bytes].
     *
     * @throws FitFormatException if the bytes are not a valid FIT file or contain no `file_id`.
     */
    public fun decode(bytes: ByteArray): FitActivity {
        val messages = reader.read(bytes).messages
        val fileId = messages.firstOrNull { it.globalNumber == FitMessageNumber.FILE_ID }?.let(::fileId)
            ?: throw FitFormatException("FIT file has no file_id message")
        return FitActivity(
            fileId = fileId,
            records = messages.filter { it.globalNumber == FitMessageNumber.RECORD }.mapNotNull(::record),
            events = messages.filter { it.globalNumber == FitMessageNumber.EVENT }.mapNotNull(::event),
            laps = messages.filter { it.globalNumber == FitMessageNumber.LAP }.mapNotNull(::lap),
            sessions = messages.filter { it.globalNumber == FitMessageNumber.SESSION }.mapNotNull(::session),
            activity = messages.firstOrNull { it.globalNumber == FitMessageNumber.ACTIVITY }?.let(::activity),
        )
    }

    private fun fileId(m: FitMessage) = FitFileId(
        type = m.int(FileIdField.TYPE) ?: FitFileType.ACTIVITY,
        manufacturer = m.int(FileIdField.MANUFACTURER) ?: FitManufacturer.DEVELOPMENT,
        product = m.int(FileIdField.PRODUCT),
        serialNumber = m.long(FileIdField.SERIAL_NUMBER),
        timeCreated = m.long(FileIdField.TIME_CREATED)?.let(::fromFitTime),
        productName = m.text(FileIdField.PRODUCT_NAME),
    )

    private fun record(m: FitMessage): FitRecord? {
        val timestamp = m.long(FitCommonField.TIMESTAMP) ?: return null
        val (lat, lon) = position(m, RecordField.POSITION_LAT, RecordField.POSITION_LONG)
        return FitRecord(
            timestamp = fromFitTime(timestamp),
            latitude = lat,
            longitude = lon,
            altitude = m.scaled(RecordField.ENHANCED_ALTITUDE, 5.0, 500.0) ?: m.scaled(RecordField.ALTITUDE, 5.0, 500.0),
            heartRate = m.int(RecordField.HEART_RATE),
            cadence = m.int(RecordField.CADENCE),
            distance = m.scaled(RecordField.DISTANCE, 100.0),
            speed = m.scaled(RecordField.ENHANCED_SPEED, 1000.0) ?: m.scaled(RecordField.SPEED, 1000.0),
            power = m.int(RecordField.POWER),
            temperature = m.int(RecordField.TEMPERATURE),
        )
    }

    private fun event(m: FitMessage): FitEvent? {
        val timestamp = m.long(FitCommonField.TIMESTAMP) ?: return null
        return FitEvent(
            timestamp = fromFitTime(timestamp),
            event = m.int(EventField.EVENT) ?: return null,
            eventType = m.int(EventField.EVENT_TYPE) ?: return null,
            data = m.long(EventField.DATA),
            eventGroup = m.int(EventField.EVENT_GROUP),
        )
    }

    private fun lap(m: FitMessage): FitLap? {
        val end = m.long(FitCommonField.TIMESTAMP) ?: return null
        val timer = m.scaled(LapField.TOTAL_TIMER_TIME, 1000.0)
        val elapsed = m.scaled(LapField.TOTAL_ELAPSED_TIME, 1000.0) ?: timer ?: 0.0
        val (startLat, startLon) = position(m, LapField.START_POSITION_LAT, LapField.START_POSITION_LONG)
        val (endLat, endLon) = position(m, LapField.END_POSITION_LAT, LapField.END_POSITION_LONG)
        return FitLap(
            timestamp = fromFitTime(end),
            startTime = startTime(m, LapField.START_TIME, end, elapsed),
            totalElapsedTime = elapsed,
            totalTimerTime = timer ?: elapsed,
            totalDistance = m.scaled(LapField.TOTAL_DISTANCE, 100.0),
            startLatitude = startLat,
            startLongitude = startLon,
            endLatitude = endLat,
            endLongitude = endLon,
            totalCalories = m.int(LapField.TOTAL_CALORIES),
            avgSpeed = m.scaled(LapField.ENHANCED_AVG_SPEED, 1000.0) ?: m.scaled(LapField.AVG_SPEED, 1000.0),
            maxSpeed = m.scaled(LapField.ENHANCED_MAX_SPEED, 1000.0) ?: m.scaled(LapField.MAX_SPEED, 1000.0),
            avgHeartRate = m.int(LapField.AVG_HEART_RATE),
            maxHeartRate = m.int(LapField.MAX_HEART_RATE),
            avgCadence = m.int(LapField.AVG_CADENCE),
            maxCadence = m.int(LapField.MAX_CADENCE),
            avgPower = m.int(LapField.AVG_POWER),
            maxPower = m.int(LapField.MAX_POWER),
            totalAscent = m.int(LapField.TOTAL_ASCENT),
            totalDescent = m.int(LapField.TOTAL_DESCENT),
            intensity = m.int(LapField.INTENSITY),
            lapTrigger = m.int(LapField.LAP_TRIGGER),
            sport = m.int(LapField.SPORT),
        )
    }

    private fun session(m: FitMessage): FitSession? {
        val end = m.long(FitCommonField.TIMESTAMP) ?: return null
        val timer = m.scaled(SessionField.TOTAL_TIMER_TIME, 1000.0)
        val elapsed = m.scaled(SessionField.TOTAL_ELAPSED_TIME, 1000.0) ?: timer ?: 0.0
        val (startLat, startLon) = position(m, SessionField.START_POSITION_LAT, SessionField.START_POSITION_LONG)
        return FitSession(
            timestamp = fromFitTime(end),
            startTime = startTime(m, SessionField.START_TIME, end, elapsed),
            sport = m.int(SessionField.SPORT) ?: FitSport.GENERIC,
            subSport = m.int(SessionField.SUB_SPORT) ?: FitSubSport.GENERIC,
            totalElapsedTime = elapsed,
            totalTimerTime = timer ?: elapsed,
            totalDistance = m.scaled(SessionField.TOTAL_DISTANCE, 100.0),
            startLatitude = startLat,
            startLongitude = startLon,
            totalCalories = m.int(SessionField.TOTAL_CALORIES),
            avgSpeed = m.scaled(SessionField.ENHANCED_AVG_SPEED, 1000.0) ?: m.scaled(SessionField.AVG_SPEED, 1000.0),
            maxSpeed = m.scaled(SessionField.ENHANCED_MAX_SPEED, 1000.0) ?: m.scaled(SessionField.MAX_SPEED, 1000.0),
            avgHeartRate = m.int(SessionField.AVG_HEART_RATE),
            maxHeartRate = m.int(SessionField.MAX_HEART_RATE),
            avgCadence = m.int(SessionField.AVG_CADENCE),
            maxCadence = m.int(SessionField.MAX_CADENCE),
            avgPower = m.int(SessionField.AVG_POWER),
            maxPower = m.int(SessionField.MAX_POWER),
            totalAscent = m.int(SessionField.TOTAL_ASCENT),
            totalDescent = m.int(SessionField.TOTAL_DESCENT),
            firstLapIndex = m.int(SessionField.FIRST_LAP_INDEX),
            numLaps = m.int(SessionField.NUM_LAPS),
        )
    }

    private fun activity(m: FitMessage): FitActivitySummary? {
        val timestamp = m.long(FitCommonField.TIMESTAMP) ?: return null
        return FitActivitySummary(
            timestamp = fromFitTime(timestamp),
            totalTimerTime = m.scaled(ActivityField.TOTAL_TIMER_TIME, 1000.0) ?: 0.0,
            numSessions = m.int(ActivityField.NUM_SESSIONS) ?: 1,
            type = m.int(ActivityField.TYPE) ?: FitActivityType.MANUAL,
            localTimestamp = m.long(ActivityField.LOCAL_TIMESTAMP)?.let(FitUnits::fromFitLocalTime),
        )
    }

    private fun position(m: FitMessage, latField: Int, lonField: Int): Pair<Double?, Double?> {
        val lat = m.long(latField)
        val lon = m.long(lonField)
        return if (lat == null || lon == null) null to null else FitUnits.fromSemicircles(lat) to FitUnits.fromSemicircles(lon)
    }

    private fun startTime(m: FitMessage, field: Int, end: Long, elapsedSeconds: Double) =
        fromFitTime(m.long(field) ?: (end - elapsedSeconds.toLong()))
}
