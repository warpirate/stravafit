// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx

import app.stravafit.lib.gpxtcx.internal.XmlOutput
import app.stravafit.lib.gpxtcx.internal.formatDecimal
import app.stravafit.lib.gpxtcx.internal.formatTime
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Options for [TcxWriter].
 *
 * @property coordinateDecimals Fraction digits for latitude/longitude.
 */
public data class TcxWriteOptions(val coordinateDecimals: Int = 7) {
    init {
        require(coordinateDecimals in 0..12) { "coordinateDecimals must be in 0..12" }
    }
}

/**
 * Writes TCX (Training Center Database v2) activity files that validate against the Garmin schema,
 * with deterministic formatting.
 *
 * Mapping notes: the `Sport` attribute is coerced to Running/Biking/Other; for running, cadence is
 * written as `ns3:RunCadence`/`ns3:AvgRunCadence` extensions as devices do, otherwise as `<Cadence>`;
 * power goes to `ns3:TPX/Watts` and `ns3:LX/AvgWatts`/`MaxWatts`; TCX has no temperature field,
 * so temperature is dropped. Missing lap calories are written as 0 because the schema requires them.
 * Every activity needs at least one lap and every trackpoint a time; otherwise [write] throws
 * [IllegalArgumentException].
 */
public class TcxWriter(private val options: TcxWriteOptions = TcxWriteOptions()) {

    /** Writes [document] to [output]. The stream is flushed but not closed. */
    public fun write(document: TcxDocument, output: OutputStream) {
        val writer = OutputStreamWriter(output, Charsets.UTF_8)
        val xml = XmlOutput(writer)
        xml.declaration()
        xml.element(
            "TrainingCenterDatabase",
            "xmlns" to TCX_NAMESPACE,
            "xmlns:ns3" to EXT_NAMESPACE,
            "xmlns:xsi" to XSI_NAMESPACE,
            "xsi:schemaLocation" to "$TCX_NAMESPACE $TCX_SCHEMA",
        ) {
            xml.element("Activities") {
                document.activities.forEach { writeActivity(xml, it) }
            }
        }
        writer.flush()
    }

    /** Returns [document] as a TCX string. */
    public fun writeToString(document: TcxDocument): String {
        val out = ByteArrayOutputStream()
        write(document, out)
        return out.toString(Charsets.UTF_8)
    }

    private fun writeActivity(xml: XmlOutput, activity: TcxActivity) {
        require(activity.laps.isNotEmpty()) { "A TCX activity needs at least one lap" }
        val sport = schemaSport(activity.sport)
        val running = sport == TcxSport.RUNNING
        xml.element("Activity", "Sport" to sport) {
            xml.leaf("Id", formatTime(activity.id))
            activity.laps.forEach { writeLap(xml, it, running) }
            xml.leaf("Notes", activity.notes)
            activity.creatorName?.let { writeCreator(xml, it) }
        }
    }

    private fun writeLap(xml: XmlOutput, lap: TcxLap, running: Boolean) {
        xml.element("Lap", "StartTime" to formatTime(lap.startTime)) {
            xml.leaf("TotalTimeSeconds", formatDecimal(lap.totalTimeSeconds, 3))
            xml.leaf("DistanceMeters", formatDecimal(lap.distanceMeters, 2))
            xml.leaf("MaximumSpeed", lap.maximumSpeed?.let { formatDecimal(it, 3) })
            xml.leaf("Calories", (lap.calories ?: 0).coerceIn(0, 65535).toString())
            lap.averageHeartRate?.let { xml.element("AverageHeartRateBpm") { xml.leaf("Value", heartRate(it)) } }
            lap.maximumHeartRate?.let { xml.element("MaximumHeartRateBpm") { xml.leaf("Value", heartRate(it)) } }
            xml.leaf("Intensity", lap.intensity.xmlValue)
            if (!running) xml.leaf("Cadence", lap.cadence?.let(::cadence))
            xml.leaf("TriggerMethod", lap.triggerMethod.xmlValue)
            lap.tracks.filter { it.points.isNotEmpty() }.forEach { track ->
                xml.element("Track") { track.points.forEach { writeTrackpoint(xml, it, running) } }
            }
            xml.leaf("Notes", lap.notes)
            writeLapExtensions(xml, lap, running)
        }
    }

    private fun writeLapExtensions(xml: XmlOutput, lap: TcxLap, running: Boolean) {
        val runCadence = lap.cadence.takeIf { running }
        if (lap.averageSpeed == null && runCadence == null && lap.averageWatts == null && lap.maximumWatts == null) return
        xml.element("Extensions") {
            xml.element("ns3:LX") {
                // Element order follows the ActivityExtension v2 XSD sequence.
                xml.leaf("ns3:AvgSpeed", lap.averageSpeed?.let { formatDecimal(it, 3) })
                xml.leaf("ns3:AvgRunCadence", runCadence?.let(::cadence))
                xml.leaf("ns3:AvgWatts", lap.averageWatts?.coerceIn(0, 65535)?.toString())
                xml.leaf("ns3:MaxWatts", lap.maximumWatts?.coerceIn(0, 65535)?.toString())
            }
        }
    }

    private fun writeTrackpoint(xml: XmlOutput, point: TrackPoint, running: Boolean) {
        xml.element("Trackpoint") {
            // Time is mandatory in the schema; time-less points cannot be represented faithfully.
            xml.leaf("Time", formatTime(requireNotNull(point.time) { "TCX trackpoints require a time" }))
            if (point.hasPosition) {
                xml.element("Position") {
                    xml.leaf("LatitudeDegrees", formatDecimal(point.latitude!!, options.coordinateDecimals))
                    xml.leaf("LongitudeDegrees", formatDecimal(point.longitude!!, options.coordinateDecimals))
                }
            }
            xml.leaf("AltitudeMeters", point.elevation?.let { formatDecimal(it, 1) })
            xml.leaf("DistanceMeters", point.distance?.let { formatDecimal(it, 2) })
            point.heartRate?.let { xml.element("HeartRateBpm") { xml.leaf("Value", heartRate(it)) } }
            if (!running) xml.leaf("Cadence", point.cadence?.let(::cadence))
            val runCadence = point.cadence.takeIf { running }
            if (point.speed != null || runCadence != null || point.power != null) {
                xml.element("Extensions") {
                    xml.element("ns3:TPX") {
                        xml.leaf("ns3:Speed", point.speed?.let { formatDecimal(it, 3) })
                        xml.leaf("ns3:RunCadence", runCadence?.let(::cadence))
                        xml.leaf("ns3:Watts", point.power?.coerceIn(0, 65535)?.toString())
                    }
                }
            }
        }
    }

    private fun writeCreator(xml: XmlOutput, name: String) {
        xml.element("Creator", "xsi:type" to "Device_t") {
            xml.leaf("Name", name)
            xml.leaf("UnitId", "0")
            xml.leaf("ProductID", "0")
            xml.element("Version") {
                xml.leaf("VersionMajor", "0")
                xml.leaf("VersionMinor", "0")
            }
        }
    }

    private fun schemaSport(sport: String): String =
        listOf(TcxSport.RUNNING, TcxSport.BIKING).firstOrNull { it.equals(sport, ignoreCase = true) } ?: TcxSport.OTHER

    // HeartRateInBeatsPerMinute_t is an unsignedByte >= 1; CadenceValue_t is an unsignedByte <= 254.
    private fun heartRate(value: Int): String = value.coerceIn(1, 255).toString()

    private fun cadence(value: Int): String = value.coerceIn(0, 254).toString()

    private companion object {
        const val TCX_NAMESPACE = "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"
        const val TCX_SCHEMA = "http://www.garmin.com/xmlschemas/TrainingCenterDatabasev2.xsd"
        const val EXT_NAMESPACE = "http://www.garmin.com/xmlschemas/ActivityExtension/v2"
        const val XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance"
    }
}
