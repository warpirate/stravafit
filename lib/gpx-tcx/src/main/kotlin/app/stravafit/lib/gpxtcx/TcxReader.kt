// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx

import app.stravafit.lib.gpxtcx.internal.PointBuilder
import app.stravafit.lib.gpxtcx.internal.attribute
import app.stravafit.lib.gpxtcx.internal.forEachChild
import app.stravafit.lib.gpxtcx.internal.moveToRoot
import app.stravafit.lib.gpxtcx.internal.openXml
import app.stravafit.lib.gpxtcx.internal.readText
import app.stravafit.lib.gpxtcx.internal.skipElement
import app.stravafit.lib.gpxtcx.internal.toFiniteDoubleOrNull
import app.stravafit.lib.gpxtcx.internal.toFormatException
import app.stravafit.lib.gpxtcx.internal.toInstantOrNull
import app.stravafit.lib.gpxtcx.internal.toRoundedIntOrNull
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/**
 * Streaming reader for Garmin Training Center Database v2 (TCX) activity files.
 *
 * Reads `Activities/Activity` (including activities nested in a `MultiSportSession`) with laps,
 * tracks and trackpoints, plus the ActivityExtension v2 `TPX` (`Speed`, `Watts`, `RunCadence`)
 * and `LX` (`AvgSpeed`, `AvgRunCadence`, `AvgWatts`, `MaxWatts`) extensions. `Courses`,
 * `Workouts` and other sections are skipped. Like [GpxReader] it ignores namespaces, a leading
 * byte-order mark or whitespace, and rejects DTDs.
 *
 * Tolerance rules: a lap without `StartTime` falls back to its first trackpoint time or the
 * activity id; missing lap totals are derived from the trackpoints; trackpoints without a valid
 * position are kept (indoor activities) with a `null` position.
 */
public class TcxReader {

    /**
     * Reads a TCX document from [input]. The stream is not closed.
     *
     * @throws TrackFileFormatException if the input is not well-formed XML, its root is not
     *   `<TrainingCenterDatabase>`, or an activity has neither an `Id` nor a lap start time.
     */
    public fun read(input: InputStream): TcxDocument {
        val xml = openXml(input)
        val activities = mutableListOf<TcxActivity>()
        try {
            val root = xml.moveToRoot()
            if (root != "TrainingCenterDatabase") {
                throw TrackFileFormatException("Expected <TrainingCenterDatabase> root element but found <$root>")
            }
            xml.forEachChild { name ->
                if (name == "Activities") readActivityContainer(xml, activities) else xml.skipElement()
            }
        } catch (e: XMLStreamException) {
            throw e.toFormatException()
        } finally {
            xml.close()
        }
        return TcxDocument(activities)
    }

    /** Collects `Activity` elements below `Activities` or a `MultiSportSession` subtree. */
    private fun readActivityContainer(xml: XMLStreamReader, into: MutableList<TcxActivity>) {
        xml.forEachChild { name ->
            when (name) {
                "Activity" -> into += readActivity(xml)
                "MultiSportSession", "FirstSport", "NextSport" -> readActivityContainer(xml, into)
                else -> xml.skipElement()
            }
        }
    }

    private fun readActivity(xml: XMLStreamReader): TcxActivity {
        val line = xml.location.lineNumber
        val sport = xml.attribute("Sport")?.trim().orEmpty().ifEmpty { TcxSport.OTHER }
        var id: Instant? = null
        var notes: String? = null
        var creator: String? = null
        val laps = mutableListOf<LapState>()
        xml.forEachChild { name ->
            when (name) {
                "Id" -> id = xml.readText().toInstantOrNull()
                "Lap" -> laps += readLap(xml)
                "Notes" -> notes = xml.readText()
                "Creator" -> creator = readCreatorName(xml)
                else -> xml.skipElement()
            }
        }
        val activityId = id
            ?: laps.firstNotNullOfOrNull { it.startTime ?: it.firstPointTime() }
            ?: throw TrackFileFormatException("Activity has no Id and no lap start time", line, 1)
        return TcxActivity(sport, activityId, laps.map { it.build(activityId) }, notes, creator)
    }

    private fun readCreatorName(xml: XMLStreamReader): String? {
        var name: String? = null
        xml.forEachChild { child -> if (child == "Name") name = xml.readText() else xml.skipElement() }
        return name
    }

    private fun readLap(xml: XMLStreamReader): LapState {
        val lap = LapState(startTime = xml.attribute("StartTime").toInstantOrNull())
        xml.forEachChild { name ->
            when (name) {
                "TotalTimeSeconds" -> lap.totalTimeSeconds = xml.readText().toFiniteDoubleOrNull()
                "DistanceMeters" -> lap.distanceMeters = xml.readText().toFiniteDoubleOrNull()
                "MaximumSpeed" -> lap.maximumSpeed = xml.readText().toFiniteDoubleOrNull()
                "Calories" -> lap.calories = xml.readText().toRoundedIntOrNull()
                "AverageHeartRateBpm" -> lap.averageHeartRate = readValueChild(xml)
                "MaximumHeartRateBpm" -> lap.maximumHeartRate = readValueChild(xml)
                "Intensity" -> lap.intensity = TcxIntensity.parse(xml.readText())
                "Cadence" -> lap.cadence = xml.readText().toRoundedIntOrNull()
                "TriggerMethod" -> lap.triggerMethod = TcxTriggerMethod.parse(xml.readText())
                "Track" -> lap.tracks += readTrack(xml)
                "Notes" -> lap.notes = xml.readText()
                "Extensions" -> readLapExtensions(xml, lap)
                else -> xml.skipElement()
            }
        }
        return lap
    }

    private fun readLapExtensions(xml: XMLStreamReader, lap: LapState) {
        xml.forEachChild { name ->
            when (name) {
                "AvgSpeed" -> lap.averageSpeed = xml.readText().toFiniteDoubleOrNull()
                "AvgRunCadence" -> lap.cadence = lap.cadence ?: xml.readText().toRoundedIntOrNull()
                "AvgWatts" -> lap.averageWatts = xml.readText().toRoundedIntOrNull()
                "MaxWatts" -> lap.maximumWatts = xml.readText().toRoundedIntOrNull()
                else -> readLapExtensions(xml, lap) // Descend through LX and unknown containers.
            }
        }
    }

    private fun readTrack(xml: XMLStreamReader): TrackSegment {
        val points = mutableListOf<TrackPoint>()
        xml.forEachChild { name -> if (name == "Trackpoint") points += readTrackpoint(xml) else xml.skipElement() }
        return TrackSegment(points)
    }

    private fun readTrackpoint(xml: XMLStreamReader): TrackPoint {
        val point = PointBuilder()
        xml.forEachChild { name ->
            when (name) {
                "Time" -> point.time = xml.readText().toInstantOrNull()
                "Position" -> xml.forEachChild { child ->
                    when (child) {
                        "LatitudeDegrees" -> point.latitude = xml.readText().toFiniteDoubleOrNull()
                        "LongitudeDegrees" -> point.longitude = xml.readText().toFiniteDoubleOrNull()
                        else -> xml.skipElement()
                    }
                }
                "AltitudeMeters" -> point.elevation = xml.readText().toFiniteDoubleOrNull()
                "DistanceMeters" -> point.distance = xml.readText().toFiniteDoubleOrNull()
                "HeartRateBpm" -> point.heartRate = readValueChild(xml)
                "Cadence" -> point.cadence = xml.readText().toRoundedIntOrNull()
                "Extensions" -> readTrackpointExtensions(xml, point)
                else -> xml.skipElement()
            }
        }
        return point.build()
    }

    private fun readTrackpointExtensions(xml: XMLStreamReader, point: PointBuilder) {
        xml.forEachChild { name ->
            when (name) {
                "Speed" -> point.speed = xml.readText().toFiniteDoubleOrNull()
                "Watts" -> point.power = xml.readText().toRoundedIntOrNull()
                "RunCadence" -> point.cadence = point.cadence ?: xml.readText().toRoundedIntOrNull()
                else -> readTrackpointExtensions(xml, point) // Descend through TPX and unknown containers.
            }
        }
    }

    /** Reads `<X><Value>n</Value></X>` as used by the heart-rate elements. */
    private fun readValueChild(xml: XMLStreamReader): Int? {
        var value: Int? = null
        xml.forEachChild { name -> if (name == "Value") value = xml.readText().toRoundedIntOrNull() else xml.skipElement() }
        return value
    }

    private class LapState(val startTime: Instant?) {
        var totalTimeSeconds: Double? = null
        var distanceMeters: Double? = null
        var maximumSpeed: Double? = null
        var calories: Int? = null
        var averageHeartRate: Int? = null
        var maximumHeartRate: Int? = null
        var intensity = TcxIntensity.ACTIVE
        var cadence: Int? = null
        var triggerMethod = TcxTriggerMethod.MANUAL
        var averageSpeed: Double? = null
        var averageWatts: Int? = null
        var maximumWatts: Int? = null
        var notes: String? = null
        val tracks = mutableListOf<TrackSegment>()

        fun firstPointTime(): Instant? = tracks.firstNotNullOfOrNull { t -> t.points.firstNotNullOfOrNull { it.time } }

        private fun lastPointTime(): Instant? = tracks.asReversed()
            .firstNotNullOfOrNull { t -> t.points.asReversed().firstNotNullOfOrNull { it.time } }

        fun build(activityId: Instant): TcxLap {
            val start = startTime ?: firstPointTime() ?: activityId
            val derivedSeconds = lastPointTime()?.let { Duration.between(start, it).toMillis() / 1000.0 } ?: 0.0
            val derivedDistance = tracks.asReversed()
                .firstNotNullOfOrNull { t -> t.points.asReversed().firstNotNullOfOrNull { it.distance } } ?: 0.0
            return TcxLap(
                startTime = start,
                totalTimeSeconds = totalTimeSeconds ?: derivedSeconds.coerceAtLeast(0.0),
                distanceMeters = distanceMeters ?: derivedDistance,
                maximumSpeed = maximumSpeed,
                calories = calories,
                averageHeartRate = averageHeartRate,
                maximumHeartRate = maximumHeartRate,
                intensity = intensity,
                cadence = cadence,
                triggerMethod = triggerMethod,
                averageSpeed = averageSpeed,
                averageWatts = averageWatts,
                maximumWatts = maximumWatts,
                tracks = tracks.toList(),
                notes = notes,
            )
        }
    }
}
