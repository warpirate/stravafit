// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.fit

import app.stravafit.lib.fit.FitUnits.toFitTime
import app.stravafit.lib.fit.FitUnits.toSemicircles
import app.stravafit.lib.fit.protocol.FieldDefinition
import app.stravafit.lib.fit.protocol.FitBaseType
import app.stravafit.lib.fit.protocol.FitBaseType.ENUM
import app.stravafit.lib.fit.protocol.FitBaseType.SINT32
import app.stravafit.lib.fit.protocol.FitBaseType.SINT8
import app.stravafit.lib.fit.protocol.FitBaseType.UINT16
import app.stravafit.lib.fit.protocol.FitBaseType.UINT32
import app.stravafit.lib.fit.protocol.FitBaseType.UINT32Z
import app.stravafit.lib.fit.protocol.FitBaseType.UINT8
import app.stravafit.lib.fit.protocol.FitFileWriter
import app.stravafit.lib.fit.protocol.MessageDefinition
import java.time.Instant
import kotlin.math.roundToLong

/**
 * Options for [FitActivityEncoder].
 *
 * @property compressTimestamps Write records less than 32 s after the previous message with a
 *   1-byte compressed-timestamp header instead of a 4-byte timestamp field (smaller files; all
 *   mainstream decoders support it, but some minimal ones do not).
 */
public data class FitEncodeOptions(val compressTimestamps: Boolean = false)

/**
 * Encodes a [FitActivity] as a FIT activity file, following the protocol's "summary last" layout:
 * `file_id`, then events and records in chronological order (timer-start events before records
 * with the same timestamp), then laps, sessions and the `activity` message.
 *
 * Record channels absent from every record are not defined at all; absent values in other records
 * and values that do not fit the field (e.g. a heart rate of 300) are written as FIT invalid values.
 * Altitude and speed are written both as the legacy 16-bit fields and the `enhanced_*` 32-bit fields.
 * The encoder does not apply privacy trimming; pass already-trimmed records.
 */
public class FitActivityEncoder(private val options: FitEncodeOptions = FitEncodeOptions()) {

    /** Returns the FIT file bytes for [activity]. */
    public fun encode(activity: FitActivity): ByteArray {
        val writer = FitFileWriter()
        writeFileId(writer, activity.fileId)
        writeTimeline(writer, activity)
        if (activity.laps.isNotEmpty()) {
            writer.writeDefinition(LOCAL_LAP, LAP_DEFINITION)
            activity.laps.forEachIndexed { index, lap -> writer.writeData(LOCAL_LAP, lapValues(index, lap)) }
        }
        if (activity.sessions.isNotEmpty()) {
            writer.writeDefinition(LOCAL_SESSION, SESSION_DEFINITION)
            activity.sessions.forEachIndexed { index, session -> writer.writeData(LOCAL_SESSION, sessionValues(index, session)) }
        }
        activity.activity?.let { summary ->
            writer.writeDefinition(LOCAL_ACTIVITY, ACTIVITY_DEFINITION)
            writer.writeData(
                LOCAL_ACTIVITY,
                listOf(
                    toFitTime(summary.timestamp),
                    millis(summary.totalTimerTime),
                    fit(summary.numSessions.toLong(), UINT16),
                    fit(summary.type.toLong(), ENUM),
                    FitEventCode.ACTIVITY,
                    FitEventType.STOP,
                    summary.localTimestamp?.let { fit(FitUnits.toFitLocalTime(it), UINT32) },
                ),
            )
        }
        return writer.toByteArray()
    }

    private fun writeFileId(writer: FitFileWriter, id: FitFileId) {
        val nameBytes = id.productName?.toByteArray(Charsets.UTF_8)?.size?.let { (it + 1).coerceAtMost(255) }
        val fields = buildList {
            add(FieldDefinition(FileIdField.TYPE, ENUM))
            add(FieldDefinition(FileIdField.MANUFACTURER, UINT16))
            add(FieldDefinition(FileIdField.PRODUCT, UINT16))
            add(FieldDefinition(FileIdField.SERIAL_NUMBER, UINT32Z))
            add(FieldDefinition(FileIdField.TIME_CREATED, UINT32))
            if (nameBytes != null) add(FieldDefinition(FileIdField.PRODUCT_NAME, FitBaseType.STRING, nameBytes))
        }
        writer.writeDefinition(LOCAL_FILE_ID, MessageDefinition(FitMessageNumber.FILE_ID, fields))
        val values = mutableListOf<Any?>(
            fit(id.type.toLong(), ENUM),
            fit(id.manufacturer.toLong(), UINT16),
            id.product?.let { fit(it.toLong(), UINT16) },
            id.serialNumber?.let { fit(it, UINT32Z) },
            id.timeCreated?.let(::toFitTime),
        )
        if (nameBytes != null) values += id.productName
        writer.writeData(LOCAL_FILE_ID, values)
    }

    /** Writes events and records interleaved by time, using compressed headers when enabled. */
    private fun writeTimeline(writer: FitFileWriter, activity: FitActivity) {
        val channels = RecordChannels.of(activity.records)
        val fullRecord = channels.definition(withTimestamp = true)
        val compressedRecord = channels.definition(withTimestamp = false)
        var definedEvent = false
        var definedFull = false
        var definedCompressed = false
        var lastTimestamp: Long? = null

        val timeline = activity.events.map { TimelineEntry(it.timestamp, if (it.eventType == FitEventType.START) 0 else 2, it) } +
            activity.records.map { TimelineEntry(it.timestamp, 1, it) }
        for (entry in timeline.sortedWith(compareBy({ it.time }, { it.priority }))) {
            val timestamp = toFitTime(entry.time)
            when (val item = entry.item) {
                is FitEvent -> {
                    if (!definedEvent) writer.writeDefinition(LOCAL_EVENT, EVENT_DEFINITION).also { definedEvent = true }
                    writer.writeData(
                        LOCAL_EVENT,
                        listOf(
                            timestamp,
                            fit(item.event.toLong(), ENUM),
                            fit(item.eventType.toLong(), ENUM),
                            item.data?.let { fit(it, UINT32) },
                            item.eventGroup?.let { fit(it.toLong(), UINT8) },
                        ),
                    )
                }
                is FitRecord -> {
                    val delta = lastTimestamp?.let { timestamp - it }
                    if (options.compressTimestamps && delta != null && delta in 0..31) {
                        if (!definedCompressed) writer.writeDefinition(LOCAL_RECORD_COMPRESSED, compressedRecord).also { definedCompressed = true }
                        writer.writeCompressedTimestampData(LOCAL_RECORD_COMPRESSED, (timestamp and 0x1F).toInt(), channels.values(item, null))
                    } else {
                        if (!definedFull) writer.writeDefinition(LOCAL_RECORD, fullRecord).also { definedFull = true }
                        writer.writeData(LOCAL_RECORD, channels.values(item, timestamp))
                    }
                }
            }
            lastTimestamp = timestamp
        }
    }

    private class TimelineEntry(val time: Instant, val priority: Int, val item: Any)

    /** The set of record channels present in at least one record, in a fixed field order. */
    private class RecordChannels(
        val position: Boolean,
        val altitude: Boolean,
        val heartRate: Boolean,
        val cadence: Boolean,
        val distance: Boolean,
        val speed: Boolean,
        val power: Boolean,
        val temperature: Boolean,
    ) {
        fun definition(withTimestamp: Boolean): MessageDefinition = MessageDefinition(
            FitMessageNumber.RECORD,
            buildList {
                if (withTimestamp) add(FieldDefinition(FitCommonField.TIMESTAMP, UINT32))
                if (position) {
                    add(FieldDefinition(RecordField.POSITION_LAT, SINT32))
                    add(FieldDefinition(RecordField.POSITION_LONG, SINT32))
                }
                if (distance) add(FieldDefinition(RecordField.DISTANCE, UINT32))
                if (altitude) {
                    add(FieldDefinition(RecordField.ALTITUDE, UINT16))
                    add(FieldDefinition(RecordField.ENHANCED_ALTITUDE, UINT32))
                }
                if (speed) {
                    add(FieldDefinition(RecordField.SPEED, UINT16))
                    add(FieldDefinition(RecordField.ENHANCED_SPEED, UINT32))
                }
                if (heartRate) add(FieldDefinition(RecordField.HEART_RATE, UINT8))
                if (cadence) add(FieldDefinition(RecordField.CADENCE, UINT8))
                if (power) add(FieldDefinition(RecordField.POWER, UINT16))
                if (temperature) add(FieldDefinition(RecordField.TEMPERATURE, SINT8))
            },
        )

        fun values(r: FitRecord, timestamp: Long?): List<Any?> = buildList {
            if (timestamp != null) add(timestamp)
            if (position) {
                add(r.latitude?.let(::toSemicircles))
                add(r.longitude?.let(::toSemicircles))
            }
            if (distance) add(r.distance?.let { fit(scale(it, 100.0), UINT32) })
            if (altitude) {
                val raw = r.altitude?.let { scale(it + 500.0, 5.0) }
                add(raw?.let { fit(it, UINT16) })
                add(raw?.let { fit(it, UINT32) })
            }
            if (speed) {
                val raw = r.speed?.let { scale(it, 1000.0) }
                add(raw?.let { fit(it, UINT16) })
                add(raw?.let { fit(it, UINT32) })
            }
            if (heartRate) add(r.heartRate?.let { fit(it.toLong(), UINT8) })
            if (cadence) add(r.cadence?.let { fit(it.toLong(), UINT8) })
            if (power) add(r.power?.let { fit(it.toLong(), UINT16) })
            if (temperature) add(r.temperature?.let { fit(it.toLong(), SINT8) })
        }

        companion object {
            fun of(records: List<FitRecord>) = RecordChannels(
                position = records.any { it.latitude != null },
                altitude = records.any { it.altitude != null },
                heartRate = records.any { it.heartRate != null },
                cadence = records.any { it.cadence != null },
                distance = records.any { it.distance != null },
                speed = records.any { it.speed != null },
                power = records.any { it.power != null },
                temperature = records.any { it.temperature != null },
            )
        }
    }

    private fun lapValues(index: Int, lap: FitLap): List<Any?> = listOf(
        fit(index.toLong(), UINT16),
        toFitTime(lap.timestamp),
        FitEventCode.LAP,
        FitEventType.STOP,
        toFitTime(lap.startTime),
        lap.startLatitude?.let(::toSemicircles),
        lap.startLongitude?.let(::toSemicircles),
        lap.endLatitude?.let(::toSemicircles),
        lap.endLongitude?.let(::toSemicircles),
        millis(lap.totalElapsedTime),
        millis(lap.totalTimerTime),
        lap.totalDistance?.let { fit(scale(it, 100.0), UINT32) },
        lap.totalCalories?.let { fit(it.toLong(), UINT16) },
        lap.avgSpeed?.let { fit(scale(it, 1000.0), UINT16) },
        lap.maxSpeed?.let { fit(scale(it, 1000.0), UINT16) },
        lap.avgHeartRate?.let { fit(it.toLong(), UINT8) },
        lap.maxHeartRate?.let { fit(it.toLong(), UINT8) },
        lap.avgCadence?.let { fit(it.toLong(), UINT8) },
        lap.maxCadence?.let { fit(it.toLong(), UINT8) },
        lap.avgPower?.let { fit(it.toLong(), UINT16) },
        lap.maxPower?.let { fit(it.toLong(), UINT16) },
        lap.totalAscent?.let { fit(it.toLong(), UINT16) },
        lap.totalDescent?.let { fit(it.toLong(), UINT16) },
        lap.intensity?.let { fit(it.toLong(), ENUM) },
        lap.lapTrigger?.let { fit(it.toLong(), ENUM) },
        lap.sport?.let { fit(it.toLong(), ENUM) },
        lap.avgSpeed?.let { fit(scale(it, 1000.0), UINT32) },
        lap.maxSpeed?.let { fit(scale(it, 1000.0), UINT32) },
    )

    private fun sessionValues(index: Int, s: FitSession): List<Any?> = listOf(
        fit(index.toLong(), UINT16),
        toFitTime(s.timestamp),
        FitEventCode.SESSION,
        FitEventType.STOP,
        toFitTime(s.startTime),
        s.startLatitude?.let(::toSemicircles),
        s.startLongitude?.let(::toSemicircles),
        fit(s.sport.toLong(), ENUM),
        fit(s.subSport.toLong(), ENUM),
        millis(s.totalElapsedTime),
        millis(s.totalTimerTime),
        s.totalDistance?.let { fit(scale(it, 100.0), UINT32) },
        s.totalCalories?.let { fit(it.toLong(), UINT16) },
        s.avgSpeed?.let { fit(scale(it, 1000.0), UINT16) },
        s.maxSpeed?.let { fit(scale(it, 1000.0), UINT16) },
        s.avgHeartRate?.let { fit(it.toLong(), UINT8) },
        s.maxHeartRate?.let { fit(it.toLong(), UINT8) },
        s.avgCadence?.let { fit(it.toLong(), UINT8) },
        s.maxCadence?.let { fit(it.toLong(), UINT8) },
        s.avgPower?.let { fit(it.toLong(), UINT16) },
        s.maxPower?.let { fit(it.toLong(), UINT16) },
        s.totalAscent?.let { fit(it.toLong(), UINT16) },
        s.totalDescent?.let { fit(it.toLong(), UINT16) },
        s.firstLapIndex?.let { fit(it.toLong(), UINT16) },
        s.numLaps?.let { fit(it.toLong(), UINT16) },
        s.avgSpeed?.let { fit(scale(it, 1000.0), UINT32) },
        s.maxSpeed?.let { fit(scale(it, 1000.0), UINT32) },
    )

    private companion object {
        const val LOCAL_FILE_ID = 0
        const val LOCAL_EVENT = 1
        const val LOCAL_RECORD = 2
        const val LOCAL_RECORD_COMPRESSED = 3 // compressed headers can only address local types 0..3
        const val LOCAL_LAP = 4
        const val LOCAL_SESSION = 5
        const val LOCAL_ACTIVITY = 6

        val EVENT_DEFINITION = MessageDefinition(
            FitMessageNumber.EVENT,
            listOf(
                FieldDefinition(FitCommonField.TIMESTAMP, UINT32),
                FieldDefinition(EventField.EVENT, ENUM),
                FieldDefinition(EventField.EVENT_TYPE, ENUM),
                FieldDefinition(EventField.DATA, UINT32),
                FieldDefinition(EventField.EVENT_GROUP, UINT8),
            ),
        )

        val LAP_DEFINITION = MessageDefinition(
            FitMessageNumber.LAP,
            listOf(
                FieldDefinition(FitCommonField.MESSAGE_INDEX, UINT16),
                FieldDefinition(FitCommonField.TIMESTAMP, UINT32),
                FieldDefinition(LapField.EVENT, ENUM),
                FieldDefinition(LapField.EVENT_TYPE, ENUM),
                FieldDefinition(LapField.START_TIME, UINT32),
                FieldDefinition(LapField.START_POSITION_LAT, SINT32),
                FieldDefinition(LapField.START_POSITION_LONG, SINT32),
                FieldDefinition(LapField.END_POSITION_LAT, SINT32),
                FieldDefinition(LapField.END_POSITION_LONG, SINT32),
                FieldDefinition(LapField.TOTAL_ELAPSED_TIME, UINT32),
                FieldDefinition(LapField.TOTAL_TIMER_TIME, UINT32),
                FieldDefinition(LapField.TOTAL_DISTANCE, UINT32),
                FieldDefinition(LapField.TOTAL_CALORIES, UINT16),
                FieldDefinition(LapField.AVG_SPEED, UINT16),
                FieldDefinition(LapField.MAX_SPEED, UINT16),
                FieldDefinition(LapField.AVG_HEART_RATE, UINT8),
                FieldDefinition(LapField.MAX_HEART_RATE, UINT8),
                FieldDefinition(LapField.AVG_CADENCE, UINT8),
                FieldDefinition(LapField.MAX_CADENCE, UINT8),
                FieldDefinition(LapField.AVG_POWER, UINT16),
                FieldDefinition(LapField.MAX_POWER, UINT16),
                FieldDefinition(LapField.TOTAL_ASCENT, UINT16),
                FieldDefinition(LapField.TOTAL_DESCENT, UINT16),
                FieldDefinition(LapField.INTENSITY, ENUM),
                FieldDefinition(LapField.LAP_TRIGGER, ENUM),
                FieldDefinition(LapField.SPORT, ENUM),
                FieldDefinition(LapField.ENHANCED_AVG_SPEED, UINT32),
                FieldDefinition(LapField.ENHANCED_MAX_SPEED, UINT32),
            ),
        )

        val SESSION_DEFINITION = MessageDefinition(
            FitMessageNumber.SESSION,
            listOf(
                FieldDefinition(FitCommonField.MESSAGE_INDEX, UINT16),
                FieldDefinition(FitCommonField.TIMESTAMP, UINT32),
                FieldDefinition(SessionField.EVENT, ENUM),
                FieldDefinition(SessionField.EVENT_TYPE, ENUM),
                FieldDefinition(SessionField.START_TIME, UINT32),
                FieldDefinition(SessionField.START_POSITION_LAT, SINT32),
                FieldDefinition(SessionField.START_POSITION_LONG, SINT32),
                FieldDefinition(SessionField.SPORT, ENUM),
                FieldDefinition(SessionField.SUB_SPORT, ENUM),
                FieldDefinition(SessionField.TOTAL_ELAPSED_TIME, UINT32),
                FieldDefinition(SessionField.TOTAL_TIMER_TIME, UINT32),
                FieldDefinition(SessionField.TOTAL_DISTANCE, UINT32),
                FieldDefinition(SessionField.TOTAL_CALORIES, UINT16),
                FieldDefinition(SessionField.AVG_SPEED, UINT16),
                FieldDefinition(SessionField.MAX_SPEED, UINT16),
                FieldDefinition(SessionField.AVG_HEART_RATE, UINT8),
                FieldDefinition(SessionField.MAX_HEART_RATE, UINT8),
                FieldDefinition(SessionField.AVG_CADENCE, UINT8),
                FieldDefinition(SessionField.MAX_CADENCE, UINT8),
                FieldDefinition(SessionField.AVG_POWER, UINT16),
                FieldDefinition(SessionField.MAX_POWER, UINT16),
                FieldDefinition(SessionField.TOTAL_ASCENT, UINT16),
                FieldDefinition(SessionField.TOTAL_DESCENT, UINT16),
                FieldDefinition(SessionField.FIRST_LAP_INDEX, UINT16),
                FieldDefinition(SessionField.NUM_LAPS, UINT16),
                FieldDefinition(SessionField.ENHANCED_AVG_SPEED, UINT32),
                FieldDefinition(SessionField.ENHANCED_MAX_SPEED, UINT32),
            ),
        )

        val ACTIVITY_DEFINITION = MessageDefinition(
            FitMessageNumber.ACTIVITY,
            listOf(
                FieldDefinition(FitCommonField.TIMESTAMP, UINT32),
                FieldDefinition(ActivityField.TOTAL_TIMER_TIME, UINT32),
                FieldDefinition(ActivityField.NUM_SESSIONS, UINT16),
                FieldDefinition(ActivityField.TYPE, ENUM),
                FieldDefinition(ActivityField.EVENT, ENUM),
                FieldDefinition(ActivityField.EVENT_TYPE, ENUM),
                FieldDefinition(ActivityField.LOCAL_TIMESTAMP, UINT32),
            ),
        )

        fun scale(value: Double, factor: Double): Long = (value * factor).roundToLong()

        fun millis(seconds: Double): Long? = fit(scale(seconds, 1000.0), UINT32)

        /** [raw] if it is a valid value for [type], otherwise `null` (written as invalid). */
        fun fit(raw: Long, type: FitBaseType): Long? = raw.takeIf { it in type.validRange }
    }
}
