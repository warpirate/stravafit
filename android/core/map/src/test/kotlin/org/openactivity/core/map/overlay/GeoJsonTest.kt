// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.overlay

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.model.LatLng
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeoJsonTest {
    @Test
    fun fewerThanTwoPointsIsEmpty() {
        assertEquals(GeoJson.EMPTY, GeoJson.lineString(emptyList()))
        assertEquals(GeoJson.EMPTY, GeoJson.lineString(listOf(LatLng(1.0, 2.0))))
        assertEquals(GeoJson.EMPTY, GeoJson.rolePoints(emptyList()))
    }

    @Test
    fun lineStringIsValidGeoJsonWithLonLatOrder() {
        val json = JSONObject(GeoJson.lineString(listOf(LatLng(18.5, 73.8), LatLng(-18.25, -73.000_000_4))))
        assertEquals("FeatureCollection", json.getString("type"))
        val geometry = json.getJSONArray("features").getJSONObject(0).getJSONObject("geometry")
        assertEquals("LineString", geometry.getString("type"))
        val coords = geometry.getJSONArray("coordinates")
        assertEquals(73.8, coords.getJSONArray(0).getDouble(0), 0.0)
        assertEquals(18.5, coords.getJSONArray(0).getDouble(1), 0.0)
        assertEquals(-73.0, coords.getJSONArray(1).getDouble(0), 0.0)
        assertEquals(-18.25, coords.getJSONArray(1).getDouble(1), 0.0)
    }

    @Test
    fun numbersIgnoreTheDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val s = GeoJson.lineString(listOf(LatLng(1.5, 2.25), LatLng(-0.000_001, 3.0)))
            assertEquals(true, s.contains("[2.250000,1.500000]"))
            assertEquals(true, s.contains("[3.000000,-0.000001]"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun longitudesUnwrapAcrossTheAntimeridian() {
        val lons = GeoJson.unwrapLongitudes(listOf(LatLng(0.0, 179.0), LatLng(0.0, -179.0), LatLng(0.0, -178.0), LatLng(0.0, 179.5)))
        assertArrayEquals(doubleArrayOf(179.0, 181.0, 182.0, 179.5), lons, 1e-9)
    }

    @Test
    fun rolePointsCarryTheirRole() {
        val json = JSONObject(GeoJson.rolePoints(listOf(LatLng(1.0, 2.0) to "start", LatLng(3.0, 4.0) to "finish")))
        val features = json.getJSONArray("features")
        assertEquals(2, features.length())
        assertEquals("finish", features.getJSONObject(1).getJSONObject("properties").getString("role"))
        val point = features.getJSONObject(1).getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(4.0, point.getDouble(0), 0.0)
        assertEquals(3.0, point.getDouble(1), 0.0)
    }
}
