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
import app.stravafit.lib.gpxtcx.internal.XmlSyntaxException
import app.stravafit.lib.gpxtcx.internal.XmlPullReader

/**
 * Streaming, tolerant GPX reader (GPX 1.1 and 1.0).
 *
 * Elements are matched by local name, so any namespace prefix or version works. Sensor values are
 * taken from any element nested in a point's `<extensions>`, covering the Garmin
 * TrackPointExtension v1/v2 (`hr`, `cad`, `atemp`, `speed`), Cluetrust (`hr`, `cadence`, `temp`,
 * `distance`), Garmin PowerExtension (`PowerInWatts`) and generic tags (`heartrate`, `power`, `watts`).
 *
 * Tolerance rules: points with a missing or out-of-range position are skipped; unparseable times
 * and numbers become `null`; a byte-order mark or whitespace before the XML declaration is ignored.
 * DTDs and external entities are rejected.
 *
 * @param recoverPartial when `true`, a file that breaks off part-way (typically a recorder crash
 *   leaving it unterminated) yields every point read before the XML error, with
 *   [GpxDocument.truncated] set, instead of an exception. A file that fails before its first valid
 *   point still throws.
 */
public class GpxReader(private val recoverPartial: Boolean = false) {

    /**
     * Reads a GPX document from [input]. The stream is not closed.
     *
     * @throws TrackFileFormatException if the input is not well-formed XML or its root is not `<gpx>`.
     */
    public fun read(input: InputStream): GpxDocument {
        val xml = openXml(input)
        val state = DocumentState()
        try {
            val root = xml.moveToRoot()
            if (root != "gpx") throw TrackFileFormatException("Expected <gpx> root element but found <$root>")
            state.creator = xml.attribute("creator")
            xml.forEachChild { name ->
                when (name) {
                    "metadata" -> state.metadata = readMetadata(xml)
                    "trk" -> readTrack(xml, state)
                    "rte" -> readRoute(xml, state)
                    else -> xml.skipElement()
                }
            }
        } catch (e: XmlSyntaxException) {
            if (recoverPartial && state.hasPoints()) return state.build(truncated = true)
            throw e.toFormatException()
        } finally {
            xml.close()
        }
        return state.build(truncated = false)
    }

    private fun readMetadata(xml: XmlPullReader): GpxMetadata {
        var metadata = GpxMetadata()
        xml.forEachChild { name ->
            when (name) {
                "name" -> metadata = metadata.copy(name = xml.readText())
                "desc" -> metadata = metadata.copy(description = xml.readText())
                "time" -> metadata = metadata.copy(time = xml.readText().toInstantOrNull())
                else -> xml.skipElement()
            }
        }
        return metadata
    }

    private fun readTrack(xml: XmlPullReader, state: DocumentState) {
        val track = TrackState().also { state.tracks += it }
        xml.forEachChild { name ->
            when (name) {
                "name" -> track.name = xml.readText()
                "desc" -> track.description = xml.readText()
                "type" -> track.type = xml.readText()
                "trkseg" -> {
                    val points = mutableListOf<TrackPoint>().also { track.segments += it }
                    xml.forEachChild { child ->
                        if (child == "trkpt") readPoint(xml)?.let(points::add) else xml.skipElement()
                    }
                }
                else -> xml.skipElement()
            }
        }
    }

    private fun readRoute(xml: XmlPullReader, state: DocumentState) {
        val route = RouteState().also { state.routes += it }
        xml.forEachChild { name ->
            when (name) {
                "name" -> route.name = xml.readText()
                "desc" -> route.description = xml.readText()
                "rtept" -> readPoint(xml)?.let(route.points::add)
                else -> xml.skipElement()
            }
        }
    }

    /** Reads a `trkpt`/`rtept`; returns `null` when it has no valid position. */
    private fun readPoint(xml: XmlPullReader): TrackPoint? {
        val point = PointBuilder().apply {
            latitude = xml.attribute("lat").toFiniteDoubleOrNull()
            longitude = xml.attribute("lon").toFiniteDoubleOrNull()
        }
        xml.forEachChild { name ->
            when (name) {
                "ele" -> point.elevation = xml.readText().toFiniteDoubleOrNull()
                "time" -> point.time = xml.readText().toInstantOrNull()
                "speed" -> point.speed = xml.readText().toFiniteDoubleOrNull() // GPX 1.0 core element
                "extensions" -> readExtensions(xml, point)
                else -> xml.skipElement()
            }
        }
        return if (point.hasValidPosition) point.build() else null
    }

    /** Walks every element below `<extensions>`, picking sensor values by (case-insensitive) local name. */
    private fun readExtensions(xml: XmlPullReader, point: PointBuilder) {
        xml.forEachChild { name ->
            when (name.lowercase()) {
                "hr", "heartrate", "heart_rate" -> point.heartRate = xml.readText().toRoundedIntOrNull()
                "cad", "cadence", "runcadence" -> point.cadence = xml.readText().toRoundedIntOrNull()
                "power", "watts", "powerinwatts" -> point.power = xml.readText().toRoundedIntOrNull()
                "atemp", "temp", "temperature" -> point.temperature = xml.readText().toFiniteDoubleOrNull()
                "speed" -> point.speed = xml.readText().toFiniteDoubleOrNull()
                "distance" -> point.distance = xml.readText().toFiniteDoubleOrNull()
                else -> readExtensions(xml, point) // Container such as TrackPointExtension: descend.
            }
        }
    }

    private class DocumentState {
        var creator: String? = null
        var metadata = GpxMetadata()
        val tracks = mutableListOf<TrackState>()
        val routes = mutableListOf<RouteState>()

        fun hasPoints() = tracks.any { t -> t.segments.any { it.isNotEmpty() } } || routes.any { it.points.isNotEmpty() }

        fun build(truncated: Boolean) = GpxDocument(
            creator = creator,
            metadata = metadata,
            tracks = tracks.map { t ->
                GpxTrack(t.name, t.description, t.type, t.segments.map { TrackSegment(it.toList()) })
            },
            routes = routes.map { GpxRoute(it.name, it.description, it.points.toList()) },
            truncated = truncated,
        )
    }

    private class TrackState {
        var name: String? = null
        var description: String? = null
        var type: String? = null
        val segments = mutableListOf<MutableList<TrackPoint>>()
    }

    private class RouteState {
        var name: String? = null
        var description: String? = null
        val points = mutableListOf<TrackPoint>()
    }
}
