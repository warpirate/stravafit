// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.camera

import androidx.compose.runtime.Immutable
import org.openactivity.model.LatLng

/**
 * A map camera: where it looks and how close.
 *
 * @property target the geographic point at the centre of the viewport.
 * @property zoom MapLibre zoom level; the world is `512 * 2^zoom` dp wide.
 * @property bearing degrees clockwise from north.
 * @property tilt degrees away from straight down.
 */
@Immutable
data class MapCamera(
    val target: LatLng,
    val zoom: Double,
    val bearing: Double = 0.0,
    val tilt: Double = 0.0,
)

/**
 * Insets, in dp, that the camera keeps clear when fitting content, e.g. for a bottom sheet or the
 * attribution line. Content is centred in the area that remains.
 */
@Immutable
data class MapPadding(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(left >= 0f && top >= 0f && right >= 0f && bottom >= 0f) { "padding must not be negative" }
    }

    companion object {
        /** No padding. */
        val None: MapPadding = MapPadding(0f, 0f, 0f, 0f)

        /** The same [dp] on every side. */
        fun all(dp: Float): MapPadding = MapPadding(dp, dp, dp, dp)
    }
}
