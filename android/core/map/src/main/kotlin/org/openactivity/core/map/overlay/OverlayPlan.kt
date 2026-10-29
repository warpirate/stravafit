// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.overlay

import androidx.annotation.ColorInt
import androidx.compose.runtime.Immutable

/** A GeoJSON source to create or update. [lineMetrics] is required for `line-gradient`. */
@Immutable
data class SourceSpec(val id: String, val geoJson: String, val lineMetrics: Boolean = false)

/** A style layer to create (once per style) and keep updated. */
@Immutable
sealed interface LayerSpec {
    val id: String
    val sourceId: String
}

/**
 * A round-capped line. When [gradient] is set it replaces [color] (MapLibre `line-gradient`).
 */
@Immutable
data class LineLayerSpec(
    override val id: String,
    override val sourceId: String,
    @ColorInt val color: Int,
    val widthDp: Float,
    val opacity: Float = 1f,
    val gradient: List<GradientStop>? = null,
) : LayerSpec

/**
 * Circles for Point features whose `role` property equals [role]. A hollow marker has a
 * [fillColor] matching the casing and a coloured stroke; a filled one is the other way round.
 */
@Immutable
data class CircleLayerSpec(
    override val id: String,
    override val sourceId: String,
    val role: String,
    val radiusDp: Float,
    @ColorInt val fillColor: Int,
    @ColorInt val strokeColor: Int,
    val strokeWidthDp: Float,
) : LayerSpec

/**
 * Sources and layers for one frame of overlays. [layers] are in draw order, bottom first. Every
 * source and layer is always present (empty when unused), so updating the map only swaps GeoJSON
 * and never reorders layers.
 */
@Immutable
data class OverlayPlan(val sources: List<SourceSpec>, val layers: List<LayerSpec>)

/** Stable ids for overlay sources and layers. */
object OverlayIds {
    private const val PREFIX = "oa-"
    const val PLANNED_SOURCE: String = PREFIX + "planned"
    const val ACTIVITY_SOURCE: String = PREFIX + "activity"
    const val ACTIVITY_GRADIENT_SOURCE: String = PREFIX + "activity-gradient"
    const val LIVE_SOURCE: String = PREFIX + "live"
    const val MARKER_SOURCE: String = PREFIX + "markers"

    const val PLANNED_CASING: String = PREFIX + "planned-casing"
    const val PLANNED_LINE: String = PREFIX + "planned-line"
    const val ACTIVITY_CASING: String = PREFIX + "activity-casing"
    const val ACTIVITY_LINE: String = PREFIX + "activity-line"
    const val ACTIVITY_GRADIENT_CASING: String = PREFIX + "activity-gradient-casing"
    const val ACTIVITY_GRADIENT_LINE: String = PREFIX + "activity-gradient-line"
    const val LIVE_CASING: String = PREFIX + "live-casing"
    const val LIVE_LINE: String = PREFIX + "live-line"
    const val START_MARKER: String = PREFIX + "start-marker"
    const val FINISH_MARKER: String = PREFIX + "finish-marker"

    const val ROLE_START: String = "start"
    const val ROLE_FINISH: String = "finish"
}
