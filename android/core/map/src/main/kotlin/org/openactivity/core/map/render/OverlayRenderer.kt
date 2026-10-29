// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.render

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.openactivity.core.map.overlay.CircleLayerSpec
import org.openactivity.core.map.overlay.GradientStop
import org.openactivity.core.map.overlay.LayerSpec
import org.openactivity.core.map.overlay.LineLayerSpec
import org.openactivity.core.map.overlay.OverlayPlan

/**
 * Applies [OverlayPlan]s to one loaded [Style]. Create a new renderer whenever the style changes:
 * a fresh style has none of our sources or layers.
 *
 * Must be used on the main thread. Unchanged GeoJSON and layer properties are skipped, so calling
 * [apply] for every new GPS fix only re-uploads the live track.
 */
internal class OverlayRenderer(private val style: Style) {
    private val appliedSources = HashMap<String, String>()
    private val appliedLayers = HashMap<String, LayerSpec>()

    fun apply(plan: OverlayPlan) {
        if (!style.isFullyLoaded) return
        for (spec in plan.sources) {
            if (appliedSources[spec.id] == spec.geoJson) continue
            val existing = style.getSourceAs<GeoJsonSource>(spec.id)
            if (existing == null) {
                style.addSource(GeoJsonSource(spec.id, spec.geoJson, GeoJsonOptions().withLineMetrics(spec.lineMetrics)))
            } else {
                existing.setGeoJson(spec.geoJson)
            }
            appliedSources[spec.id] = spec.geoJson
        }
        for (spec in plan.layers) {
            if (appliedLayers[spec.id] == spec) continue
            val layer = style.getLayer(spec.id) ?: createLayer(spec).also(::addBelowPuck)
            layer.setProperties(*properties(spec))
            appliedLayers[spec.id] = spec
        }
    }

    /**
     * Adds [layer] on top of the base map but under the location puck. The puck's layers may already
     * be there (LocationComponent activated, or re-added after a style change) and would otherwise
     * end up hidden under the live track. Adding each layer in plan order keeps our own order intact.
     */
    private fun addBelowPuck(layer: Layer) {
        val anchor = lowestPuckLayer(style.layers.map { it.id })
        if (anchor == null) style.addLayer(layer) else style.addLayerBelow(layer, anchor)
    }

    private fun createLayer(spec: LayerSpec): Layer = when (spec) {
        is LineLayerSpec -> LineLayer(spec.id, spec.sourceId).withProperties(
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        is CircleLayerSpec -> CircleLayer(spec.id, spec.sourceId)
            .withFilter(Expression.eq(Expression.get("role"), spec.role))
            .withProperties(PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP))
    }

    private fun properties(spec: LayerSpec): Array<PropertyValue<*>> = when (spec) {
        is LineLayerSpec -> buildList {
            add(PropertyFactory.lineColor(spec.color))
            add(PropertyFactory.lineWidth(spec.widthDp))
            add(PropertyFactory.lineOpacity(spec.opacity))
            spec.gradient?.let { add(PropertyFactory.lineGradient(gradientExpression(it))) }
        }.toTypedArray()
        is CircleLayerSpec -> arrayOf(
            PropertyFactory.circleRadius(spec.radiusDp),
            PropertyFactory.circleColor(spec.fillColor),
            PropertyFactory.circleStrokeColor(spec.strokeColor),
            PropertyFactory.circleStrokeWidth(spec.strokeWidthDp),
        )
    }

    companion object {
        /** Prefix MapLibre's LocationComponent uses for every layer it adds. */
        internal const val PUCK_LAYER_PREFIX: String = "mapbox-location-"

        /** The bottom-most location-puck layer in [layerIds] (bottom first), or null when there is none. */
        internal fun lowestPuckLayer(layerIds: List<String>): String? = layerIds.firstOrNull { it.startsWith(PUCK_LAYER_PREFIX) }

        /** `["interpolate", ["linear"], ["line-progress"], stop, colour, ...]` for [stops]. */
        fun gradientExpression(stops: List<GradientStop>): Expression = Expression.interpolate(
            Expression.linear(),
            Expression.lineProgress(),
            *stops.map { Expression.stop(it.progress, Expression.color(it.color)) }.toTypedArray(),
        )
    }
}
