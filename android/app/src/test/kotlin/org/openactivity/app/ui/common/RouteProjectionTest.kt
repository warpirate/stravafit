// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.app.data.sample.SampleRoutes
import org.openactivity.app.model.LatLng

class RouteProjectionTest {

    @Test
    fun `projected route fits inside the padded box`() {
        val route = SampleRoutes.loop(LatLng(18.52, 73.85), radiusKm = 2.0, seed = 5)
        val points = projectRoute(route, width = 400f, height = 200f, padding = 10f)
        assertEquals(route.size, points.size)
        assertTrue(points.all { it.x in 10f..390f && it.y in 10f..190f })
    }

    @Test
    fun `north is up and east is right`() {
        val route = listOf(LatLng(0.0, 0.0), LatLng(0.01, 0.01))
        val (start, end) = projectRoute(route, 100f, 100f, 0f)
        assertTrue(end.x > start.x)
        assertTrue(end.y < start.y)
    }

    @Test
    fun `degenerate input is handled`() {
        assertTrue(projectRoute(emptyList(), 100f, 100f, 0f).isEmpty())
        val single = projectRoute(listOf(LatLng(1.0, 1.0)), 100f, 100f, 0f)
        assertEquals(1, single.size)
    }

    @Test
    fun `sample routes are deterministic`() {
        val a = SampleRoutes.outAndBack(LatLng(18.5, 73.8), 3.0, 45.0, seed = 9)
        val b = SampleRoutes.outAndBack(LatLng(18.5, 73.8), 3.0, 45.0, seed = 9)
        assertEquals(a, b)
    }
}
