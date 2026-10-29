// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openactivity.model.LatLng

class SuggestionCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var now = 1_000_000L
    private fun cache() = SuggestionCache(folder.root, { now })

    @Test
    fun `nearby positions share a key, other cells, sports and distances do not`() {
        val cache = cache()
        val key = cache.key(LatLng(18.5314, 73.8446), SuggestionSport.RUN, 5_000.0)
        assertEquals(key, cache.key(LatLng(18.5316, 73.8448), SuggestionSport.RUN, 5_000.0))
        assertNotEquals(key, cache.key(LatLng(18.5414, 73.8446), SuggestionSport.RUN, 5_000.0))
        assertNotEquals(key, cache.key(LatLng(18.5314, 73.8446), SuggestionSport.RIDE, 5_000.0))
        assertNotEquals(key, cache.key(LatLng(18.5314, 73.8446), SuggestionSport.RUN, 10_000.0))
        assertEquals("run_5000_3706_14768", key)
    }

    @Test
    fun `entries are fresh for a day and kept as an offline fallback after`() = runTest {
        val cache = cache()
        val routes = listOf(suggestion("a"), suggestion("b"))
        cache.put("k", routes)
        assertEquals(routes, cache.get("k"))
        now += SuggestionCache.DEFAULT_TTL_MILLIS + 1
        assertNull(cache.get("k"))
        assertEquals(routes, cache.get("k", allowExpired = true))
    }

    @Test
    fun `a corrupt or missing file reads as absent`() = runTest {
        val cache = cache()
        assertNull(cache.get("missing", allowExpired = true))
        folder.newFile("broken.json").writeText("{not json")
        assertNull(cache.get("broken", allowExpired = true))
    }

    @Test
    fun `routes can be found by id and the cache cleared`() = runTest {
        val cache = cache()
        cache.put("k1", listOf(suggestion("a")))
        cache.put("k2", listOf(suggestion("b")))
        assertEquals("b", cache.find("b")?.id)
        assertEquals(listOf(LatLng(18.5314, 73.8446), LatLng(18.54, 73.85)), cache.find("a")?.points)
        cache.clear()
        assertNull(cache.find("a"))
    }
}

/** A minimal suggestion for cache and view-model tests. */
internal fun suggestion(id: String) = RouteSuggestion(
    id = id,
    name = "Route $id",
    sport = SuggestionSport.RUN,
    targetDistanceMeters = 5_000.0,
    distanceMeters = 5_120.0,
    elevationGainMeters = 22.0,
    elevationLossMeters = 21.0,
    polyline = org.openactivity.polyline.PolylineCodec.encode(listOf(LatLng(18.5314, 73.8446), LatLng(18.54, 73.85)), 6),
    profile = listOf(ProfileSample(0.0, 560.0), ProfileSample(5_120.0, 561.0)),
    startLatitude = 18.5314,
    startLongitude = 73.8446,
    bearingDegrees = 45.0,
)
