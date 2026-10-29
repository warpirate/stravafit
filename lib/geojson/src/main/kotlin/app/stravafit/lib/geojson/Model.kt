// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.geojson

/** Any RFC 7946 GeoJSON object: a [Geometry], a [Feature] or a [FeatureCollection]. */
public sealed interface GeoJsonObject

/**
 * A GeoJSON position. RFC 7946 orders coordinates **longitude, latitude[, altitude]** (WGS-84
 * degrees, altitude in metres); use [fromLatLon] to avoid swapping them by accident.
 */
public data class Position(val longitude: Double, val latitude: Double, val altitude: Double? = null) {
    init {
        require(longitude.isFinite() && latitude.isFinite()) { "Coordinates must be finite: $longitude, $latitude" }
        require(altitude == null || altitude.isFinite()) { "Altitude must be finite: $altitude" }
    }

    public companion object {
        /** Creates a position from latitude-first arguments, the order most location APIs use. */
        public fun fromLatLon(latitude: Double, longitude: Double, altitude: Double? = null): Position =
            Position(longitude, latitude, altitude)
    }
}

/** A GeoJSON geometry object. */
public sealed interface Geometry : GeoJsonObject {
    /** All positions in this geometry, depth-first. */
    public val positions: Sequence<Position>
}

/** A single position. */
public data class Point(val coordinates: Position) : Geometry {
    override val positions: Sequence<Position> get() = sequenceOf(coordinates)
}

/** An unconnected set of positions. */
public data class MultiPoint(val coordinates: List<Position>) : Geometry {
    override val positions: Sequence<Position> get() = coordinates.asSequence()
}

/** A path of two or more positions, e.g. an activity track. */
public data class LineString(val coordinates: List<Position>) : Geometry {
    init {
        require(coordinates.size >= 2) { "A LineString needs at least 2 positions, got ${coordinates.size}" }
    }

    override val positions: Sequence<Position> get() = coordinates.asSequence()
}

/** Several paths, e.g. a track split by pauses or by privacy trimming. */
public data class MultiLineString(val coordinates: List<List<Position>>) : Geometry {
    init {
        coordinates.forEach { require(it.size >= 2) { "Each line needs at least 2 positions, got ${it.size}" } }
    }

    override val positions: Sequence<Position> get() = coordinates.asSequence().flatten()
}

/**
 * A polygon: an exterior ring followed by optional holes. Each ring is closed (first position equals
 * last) and has at least four positions. RFC 7946 recommends counter-clockwise exterior rings; this
 * class does not re-orient rings.
 */
public data class Polygon(val coordinates: List<List<Position>>) : Geometry {
    init {
        coordinates.forEach(::requireLinearRing)
    }

    override val positions: Sequence<Position> get() = coordinates.asSequence().flatten()
}

/** Several polygons. */
public data class MultiPolygon(val coordinates: List<List<List<Position>>>) : Geometry {
    init {
        coordinates.forEach { polygon -> polygon.forEach(::requireLinearRing) }
    }

    override val positions: Sequence<Position> get() = coordinates.asSequence().flatten().flatten()
}

/** A heterogeneous collection of geometries. */
public data class GeometryCollection(val geometries: List<Geometry>) : Geometry {
    override val positions: Sequence<Position> get() = geometries.asSequence().flatMap { it.positions }
}

/**
 * A spatially bounded thing.
 *
 * @property geometry The geometry, or `null` for an unlocated feature.
 * @property properties JSON-compatible values: `String`, `Number`, `Boolean`, `null`, `List` and
 *   `Map<String, *>` (nested freely). `null` is written as `"properties": null`.
 * @property id Optional identifier; must be a `String` or a `Number`.
 */
public data class Feature(
    val geometry: Geometry?,
    val properties: Map<String, Any?>? = emptyMap(),
    val id: Any? = null,
) : GeoJsonObject {
    init {
        require(id == null || id is String || id is Number) { "Feature id must be a String or Number" }
    }
}

/** A list of [features]. */
public data class FeatureCollection(val features: List<Feature>) : GeoJsonObject

private fun requireLinearRing(ring: List<Position>) {
    require(ring.size >= 4) { "A linear ring needs at least 4 positions, got ${ring.size}" }
    require(ring.first() == ring.last()) { "A linear ring must be closed (first position == last)" }
}
