// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx

import app.stravafit.lib.gpxtcx.internal.XmlOutput
import app.stravafit.lib.gpxtcx.internal.formatDecimal
import app.stravafit.lib.gpxtcx.internal.formatTime
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter

/** Which Garmin TrackPointExtension schema the [GpxWriter] uses for sensor values. */
public enum class TrackPointExtensionVersion(internal val namespace: String) {
    /** v1: `atemp`, `hr`, `cad`. The most widely read variant. */
    V1("http://www.garmin.com/xmlschemas/TrackPointExtension/v1"),

    /** v2: v1 plus `speed` (m/s). */
    V2("http://www.garmin.com/xmlschemas/TrackPointExtension/v2"),
}

/** How the [GpxWriter] writes per-point power, which GPX 1.1 has no core element for. */
public enum class GpxPowerFormat {
    /**
     * Garmin PowerExtension v1 `<pwr:PowerInWatts>`: valid against the GPX 1.1 XSD (whose
     * `<extensions>` only admits elements from other namespaces) and read by Garmin-compatible tools.
     */
    GARMIN_POWER_EXTENSION,

    /**
     * A bare `<power>` element in the GPX namespace: the de-facto convention many importers read,
     * but it makes the file invalid against the GPX 1.1 XSD.
     */
    PLAIN,

    /** Power is not written. */
    NONE,
}

/**
 * Options for [GpxWriter].
 *
 * @property creator Fallback for the mandatory `creator` attribute when [GpxDocument.creator] is null.
 * @property extensionVersion TrackPointExtension schema for heart rate, cadence and temperature.
 * @property powerFormat How power is written; the default keeps the file XSD-valid.
 * @property coordinateDecimals Fraction digits for latitude/longitude; 7 is about 1 cm.
 * @property elevationDecimals Fraction digits for elevation.
 */
public data class GpxWriteOptions(
    val creator: String = "lib-gpx-tcx",
    val extensionVersion: TrackPointExtensionVersion = TrackPointExtensionVersion.V1,
    val powerFormat: GpxPowerFormat = GpxPowerFormat.GARMIN_POWER_EXTENSION,
    val coordinateDecimals: Int = 7,
    val elevationDecimals: Int = 1,
) {
    init {
        require(coordinateDecimals in 0..12) { "coordinateDecimals must be in 0..12" }
        require(elevationDecimals in 0..6) { "elevationDecimals must be in 0..6" }
    }
}

/**
 * Writes GPX 1.1 documents, UTF-8 encoded with deterministic formatting.
 *
 * Points without a position are skipped, as GPX requires coordinates. The writer does not apply
 * privacy trimming: callers must pass already-trimmed tracks for anything that leaves the device.
 */
public class GpxWriter(private val options: GpxWriteOptions = GpxWriteOptions()) {

    /** Writes [document] to [output]. The stream is flushed but not closed. */
    public fun write(document: GpxDocument, output: OutputStream) {
        val writer = OutputStreamWriter(output, Charsets.UTF_8)
        val xml = XmlOutput(writer)
        xml.declaration()
        xml.element(
            "gpx",
            "version" to "1.1",
            "creator" to (document.creator ?: options.creator),
            "xmlns" to GPX_NAMESPACE,
            "xmlns:xsi" to XSI_NAMESPACE,
            "xmlns:gpxtpx" to options.extensionVersion.namespace,
            "xmlns:pwr" to POWER_NAMESPACE.takeIf { options.powerFormat == GpxPowerFormat.GARMIN_POWER_EXTENSION },
            "xsi:schemaLocation" to "$GPX_NAMESPACE $GPX_SCHEMA",
        ) {
            writeMetadata(xml, document.metadata)
            document.routes.forEach { writeRoute(xml, it) }
            document.tracks.forEach { writeTrack(xml, it) }
        }
        writer.flush()
    }

    /** Returns [document] as a GPX string. */
    public fun writeToString(document: GpxDocument): String {
        val out = ByteArrayOutputStream()
        write(document, out)
        return out.toString(Charsets.UTF_8)
    }

    private fun writeMetadata(xml: XmlOutput, metadata: GpxMetadata) {
        if (metadata == GpxMetadata()) return
        xml.element("metadata") {
            xml.leaf("name", metadata.name)
            xml.leaf("desc", metadata.description)
            xml.leaf("time", metadata.time?.let(::formatTime))
        }
    }

    private fun writeRoute(xml: XmlOutput, route: GpxRoute) {
        xml.element("rte") {
            xml.leaf("name", route.name)
            xml.leaf("desc", route.description)
            route.points.filter { it.hasPosition }.forEach { writePoint(xml, "rtept", it) }
        }
    }

    private fun writeTrack(xml: XmlOutput, track: GpxTrack) {
        xml.element("trk") {
            xml.leaf("name", track.name)
            xml.leaf("desc", track.description)
            xml.leaf("type", track.type)
            for (segment in track.segments) {
                xml.element("trkseg") {
                    segment.points.filter { it.hasPosition }.forEach { writePoint(xml, "trkpt", it) }
                }
            }
        }
    }

    private fun writePoint(xml: XmlOutput, tag: String, point: TrackPoint) {
        xml.element(
            tag,
            "lat" to formatDecimal(point.latitude!!, options.coordinateDecimals),
            "lon" to formatDecimal(point.longitude!!, options.coordinateDecimals),
        ) {
            xml.leaf("ele", point.elevation?.let { formatDecimal(it, options.elevationDecimals) })
            xml.leaf("time", point.time?.let(::formatTime))
            writeExtensions(xml, point)
        }
    }

    private fun writeExtensions(xml: XmlOutput, point: TrackPoint) {
        val speed = point.speed.takeIf { options.extensionVersion == TrackPointExtensionVersion.V2 }
        val hasTpx = point.temperature != null || point.heartRate != null || point.cadence != null || speed != null
        val power = point.power?.takeIf { options.powerFormat != GpxPowerFormat.NONE }
        if (!hasTpx && power == null) return
        xml.element("extensions") {
            if (power != null && options.powerFormat == GpxPowerFormat.PLAIN) xml.leaf("power", power.toString())
            if (hasTpx) {
                xml.element("gpxtpx:TrackPointExtension") {
                    // Element order follows the TrackPointExtension XSD sequence.
                    xml.leaf("gpxtpx:atemp", point.temperature?.let { formatDecimal(it, 1) })
                    xml.leaf("gpxtpx:hr", point.heartRate?.coerceIn(0, 255)?.toString())
                    xml.leaf("gpxtpx:cad", point.cadence?.coerceIn(0, 254)?.toString())
                    xml.leaf("gpxtpx:speed", speed?.let { formatDecimal(it, 3) })
                }
            }
            if (power != null && options.powerFormat == GpxPowerFormat.GARMIN_POWER_EXTENSION) {
                // PowerInWatts is an xsd:unsignedShort.
                xml.leaf("pwr:PowerInWatts", power.coerceIn(0, 65535).toString())
            }
        }
    }

    private companion object {
        const val GPX_NAMESPACE = "http://www.topografix.com/GPX/1/1"
        const val GPX_SCHEMA = "http://www.topografix.com/GPX/1/1/gpx.xsd"
        const val XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance"
        const val POWER_NAMESPACE = "http://www.garmin.com/xmlschemas/PowerExtension/v1"
    }
}
