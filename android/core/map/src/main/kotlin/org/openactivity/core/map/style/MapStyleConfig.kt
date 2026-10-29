// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.style

import androidx.compose.runtime.Immutable

/**
 * Endpoints for the base maps. The defaults need no API key: OpenFreeMap vector styles for
 * [MapStyleKind.Standard] and [MapStyleKind.Dark] and OpenTopoMap raster tiles for
 * [MapStyleKind.Outdoor]. An instance operator swaps these for a self-hosted Protomaps setup.
 *
 * @property standardStyleUrl style JSON for the light map.
 * @property darkStyleUrl style JSON for the dark map.
 * @property vectorAttribution credits for the two vector styles.
 * @property outdoorTileUrls raster tile URL templates (`{z}/{x}/{y}`) for the outdoor map; several
 *   entries spread load over mirrors. Empty hides the outdoor style.
 * @property outdoorMaxZoom highest zoom the outdoor tile server renders.
 * @property outdoorAttribution credits for the outdoor tiles.
 */
@Immutable
data class MapStyleConfig(
    val standardStyleUrl: String,
    val darkStyleUrl: String,
    val vectorAttribution: List<AttributionPart>,
    val outdoorTileUrls: List<String>,
    val outdoorMaxZoom: Int,
    val outdoorAttribution: List<AttributionPart>,
) {
    companion object {
        private val Osm = AttributionPart("© OpenStreetMap contributors", "https://www.openstreetmap.org/copyright")

        /** Free, keyless defaults (OpenFreeMap and OpenTopoMap). */
        val OpenFreeMap: MapStyleConfig = MapStyleConfig(
            standardStyleUrl = "https://tiles.openfreemap.org/styles/liberty",
            darkStyleUrl = "https://tiles.openfreemap.org/styles/dark",
            vectorAttribution = listOf(
                AttributionPart("OpenFreeMap", "https://openfreemap.org"),
                AttributionPart("© OpenMapTiles", "https://www.openmaptiles.org/"),
                Osm,
            ),
            outdoorTileUrls = listOf(
                "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
                "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
                "https://c.tile.opentopomap.org/{z}/{x}/{y}.png",
            ),
            outdoorMaxZoom = 17,
            outdoorAttribution = listOf(
                Osm,
                AttributionPart("SRTM", "https://www2.jpl.nasa.gov/srtm/"),
                AttributionPart("© OpenTopoMap (CC-BY-SA)", "https://opentopomap.org/about"),
            ),
        )
    }
}

/** [MapStyleProvider] backed by a [MapStyleConfig]. */
class ConfigurableMapStyleProvider(private val config: MapStyleConfig) : MapStyleProvider {
    private val outdoorJson: String? by lazy {
        if (config.outdoorTileUrls.isEmpty()) {
            null
        } else {
            RasterStyleJson.build(
                name = "Outdoor",
                tileUrls = config.outdoorTileUrls,
                maxZoom = config.outdoorMaxZoom,
                attribution = config.outdoorAttribution,
            )
        }
    }

    override val available: List<MapStyleKind>
        get() = MapStyleKind.entries.filter { it != MapStyleKind.Outdoor || outdoorJson != null }

    override fun style(kind: MapStyleKind): MapStyleDefinition = when (kind) {
        MapStyleKind.Standard ->
            MapStyleDefinition(kind, StyleSource.Url(config.standardStyleUrl), config.vectorAttribution)
        MapStyleKind.Dark ->
            MapStyleDefinition(kind, StyleSource.Url(config.darkStyleUrl), config.vectorAttribution)
        MapStyleKind.Outdoor -> outdoorJson
            ?.let { MapStyleDefinition(kind, StyleSource.Json(it), config.outdoorAttribution) }
            // No outdoor tiles configured: fall back to the standard map rather than a blank one.
            ?: style(MapStyleKind.Standard)
    }
}
