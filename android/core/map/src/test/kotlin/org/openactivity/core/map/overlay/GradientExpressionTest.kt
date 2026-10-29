// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.overlay

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.map.render.OverlayRenderer
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GradientExpressionTest {
    @Test
    fun buildsALineProgressInterpolation() {
        val expression = OverlayRenderer.gradientExpression(
            listOf(GradientStop(0.0, 0xFFFF0000.toInt()), GradientStop(1.0, 0xFF0000FF.toInt())),
        )
        val array = expression.toArray()
        assertEquals("interpolate", array[0])
        assertEquals("linear", (array[1] as Array<*>)[0])
        assertEquals("line-progress", (array[2] as Array<*>)[0])
        // Two stops: input, colour, input, colour.
        assertEquals(7, array.size)
        assertEquals(0.0, (array[3] as Number).toDouble(), 0.0)
        assertEquals(1.0, (array[5] as Number).toDouble(), 0.0)
    }
}
