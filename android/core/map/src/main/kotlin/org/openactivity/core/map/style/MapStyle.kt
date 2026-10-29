// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.style

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import org.openactivity.core.map.R

/**
 * The base maps a user can pick in the style switcher.
 *
 * There is deliberately no satellite option: no global imagery is both free and licensed for
 * redistribution (see docs/04-features/maps-routes.md section 13.1). [Outdoor] is the free
 * alternative: a topographic map with contours and hill shading.
 */
enum class MapStyleKind(@StringRes val labelRes: Int, val isDark: Boolean) {
    /** Light vector street and trail map. */
    Standard(R.string.map_style_standard, isDark = false),

    /** Topographic raster map with contours and relief. */
    Outdoor(R.string.map_style_outdoor, isDark = false),

    /** Dark vector map for night use and dark theme. */
    Dark(R.string.map_style_dark, isDark = true),
    ;

    companion object {
        /** The style to start with when the user has not chosen one: follows the app theme. */
        fun default(darkTheme: Boolean): MapStyleKind = if (darkTheme) Dark else Standard
    }
}

/** Where MapLibre loads a style from. */
@Immutable
sealed interface StyleSource {
    /** A style JSON document served over HTTP(S) or from `asset://`. */
    data class Url(val url: String) : StyleSource

    /** An inline style JSON document. */
    data class Json(val json: String) : StyleSource
}

/** One linked credit in the attribution line. */
@Immutable
data class AttributionPart(val text: String, val url: String)

/**
 * A resolved base map: what to load and whom to credit. The credits are shown on every map that
 * draws this style (OSM and ODbL require visible attribution, not one hidden behind a button).
 */
@Immutable
data class MapStyleDefinition(
    val kind: MapStyleKind,
    val source: StyleSource,
    val attribution: List<AttributionPart>,
) {
    /** Plain-text credit line, e.g. for accessibility or for drawing onto an exported image. */
    val attributionText: String get() = attribution.joinToString(" · ") { it.text }
}

/**
 * Resolves [MapStyleKind]s to concrete styles. Bound in Hilt; a self-hosting instance (Protomaps
 * PMTiles, ADR-012) provides its own implementation or [MapStyleConfig].
 */
interface MapStyleProvider {
    /** Styles offered in the switcher, in display order. */
    val available: List<MapStyleKind>

    /** The style to load for [kind]. */
    fun style(kind: MapStyleKind): MapStyleDefinition
}
