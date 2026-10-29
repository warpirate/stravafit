// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.geojson

import app.stravafit.lib.geojson.internal.JsonSerializer
import app.stravafit.lib.geojson.internal.RawNumber
import app.stravafit.lib.geojson.internal.formatNumber
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Options for [GeoJsonWriter].
 *
 * @property coordinateDecimals Fraction digits kept for longitude/latitude, or `null` for full
 *   precision. RFC 7946 §11.2 notes 6 decimals (~10 cm) is plenty and reduces size.
 * @property altitudeDecimals Fraction digits kept for altitude, or `null` for full precision.
 * @property includeAltitude Write the third coordinate when a position has one.
 * @property includeBbox Add a 2-D `bbox` member to the top-level object (and to each feature of a
 *   collection) computed from its positions.
 * @property prettyPrint Indent with two spaces; positions stay on one line.
 */
public data class GeoJsonWriteOptions(
    val coordinateDecimals: Int? = 6,
    val altitudeDecimals: Int? = 1,
    val includeAltitude: Boolean = true,
    val includeBbox: Boolean = false,
    val prettyPrint: Boolean = false,
) {
    init {
        require(coordinateDecimals == null || coordinateDecimals in 0..15) { "coordinateDecimals must be in 0..15" }
        require(altitudeDecimals == null || altitudeDecimals in 0..15) { "altitudeDecimals must be in 0..15" }
    }
}

/**
 * Writes RFC 7946 GeoJSON. Positions are emitted as `[longitude, latitude(, altitude)]`, members in
 * a fixed order (`type`, `id`, `bbox`, `geometry`/`coordinates`, `properties`), and no `crs`
 * member, so output is deterministic. The writer does not apply privacy trimming.
 */
public class GeoJsonWriter(private val options: GeoJsonWriteOptions = GeoJsonWriteOptions()) {

    /** Returns [value] as GeoJSON text. */
    public fun write(value: GeoJsonObject): String = JsonSerializer(options.prettyPrint).serialize(toTree(value, withBbox = true))

    /** Writes [value] as UTF-8 GeoJSON to [output]. The stream is flushed but not closed. */
    public fun write(value: GeoJsonObject, output: OutputStream) {
        output.write(write(value).toByteArray(Charsets.UTF_8))
        output.flush()
    }

    /** Builds the JSON tree; a bbox is added only when [withBbox] and the option are both set. */
    private fun toTree(value: GeoJsonObject, withBbox: Boolean): Map<String, Any?> = when (value) {
        is FeatureCollection -> linkedMapOf<String, Any?>("type" to "FeatureCollection").apply {
            if (withBbox) putBbox(value.features.asSequence().mapNotNull { it.geometry }.flatMap { it.positions })
            put("features", value.features.map { toTree(it, withBbox = true) })
        }
        is Feature -> linkedMapOf<String, Any?>("type" to "Feature").apply {
            value.id?.let { put("id", it) }
            if (withBbox) putBbox(value.geometry?.positions.orEmpty())
            put("geometry", value.geometry?.let(::geometryTree))
            put("properties", value.properties?.let(::checkedProperties))
        }
        is Geometry -> {
            val tree = geometryTree(value)
            if (withBbox && options.includeBbox) {
                linkedMapOf<String, Any?>("type" to tree["type"]).apply {
                    putBbox(value.positions)
                    putAll(tree - "type")
                }
            } else {
                tree
            }
        }
    }

    private fun geometryTree(geometry: Geometry): Map<String, Any?> = when (geometry) {
        is Point -> coordinates("Point", position(geometry.coordinates))
        is MultiPoint -> coordinates("MultiPoint", geometry.coordinates.map(::position))
        is LineString -> coordinates("LineString", geometry.coordinates.map(::position))
        is MultiLineString -> coordinates("MultiLineString", geometry.coordinates.map { it.map(::position) })
        is Polygon -> coordinates("Polygon", geometry.coordinates.map { it.map(::position) })
        is MultiPolygon -> coordinates("MultiPolygon", geometry.coordinates.map { p -> p.map { it.map(::position) } })
        is GeometryCollection -> linkedMapOf(
            "type" to "GeometryCollection",
            "geometries" to geometry.geometries.map(::geometryTree),
        )
    }

    private fun coordinates(type: String, coordinates: Any): Map<String, Any?> =
        linkedMapOf("type" to type, "coordinates" to coordinates)

    private fun position(p: Position): List<RawNumber> = buildList {
        add(number(p.longitude, options.coordinateDecimals))
        add(number(p.latitude, options.coordinateDecimals))
        if (options.includeAltitude && p.altitude != null) add(number(p.altitude, options.altitudeDecimals))
    }

    private fun number(value: Double, decimals: Int?): RawNumber =
        if (decimals == null) {
            RawNumber(formatNumber(value))
        } else {
            RawNumber(formatNumber(BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)))
        }

    private fun MutableMap<String, Any?>.putBbox(positions: Sequence<Position>) {
        if (!options.includeBbox) return
        var minLon = Double.POSITIVE_INFINITY
        var minLat = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        for (p in positions) {
            minLon = minOf(minLon, p.longitude)
            minLat = minOf(minLat, p.latitude)
            maxLon = maxOf(maxLon, p.longitude)
            maxLat = maxOf(maxLat, p.latitude)
        }
        if (minLon.isFinite()) {
            put("bbox", listOf(minLon, minLat, maxLon, maxLat).map { number(it, options.coordinateDecimals) })
        }
    }

    /** Validates that property values are JSON-representable; fails fast with the offending path. */
    private fun checkedProperties(properties: Map<String, Any?>): Map<String, Any?> {
        fun check(value: Any?, path: String) {
            when (value) {
                null, is String, is Boolean -> Unit
                is Number -> require(!(value is Double && !value.isFinite()) && !(value is Float && !value.isFinite())) {
                    "Property $path is not finite"
                }
                is List<*> -> value.forEachIndexed { i, v -> check(v, "$path[$i]") }
                is Map<*, *> -> value.forEach { (k, v) ->
                    require(k is String) { "Property $path has a non-string key" }
                    check(v, "$path.$k")
                }
                else -> throw IllegalArgumentException("Property $path has unsupported type ${value::class.qualifiedName}")
            }
        }
        properties.forEach { (k, v) -> check(v, k) }
        return properties
    }
}
