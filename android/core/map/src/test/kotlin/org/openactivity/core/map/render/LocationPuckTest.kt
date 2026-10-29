// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.render

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationPuckTest {
    @Test
    fun `following from world zoom zooms in to street level`() {
        // Regression: the Record map stayed at world zoom (1.0) while following the user.
        assertEquals(LocationPuck.FOLLOW_ZOOM, LocationPuck.followZoom(1.0), 0.0)
        assertEquals(LocationPuck.FOLLOW_ZOOM, LocationPuck.followZoom(12.9), 0.0)
    }

    @Test
    fun `following keeps a zoom the user chose close in`() {
        assertEquals(14.5, LocationPuck.followZoom(14.5), 0.0)
    }
}
