// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.geojson

import app.stravafit.lib.geojson.internal.JsonParser
import java.io.InputStream

/**
 * Reads RFC 7946 GeoJSON into the [GeoJsonObject] model.
 *
 * Positions keep at most three values (extra elements are ignored, as RFC 7946 §3.1.1 allows);
 * `bbox`, `crs` and foreign members are ignored. Property values come back as `String`, `Long`,
 * `Double`, `Boolean`, `null`, `List` and `Map`. Structural errors (missing `type`, bad
 * coordinates, unclosed rings, too-short lines) raise [GeoJsonFormatException].
 */
public class GeoJsonReader {

    /** Parses [text]. A leading byte-order mark is ignored. */
    public fun read(text: String): GeoJsonObject {
        val root = JsonParser(text.removePrefix("\uFEFF")).parse()
        return objectFrom(root.asObject("root"), "root")
    }

    /** Parses UTF-8 GeoJSON from [input]. The stream is not closed. */
    public fun read(input: InputStream): GeoJsonObject = read(input.readBytes().toString(Charsets.UTF_8))

    private fun objectFrom(json: Map<String, Any?>, path: String): GeoJsonObject =
        when (val type = json["type"]) {
            "FeatureCollection" -> FeatureCollection(
                json.member("features", path).asArray("$path.features").mapIndexed { i, f ->
                    feature(f.asObject("$path.features[$i]"), "$path.features[$i]")
                },
            )
            "Feature" -> feature(json, path)
            is String -> geometry(json, path)
            else -> fail(path, "missing or non-string \"type\"")
        }

    private fun feature(json: Map<String, Any?>, path: String): Feature {
        if (json["type"] != "Feature") fail(path, "expected a Feature")
        val geometry = json["geometry"]?.let { geometry(it.asObject("$path.geometry"), "$path.geometry") }
        val properties = json["properties"]?.asObject("$path.properties")
        val id = json["id"]
        if (id != null && id !is String && id !is Number) fail(path, "\"id\" must be a string or number")
        return Feature(geometry, properties, id)
    }

    private fun geometry(json: Map<String, Any?>, path: String): Geometry = guard(path) {
        val type = json["type"]
        if (type == "GeometryCollection") {
            return@guard GeometryCollection(
                json.member("geometries", path).asArray("$path.geometries").mapIndexed { i, g ->
                    geometry(g.asObject("$path.geometries[$i]"), "$path.geometries[$i]")
                },
            )
        }
        val coords = json.member("coordinates", path)
        val at = "$path.coordinates"
        when (type) {
            "Point" -> Point(position(coords, at))
            "MultiPoint" -> MultiPoint(positions(coords, at))
            "LineString" -> LineString(positions(coords, at))
            "MultiLineString" -> MultiLineString(nested(coords, at, ::positions))
            "Polygon" -> Polygon(nested(coords, at, ::positions))
            "MultiPolygon" -> MultiPolygon(nested(coords, at) { p, pp -> nested(p, pp, ::positions) })
            else -> fail(path, "unsupported geometry type \"$type\"")
        }
    }

    private fun position(value: Any?, path: String): Position {
        val list = value.asArray(path)
        if (list.size < 2) fail(path, "a position needs at least 2 numbers")
        val numbers = list.take(3).mapIndexed { i, n -> (n as? Number)?.toDouble() ?: fail("$path[$i]", "not a number") }
        return Position(numbers[0], numbers[1], numbers.getOrNull(2))
    }

    private fun positions(value: Any?, path: String): List<Position> =
        value.asArray(path).mapIndexed { i, p -> position(p, "$path[$i]") }

    private fun <T> nested(value: Any?, path: String, item: (Any?, String) -> T): List<T> =
        value.asArray(path).mapIndexed { i, v -> item(v, "$path[$i]") }

    /** Converts model validation failures (e.g. an unclosed ring) into format errors with a path. */
    private inline fun <T> guard(path: String, block: () -> T): T =
        try {
            block()
        } catch (e: IllegalArgumentException) {
            throw GeoJsonFormatException("$path: ${e.message}", e)
        }

    private fun Map<String, Any?>.member(name: String, path: String): Any? =
        if (containsKey(name)) get(name) else fail(path, "missing \"$name\"")

    // The parser only produces String-keyed maps, so the cast is safe.
    @Suppress("UNCHECKED_CAST")
    private fun Any?.asObject(path: String): Map<String, Any?> =
        this as? Map<String, Any?> ?: fail(path, "expected an object")

    private fun Any?.asArray(path: String): List<*> = this as? List<*> ?: fail(path, "expected an array")

    private fun fail(path: String, message: String): Nothing = throw GeoJsonFormatException("$path: $message")
}
