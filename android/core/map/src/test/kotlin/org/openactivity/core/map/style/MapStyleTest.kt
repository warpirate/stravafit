// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.style

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapStyleTest {
    private val provider = ConfigurableMapStyleProvider(MapStyleConfig.OpenFreeMap)

    @Test
    fun defaultsAreKeylessOpenFreeMapStyles() {
        val standard = provider.style(MapStyleKind.Standard).source as StyleSource.Url
        val dark = provider.style(MapStyleKind.Dark).source as StyleSource.Url
        assertEquals("https://tiles.openfreemap.org/styles/liberty", standard.url)
        assertEquals("https://tiles.openfreemap.org/styles/dark", dark.url)
        assertTrue(listOf(standard.url, dark.url).none { "key=" in it })
    }

    @Test
    fun everyStyleCreditsOpenStreetMap() {
        for (kind in MapStyleKind.entries) {
            val definition = provider.style(kind)
            assertTrue("$kind", definition.attribution.any { "OpenStreetMap" in it.text && it.url.startsWith("https://") })
            assertTrue(definition.attributionText.contains("© OpenStreetMap contributors"))
        }
    }

    @Test
    fun outdoorStyleIsAValidRasterStyle() {
        val source = provider.style(MapStyleKind.Outdoor).source as StyleSource.Json
        val json = JSONObject(source.json)
        assertEquals(8, json.getInt("version"))
        val raster = json.getJSONObject("sources").getJSONObject(RasterStyleJson.SOURCE_ID)
        assertEquals("raster", raster.getString("type"))
        assertEquals(3, raster.getJSONArray("tiles").length())
        assertEquals(17, raster.getInt("maxzoom"))
        assertTrue(raster.getString("attribution").contains("OpenTopoMap"))
        val layers = json.getJSONArray("layers")
        assertEquals(RasterStyleJson.LAYER_ID, layers.getJSONObject(layers.length() - 1).getString("id"))
    }

    @Test
    fun outdoorFallsBackToStandardWhenNoTilesAreConfigured() {
        val noOutdoor = ConfigurableMapStyleProvider(MapStyleConfig.OpenFreeMap.copy(outdoorTileUrls = emptyList()))
        assertEquals(listOf(MapStyleKind.Standard, MapStyleKind.Dark), noOutdoor.available)
        assertEquals(MapStyleKind.Standard, noOutdoor.style(MapStyleKind.Outdoor).kind)
    }

    @Test
    fun selfHostedConfigIsUsedVerbatim() {
        val custom = ConfigurableMapStyleProvider(
            MapStyleConfig.OpenFreeMap.copy(standardStyleUrl = "https://tiles.example.org/light.json"),
        )
        assertEquals(StyleSource.Url("https://tiles.example.org/light.json"), custom.style(MapStyleKind.Standard).source)
    }

    @Test
    fun defaultFollowsTheme() {
        assertEquals(MapStyleKind.Dark, MapStyleKind.default(darkTheme = true))
        assertEquals(MapStyleKind.Standard, MapStyleKind.default(darkTheme = false))
    }

    @Test
    fun quoteEscapesJson() {
        val quoted = RasterStyleJson.quote("a\"b\\c\n\u0001")
        assertEquals("a\"b\\c\n\u0001", JSONObject("{\"k\":$quoted}").getString("k"))
    }
}
