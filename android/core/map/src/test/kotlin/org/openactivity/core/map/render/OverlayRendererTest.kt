// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.location.LocationComponentConstants

class OverlayRendererTest {
    @Test
    fun `overlays go under the lowest puck layer`() {
        val ids = listOf(
            "background",
            "road",
            LocationComponentConstants.ACCURACY_LAYER,
            LocationComponentConstants.SHADOW_LAYER,
            LocationComponentConstants.FOREGROUND_LAYER,
        )
        assertEquals(LocationComponentConstants.ACCURACY_LAYER, OverlayRenderer.lowestPuckLayer(ids))
    }

    @Test
    fun `without a puck overlays go on top`() {
        assertNull(OverlayRenderer.lowestPuckLayer(listOf("background", "road", "oa-live-line")))
    }

    @Test
    fun `prefix matches every MapLibre location layer`() {
        listOf(
            LocationComponentConstants.ACCURACY_LAYER,
            LocationComponentConstants.BACKGROUND_LAYER,
            LocationComponentConstants.BEARING_LAYER,
            LocationComponentConstants.FOREGROUND_LAYER,
            LocationComponentConstants.PULSING_CIRCLE_LAYER,
            LocationComponentConstants.SHADOW_LAYER,
        ).forEach { assertTrue(it, it.startsWith(OverlayRenderer.PUCK_LAYER_PREFIX)) }
    }
}
