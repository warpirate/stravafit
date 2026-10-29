// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.overlay

import androidx.annotation.ColorInt
import androidx.compose.runtime.Immutable
import org.openactivity.model.LatLng

/**
 * Everything drawn on top of the base map. Each line needs at least two points to show.
 *
 * @property liveTrack the track being recorded right now.
 * @property plannedRoute a saved or planned route, drawn in its own colour under the other lines.
 * @property activityRoute a finished activity, with start and finish markers.
 */
@Immutable
data class MapOverlays(
    val liveTrack: List<LatLng> = emptyList(),
    val plannedRoute: List<LatLng> = emptyList(),
    val activityRoute: ActivityRouteOverlay? = null,
) {
    /** Every point on the map, for fitting the camera to all overlays at once. */
    val allPoints: List<LatLng> get() = plannedRoute + (activityRoute?.points ?: emptyList()) + liveTrack

    companion object {
        /** Nothing drawn. */
        val Empty: MapOverlays = MapOverlays()
    }
}

/**
 * A recorded activity's route.
 *
 * @property points the track.
 * @property speedsMetersPerSecond optional speed at each point (same size as [points]; nulls allowed).
 *   When present and [colorByPace] is true the line is coloured slow-to-fast.
 * @property colorByPace whether to use the pace gradient when speeds are available.
 * @property showEndpoints draw a hollow start marker and a filled finish marker.
 */
@Immutable
data class ActivityRouteOverlay(
    val points: List<LatLng>,
    val speedsMetersPerSecond: List<Double?>? = null,
    val colorByPace: Boolean = true,
    val showEndpoints: Boolean = true,
) {
    init {
        require(speedsMetersPerSecond == null || speedsMetersPerSecond.size == points.size) {
            "speeds must match points (${speedsMetersPerSecond?.size} vs ${points.size})"
        }
    }
}

/**
 * Colours for overlays, as ARGB ints so planning stays free of Compose and MapLibre types.
 * Build one from the theme with `rememberMapPalette()`.
 */
@Immutable
data class MapPalette(
    @ColorInt val liveTrack: Int,
    @ColorInt val plannedRoute: Int,
    @ColorInt val activityRoute: Int,
    /** Outline under every line and marker so it reads on any base map. */
    @ColorInt val casing: Int,
    @ColorInt val marker: Int,
    @ColorInt val puck: Int,
    @ColorInt val puckAccuracy: Int,
    /** Background of offline thumbnails. */
    @ColorInt val land: Int,
    /** Pace ramp from slowest to fastest; at least two colours. */
    val paceRamp: List<Int> = ViridisRamp,
    /** Start marker fill (green, strava `routeStart`). */
    @ColorInt val start: Int = 0xFF1FA45B.toInt(),
    /** Finish marker fill (near-black, so it reads as the chequered flag's dark square). */
    @ColorInt val finish: Int = 0xFF242428.toInt(),
    /** Ring around the markers (white on any base map). */
    @ColorInt val markerRing: Int = 0xFFFFFFFF.toInt(),
) {
    init {
        require(paceRamp.size >= 2) { "pace ramp needs at least two colours" }
    }

    companion object {
        /**
         * Colour-blind-safe viridis ramp, slow (yellow) to fast (deep purple). It keeps contrast on both
         * light and dark base maps, and the casing separates it from the map underneath.
         */
        val ViridisRamp: List<Int> = listOf(
            0xFFFDE725.toInt(),
            0xFF5EC962.toInt(),
            0xFF21918C.toInt(),
            0xFF3B528B.toInt(),
            0xFF440154.toInt(),
        )
    }
}
