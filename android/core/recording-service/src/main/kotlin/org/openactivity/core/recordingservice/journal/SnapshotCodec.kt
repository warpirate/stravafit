// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice.journal

import org.openactivity.metrics.ElevationAccumulator
import org.openactivity.metrics.MovingTimeRule
import org.openactivity.metrics.SplitAccumulator
import org.openactivity.model.Distance
import org.openactivity.model.Lap
import org.openactivity.model.LapTrigger
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.model.Split
import org.openactivity.model.UnitSystem
import org.openactivity.recording.AcceptedFix
import org.openactivity.recording.AutoPauseRule
import org.openactivity.recording.EngineSnapshot
import org.openactivity.recording.GpsGap
import org.openactivity.recording.MotionDetectorState
import org.openactivity.recording.PauseInterval
import org.openactivity.recording.PauseKind
import org.openactivity.recording.PositionFilterState
import org.openactivity.recording.RecordingConfig
import org.openactivity.recording.RecordingStatus
import org.openactivity.recording.SensorChannelState
import org.openactivity.recording.SportProfile
import org.openactivity.recording.TrackPoint
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Binary encoding of an in-progress [EngineSnapshot] for the recording journal.
 *
 * Doubles are written bit-exactly (NaN included) so a decoded snapshot equals the original and the restored
 * engine continues exactly as the old one would have. Enums are written by name, so reordering an enum
 * does not corrupt journals. A finished snapshot is never journalled and cannot be encoded.
 *
 * Any change to the layout must bump [FORMAT]; [decode] rejects other formats, and the service then
 * abandons that journal rather than restoring garbage.
 */
object SnapshotCodec {
    /** Version of the layout written by [encode]. */
    const val FORMAT: Int = 3

    private const val MAGIC = 0x4F415253 // "OARS"

    fun encode(snapshot: EngineSnapshot): ByteArray {
        require(snapshot.finishedActivity == null) { "finished recordings are saved, not journalled" }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(FORMAT)
            Writer(out).snapshot(snapshot)
        }
        return bytes.toByteArray()
    }

    /** @throws IOException when [bytes] are not a snapshot in [FORMAT]. */
    fun decode(bytes: ByteArray): EngineSnapshot {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != MAGIC) throw IOException("not an engine snapshot")
            val format = input.readInt()
            if (format != FORMAT) throw IOException("unsupported snapshot format $format")
            return try {
                Reader(input).snapshot()
            } catch (e: IllegalArgumentException) {
                throw IOException("corrupt snapshot", e)
            }
        }
    }

    private class Writer(private val o: DataOutputStream) {
        fun snapshot(s: EngineSnapshot) {
            config(s.config)
            enum(s.status)
            o.writeLong(s.offsetNanos)
            nullableLong(s.clockNanos)
            o.writeLong(s.startNanos)
            o.writeLong(s.finishNanos)
            nullableLong(s.wallAnchorNanos)
            o.writeLong(s.wallAnchorMillis)
            o.writeLong(s.timerNanos)
            o.writeLong(s.movingNanos)
            nullableEnum(s.openPauseKind)
            o.writeLong(s.openPauseStart)
            o.writeLong(s.lastTimerStart)
            o.writeDouble(s.maxSpeedMps)
            list(s.pauses) { enum(it.kind); o.writeLong(it.startOffsetNanos); o.writeLong(it.endOffsetNanos) }
            o.writeInt(s.autoPauseCount)
            o.writeDouble(s.distanceMeters)
            nullable(s.anchor, ::latLng)
            o.writeBoolean(s.bridgePending)
            o.writeDouble(s.previousFusedSpeed)
            o.writeBoolean(s.dopplerTrusted)
            nullable(s.dopplerCheckAnchor, ::latLng)
            o.writeLong(s.dopplerCheckStart)
            o.writeDouble(s.dopplerCheckDistance)
            o.writeInt(s.segment)
            nullableLong(s.lastFixNanos)
            nullable(s.lastAccepted) {
                o.writeDouble(it.latitude); o.writeDouble(it.longitude); o.writeLong(it.nanos); o.writeDouble(it.accuracy)
            }
            o.writeInt(s.rejectsInARow)
            nullableDouble(s.lastAccuracy)
            nullableLong(s.lastAccuracyNanos)
            list(s.gaps) { o.writeLong(it.startOffsetNanos); o.writeLong(it.durationNanos); o.writeDouble(it.bridgedMeters) }
            o.writeBoolean(s.mockSeen)
            o.writeBoolean(s.interrupted)
            nullable(s.filter) {
                latLng(it.origin)
                o.writeDouble(it.east); o.writeDouble(it.north)
                o.writeDouble(it.velocityEast); o.writeDouble(it.velocityNorth)
                o.writeDouble(it.p00); o.writeDouble(it.p01); o.writeDouble(it.p11)
                o.writeLong(it.lastNanos)
                o.writeInt(it.gatedInARow)
            }
            motion(s.motion)
            elevation(s.gpsElevation)
            elevation(s.baroElevation)
            o.writeBoolean(s.hasBarometer)
            o.writeDouble(s.verticalAccuracyAvg)
            o.writeDouble(s.carriedGain)
            o.writeDouble(s.carriedLoss)
            o.writeDouble(s.baroAltitude)
            o.writeDouble(s.baroCalibrationSum)
            o.writeInt(s.baroCalibrationCount)
            o.writeDouble(s.stepSpeed)
            o.writeDouble(s.baroMin)
            o.writeDouble(s.baroMax)
            o.writeDouble(s.gpsAltMin)
            o.writeDouble(s.gpsAltMax)
            o.writeLong(s.stillMovingNanos)
            o.writeDouble(s.lastGpsAltitude)
            o.writeLong(s.lastGpsAltitudeNanos)
            splitState(s.splits)
            list(s.laps, ::lap)
            o.writeLong(s.lapStartNanos)
            o.writeDouble(s.lapStartDistance)
            o.writeLong(s.lapStartMovingNanos)
            o.writeDouble(s.lapStartGain)
            list(s.points, ::point)
            sensor(s.heartRate)
            sensor(s.power)
            sensor(s.cadence)
        }

        private fun config(c: RecordingConfig) {
            enum(c.sport)
            o.writeBoolean(c.autoPauseEnabled)
            enum(c.unitSystem)
            val p = c.profile
            o.writeDouble(p.maxSpeedMps)
            o.writeDouble(p.movingRule.thresholdMps)
            o.writeDouble(p.movingRule.gapCapSeconds)
            o.writeDouble(p.movingRule.smoothingWindowSeconds)
            nullable(p.autoPause) {
                o.writeDouble(it.pauseBelowMps); o.writeDouble(it.pauseAfterSeconds)
                o.writeDouble(it.resumeAboveMps); o.writeDouble(it.resumeAfterSeconds)
            }
            o.writeDouble(p.processNoiseAccel)
            o.writeDouble(p.stationaryEnterMps)
            o.writeDouble(p.stationaryExitMps)
            o.writeDouble(c.maxAccuracyMeters)
            o.writeDouble(c.autoResumeMaxAccuracyMeters)
            o.writeDouble(c.minStepMeters)
            o.writeDouble(c.gapResetSeconds)
            o.writeDouble(c.gpsLostSeconds)
            o.writeDouble(c.significantGapMeters)
        }

        private fun motion(m: MotionDetectorState) {
            o.writeDouble(m.smoothedSpeed)
            o.writeBoolean(m.isStationary)
            o.writeBoolean(m.hasSpeed)
            o.writeInt(m.slowFixes)
            o.writeInt(m.fastFixes)
            nullableLong(m.belowSince)
            nullableLong(m.aboveSince)
            nullableLong(m.rawBelowSince)
            nullableLong(m.rawAboveSince)
        }

        private fun elevation(e: ElevationAccumulator.State) {
            list(e.ring) { o.writeDouble(it) }
            o.writeInt(e.count)
            o.writeInt(e.next)
            o.writeDouble(e.reference)
            o.writeDouble(e.gain)
            o.writeDouble(e.loss)
            o.writeDouble(e.smoothed)
        }

        private fun splitState(s: SplitAccumulator.State) {
            list(s.completed, ::split)
            o.writeBoolean(s.hasSample)
            o.writeDouble(s.lastTime)
            o.writeDouble(s.lastDistance)
            o.writeDouble(s.lastMoving)
            o.writeDouble(s.lastAltitude)
            o.writeDouble(s.splitStartTime)
            o.writeDouble(s.splitStartDistance)
            o.writeDouble(s.splitStartMoving)
            o.writeDouble(s.splitStartAltitude)
        }

        private fun split(s: Split) {
            o.writeInt(s.index)
            o.writeDouble(s.distance.meters)
            duration(s.elapsedTime)
            duration(s.movingTime)
            nullableDouble(s.elevationDelta?.meters)
            o.writeBoolean(s.isPartial)
        }

        private fun lap(l: Lap) {
            o.writeInt(l.index)
            duration(l.startOffset)
            duration(l.elapsedTime)
            duration(l.movingTime)
            o.writeDouble(l.distance.meters)
            o.writeDouble(l.elevationGain.meters)
            enum(l.trigger)
        }

        private fun point(p: TrackPoint) {
            o.writeLong(p.offsetNanos)
            o.writeLong(p.epochMillis)
            o.writeDouble(p.latitude)
            o.writeDouble(p.longitude)
            o.writeDouble(p.rawLatitude)
            o.writeDouble(p.rawLongitude)
            nullableDouble(p.accuracyMeters)
            nullableDouble(p.altitudeMeters)
            o.writeDouble(p.speedMps)
            o.writeDouble(p.distanceMeters)
            o.writeBoolean(p.paused)
            o.writeInt(p.segment)
            nullable(p.heartRateBpm) { o.writeInt(it) }
            nullableDouble(p.cadence)
            nullable(p.powerWatts) { o.writeInt(it) }
        }

        private fun sensor(s: SensorChannelState) {
            o.writeDouble(s.latest)
            o.writeLong(s.latestNanos)
            o.writeDouble(s.weightedSum)
            o.writeLong(s.weightNanos)
            o.writeDouble(s.max)
        }

        private fun latLng(p: LatLng) {
            o.writeDouble(p.latitude)
            o.writeDouble(p.longitude)
        }

        private fun duration(d: Duration) = o.writeLong(d.inWholeNanoseconds)

        private fun enum(e: Enum<*>) = o.writeUTF(e.name)

        private fun nullableEnum(e: Enum<*>?) = nullable(e) { enum(it) }

        private fun nullableLong(v: Long?) = nullable(v) { o.writeLong(it) }

        private fun nullableDouble(v: Double?) = nullable(v) { o.writeDouble(it) }

        private inline fun <T> nullable(v: T?, write: (T) -> Unit) {
            o.writeBoolean(v != null)
            if (v != null) write(v)
        }

        private inline fun <T> list(items: List<T>, write: (T) -> Unit) {
            o.writeInt(items.size)
            items.forEach(write)
        }
    }

    private class Reader(private val i: DataInputStream) {
        fun snapshot(): EngineSnapshot = EngineSnapshot(
            config = config(),
            status = enum<RecordingStatus>(),
            offsetNanos = i.readLong(),
            clockNanos = nullableLong(),
            startNanos = i.readLong(),
            finishNanos = i.readLong(),
            wallAnchorNanos = nullableLong(),
            wallAnchorMillis = i.readLong(),
            timerNanos = i.readLong(),
            movingNanos = i.readLong(),
            openPauseKind = nullable { enum<PauseKind>() },
            openPauseStart = i.readLong(),
            lastTimerStart = i.readLong(),
            maxSpeedMps = i.readDouble(),
            pauses = list { PauseInterval(enum(), i.readLong(), i.readLong()) },
            autoPauseCount = i.readInt(),
            distanceMeters = i.readDouble(),
            anchor = nullable(::latLng),
            bridgePending = i.readBoolean(),
            previousFusedSpeed = i.readDouble(),
            dopplerTrusted = i.readBoolean(),
            dopplerCheckAnchor = nullable(::latLng),
            dopplerCheckStart = i.readLong(),
            dopplerCheckDistance = i.readDouble(),
            segment = i.readInt(),
            lastFixNanos = nullableLong(),
            lastAccepted = nullable { AcceptedFix(i.readDouble(), i.readDouble(), i.readLong(), i.readDouble()) },
            rejectsInARow = i.readInt(),
            lastAccuracy = nullableDouble(),
            lastAccuracyNanos = nullableLong(),
            gaps = list { GpsGap(i.readLong(), i.readLong(), i.readDouble()) },
            mockSeen = i.readBoolean(),
            interrupted = i.readBoolean(),
            filter = nullable {
                PositionFilterState(
                    origin = latLng(),
                    east = i.readDouble(),
                    north = i.readDouble(),
                    velocityEast = i.readDouble(),
                    velocityNorth = i.readDouble(),
                    p00 = i.readDouble(),
                    p01 = i.readDouble(),
                    p11 = i.readDouble(),
                    lastNanos = i.readLong(),
                    gatedInARow = i.readInt(),
                )
            },
            motion = motion(),
            gpsElevation = elevation(),
            baroElevation = elevation(),
            hasBarometer = i.readBoolean(),
            verticalAccuracyAvg = i.readDouble(),
            carriedGain = i.readDouble(),
            carriedLoss = i.readDouble(),
            baroAltitude = i.readDouble(),
            baroCalibrationSum = i.readDouble(),
            baroCalibrationCount = i.readInt(),
            stepSpeed = i.readDouble(),
            baroMin = i.readDouble(),
            baroMax = i.readDouble(),
            gpsAltMin = i.readDouble(),
            gpsAltMax = i.readDouble(),
            stillMovingNanos = i.readLong(),
            lastGpsAltitude = i.readDouble(),
            lastGpsAltitudeNanos = i.readLong(),
            splits = splitState(),
            laps = list(::lap),
            lapStartNanos = i.readLong(),
            lapStartDistance = i.readDouble(),
            lapStartMovingNanos = i.readLong(),
            lapStartGain = i.readDouble(),
            points = list(::point),
            heartRate = sensor(),
            power = sensor(),
            cadence = sensor(),
            finishedActivity = null,
        )

        private fun config(): RecordingConfig {
            val sport = enum<SportType>()
            val autoPauseEnabled = i.readBoolean()
            val units = enum<UnitSystem>()
            val profile = SportProfile(
                maxSpeedMps = i.readDouble(),
                movingRule = MovingTimeRule(i.readDouble(), i.readDouble(), i.readDouble()),
                autoPause = nullable { AutoPauseRule(i.readDouble(), i.readDouble(), i.readDouble(), i.readDouble()) },
                processNoiseAccel = i.readDouble(),
                stationaryEnterMps = i.readDouble(),
                stationaryExitMps = i.readDouble(),
            )
            return RecordingConfig(
                sport = sport,
                autoPauseEnabled = autoPauseEnabled,
                unitSystem = units,
                profile = profile,
                maxAccuracyMeters = i.readDouble(),
                autoResumeMaxAccuracyMeters = i.readDouble(),
                minStepMeters = i.readDouble(),
                gapResetSeconds = i.readDouble(),
                gpsLostSeconds = i.readDouble(),
                significantGapMeters = i.readDouble(),
            )
        }

        private fun motion() = MotionDetectorState(
            smoothedSpeed = i.readDouble(),
            isStationary = i.readBoolean(),
            hasSpeed = i.readBoolean(),
            slowFixes = i.readInt(),
            fastFixes = i.readInt(),
            belowSince = nullableLong(),
            aboveSince = nullableLong(),
            rawBelowSince = nullableLong(),
            rawAboveSince = nullableLong(),
        )

        private fun elevation() = ElevationAccumulator.State(
            ring = list { i.readDouble() },
            count = i.readInt(),
            next = i.readInt(),
            reference = i.readDouble(),
            gain = i.readDouble(),
            loss = i.readDouble(),
            smoothed = i.readDouble(),
        )

        private fun splitState() = SplitAccumulator.State(
            completed = list(::split),
            hasSample = i.readBoolean(),
            lastTime = i.readDouble(),
            lastDistance = i.readDouble(),
            lastMoving = i.readDouble(),
            lastAltitude = i.readDouble(),
            splitStartTime = i.readDouble(),
            splitStartDistance = i.readDouble(),
            splitStartMoving = i.readDouble(),
            splitStartAltitude = i.readDouble(),
        )

        private fun split() = Split(
            index = i.readInt(),
            distance = Distance(i.readDouble()),
            elapsedTime = duration(),
            movingTime = duration(),
            elevationDelta = nullableDouble()?.let(::Distance),
            isPartial = i.readBoolean(),
        )

        private fun lap() = Lap(
            index = i.readInt(),
            startOffset = duration(),
            elapsedTime = duration(),
            movingTime = duration(),
            distance = Distance(i.readDouble()),
            elevationGain = Distance(i.readDouble()),
            trigger = enum<LapTrigger>(),
        )

        private fun point() = TrackPoint(
            offsetNanos = i.readLong(),
            epochMillis = i.readLong(),
            latitude = i.readDouble(),
            longitude = i.readDouble(),
            rawLatitude = i.readDouble(),
            rawLongitude = i.readDouble(),
            accuracyMeters = nullableDouble(),
            altitudeMeters = nullableDouble(),
            speedMps = i.readDouble(),
            distanceMeters = i.readDouble(),
            paused = i.readBoolean(),
            segment = i.readInt(),
            heartRateBpm = nullable { i.readInt() },
            cadence = nullableDouble(),
            powerWatts = nullable { i.readInt() },
        )

        private fun sensor() = SensorChannelState(i.readDouble(), i.readLong(), i.readDouble(), i.readLong(), i.readDouble())

        private fun latLng() = LatLng(i.readDouble(), i.readDouble())

        private fun duration(): Duration = i.readLong().nanoseconds

        private inline fun <reified E : Enum<E>> enum(): E {
            val name = i.readUTF()
            return enumValues<E>().firstOrNull { it.name == name } ?: throw IOException("unknown ${E::class.simpleName} $name")
        }

        private fun nullableLong(): Long? = nullable { i.readLong() }

        private fun nullableDouble(): Double? = nullable { i.readDouble() }

        private inline fun <T> nullable(read: () -> T): T? = if (i.readBoolean()) read() else null

        private inline fun <T> list(read: () -> T): List<T> {
            val n = i.readInt()
            if (n < 0 || n > MAX_LIST) throw IOException("bad list length $n")
            return List(n) { read() }
        }
    }

    private const val MAX_LIST = 10_000_000
}
